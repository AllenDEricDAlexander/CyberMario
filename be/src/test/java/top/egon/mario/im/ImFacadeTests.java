package top.egon.mario.im;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.im.context.ImContext;
import top.egon.mario.im.context.ImPrincipal;
import top.egon.mario.im.legacy.LegacyImFacade;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;
import top.egon.mario.im.policy.ImSendPolicy;
import top.egon.mario.im.policy.ImVisibilityPolicy;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImMessageRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImFacadeTests {

    private static final String TEST_CONTEXT = "IM_TEST";
    private static final String PRIVATE_CONTEXT = "IM_PRIVATE";
    private static final String DENIED_CONTEXT = "IM_DENIED";

    @Autowired
    private LegacyImFacade imFacade;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImChannelRepository channelRepository;

    @Autowired
    private ImConversationMemberRepository memberRepository;

    @Autowired
    private ImConversationRepository conversationRepository;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createChannelIsIdempotentByContext() {
        ImChannelPo first = imFacade.ensureChannel(TEST_CONTEXT, 101L, "ROOM");
        ImChannelPo second = imFacade.ensureChannel(TEST_CONTEXT, 101L, "ROOM");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(channelRepository.findAll())
                .filteredOn(channel -> TEST_CONTEXT.equals(channel.getContextType()))
                .filteredOn(channel -> Long.valueOf(101L).equals(channel.getContextId()))
                .filteredOn(channel -> "ROOM".equals(channel.getChannelKey()))
                .hasSize(1);
    }

    @Test
    void createPrivateConversationUsesStableParticipantKey() {
        ImGroupPo group = ensureGroup(TEST_CONTEXT, 201L);

        ImConversationPo first = imFacade.ensureConversation(group.getId(), "USER_PAIR", 301L,
                "PRIVATE", List.of(20L, 10L));
        ImConversationPo second = imFacade.ensureConversation(group.getId(), "USER_PAIR", 301L,
                "PRIVATE", List.of(10L, 20L));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getParticipantKey()).isEqualTo("10:20");
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> group.getId().equals(conversation.getGroupId()))
                .filteredOn(conversation -> "USER_PAIR".equals(conversation.getScopeType()))
                .filteredOn(conversation -> Long.valueOf(301L).equals(conversation.getScopeId()))
                .filteredOn(conversation -> "PRIVATE".equals(conversation.getConversationType()))
                .filteredOn(conversation -> "10:20".equals(conversation.getParticipantKey()))
                .hasSize(1);
    }

    @Test
    void privateConversationRejectsEmptyParticipantList() {
        ImGroupPo group = ensureGroup(TEST_CONTEXT, 251L);

        assertThatThrownBy(() -> imFacade.ensureConversation(group.getId(), "USER_PAIR", 351L,
                "PRIVATE", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_PARTICIPANTS_REQUIRED");
        assertThatThrownBy(() -> imFacade.ensureConversation(group.getId(), "USER_PAIR", 352L,
                "DIRECT", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_PARTICIPANTS_REQUIRED");
        assertThatThrownBy(() -> imFacade.ensureConversation(group.getId(), "USER_PAIR", 353L,
                "ONE_TO_ONE", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_PARTICIPANTS_REQUIRED");
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> group.getId().equals(conversation.getGroupId()))
                .isEmpty();
    }

    @Test
    void sendMessageEvaluatesPolicyBeforePersisting() {
        ImConversationPo conversation = ensureConversation(DENIED_CONTEXT, 301L, 1L, 2L);

        assertThatThrownBy(() -> imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L),
                "blocked", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_SEND_DENIED");
        assertThat(messageRepository.countByConversationIdAndDeletedFalse(conversation.getId())).isZero();
    }

    @Test
    void sendMessageAllocatesConversationLocalSequence() {
        ImConversationPo conversation = ensureConversation(TEST_CONTEXT, 401L, 1L, 2L);

        ImMessagePo first = imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "one", "{}");
        ImMessagePo second = imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "two", "{}");
        ImMessagePo third = imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "three", "{}");

        assertThat(List.of(first.getMessageSeq(), second.getMessageSeq(), third.getMessageSeq()))
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void sendMessageRetriesInIndependentTransactionsWhenFirstAttemptConflicts() {
        ImConversationPo conversation = ensureConversation(TEST_CONTEXT, 451L, 1L, 2L);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failOnce.getAndSet(false)) {
                throw new DataIntegrityViolationException("forced sequence race");
            }
            ImMessagePo message = invocation.getArgument(0);
            entityManager.persist(message);
            entityManager.flush();
            return message;
        }).when(messageRepository).saveAndFlush(any(ImMessagePo.class));

        ImMessagePo message = new TransactionTemplate(transactionManager).execute(status ->
                imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "retried", "{}"));

        assertThat(message).isNotNull();
        assertThat(message.getMessageSeq()).isEqualTo(1L);
        assertThat(messageRepository.countByConversationIdAndDeletedFalse(conversation.getId())).isEqualTo(1L);
        verify(messageRepository, times(2)).saveAndFlush(any(ImMessagePo.class));
    }

    @Test
    void markReadIsIdempotentAndNeverMovesBackward() {
        ImConversationPo conversation = ensureConversation(TEST_CONTEXT, 501L, 1L, 2L);
        imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "one", "{}");
        imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "two", "{}");
        imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "three", "{}");

        ImReadStatePo high = imFacade.markRead(conversation.getId(), new ImPrincipal(2L), 3L);
        ImReadStatePo lower = imFacade.markRead(conversation.getId(), new ImPrincipal(2L), 1L);

        assertThat(high.getLastReadMessageSeq()).isEqualTo(3L);
        assertThat(lower.getLastReadMessageSeq()).isEqualTo(3L);
    }

    @Test
    void historyAppliesVisibilityPolicy() {
        ImConversationPo conversation = ensureConversation(PRIVATE_CONTEXT, 601L, 1L, 2L);
        imFacade.sendMessage(conversation.getId(), new ImPrincipal(1L), "private", "{}");

        Page<ImMessagePo> visible = imFacade.history(conversation.getId(), new ImPrincipal(2L),
                PageRequest.of(0, 10));

        assertThat(visible.getContent()).hasSize(1);
        assertThatThrownBy(() -> imFacade.history(conversation.getId(), new ImPrincipal(99L),
                PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_HISTORY_FORBIDDEN");
    }

    @Test
    void ensureChannelReloadsExistingRowAfterCreateConflict() {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            ImChannelPo candidate = invocation.getArgument(0);
            if (failOnce.getAndSet(false) && "IM_RACE".equals(candidate.getContextType())) {
                requiresNewTransaction().executeWithoutResult(status -> jdbcTemplate.update("""
                        insert into im_channel (
                            context_type, context_id, channel_key, name, status, metadata_json,
                            created_at, updated_at, version, deleted
                        )
                        values (?, ?, ?, ?, 'ACTIVE', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, false)
                        """, candidate.getContextType(), candidate.getContextId(), candidate.getChannelKey(),
                        "race winner"));
                throw new DataIntegrityViolationException("forced channel race");
            }
            entityManager.persist(candidate);
            entityManager.flush();
            return candidate;
        }).when(channelRepository).saveAndFlush(any(ImChannelPo.class));

        ImChannelPo channel = imFacade.ensureChannel("IM_RACE", 701L, "ROOM");

        assertThat(channel.getName()).isEqualTo("race winner");
        assertThat(channelRepository.findAll())
                .filteredOn(candidate -> "IM_RACE".equals(candidate.getContextType()))
                .filteredOn(candidate -> Long.valueOf(701L).equals(candidate.getContextId()))
                .filteredOn(candidate -> "ROOM".equals(candidate.getChannelKey()))
                .hasSize(1);
    }

    @Test
    void nonPrivateConversationIdentityIgnoresMutableParticipantListAndAddsMembers() {
        ImGroupPo group = ensureGroup(PRIVATE_CONTEXT, 801L);

        ImConversationPo first = imFacade.ensureConversation(group.getId(), "ROOM", 801L,
                "ROOM", List.of(1L, 2L));
        ImConversationPo second = imFacade.ensureConversation(group.getId(), "ROOM", 801L,
                "ROOM", List.of(1L, 2L, 3L));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getParticipantKey()).isEqualTo("ROOM:801");
        assertThat(memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                second.getId(), 3L, "ACTIVE")).isTrue();
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> group.getId().equals(conversation.getGroupId()))
                .filteredOn(conversation -> "ROOM".equals(conversation.getScopeType()))
                .filteredOn(conversation -> Long.valueOf(801L).equals(conversation.getScopeId()))
                .filteredOn(conversation -> "ROOM".equals(conversation.getConversationType()))
                .hasSize(1);
        assertThat(imFacade.history(second.getId(), new ImPrincipal(3L), PageRequest.of(0, 10)).getContent())
                .isEmpty();
        assertThatThrownBy(() -> imFacade.history(second.getId(), new ImPrincipal(99L), PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM_HISTORY_FORBIDDEN");
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private ImGroupPo ensureGroup(String contextType, Long contextId) {
        ImChannelPo channel = imFacade.ensureChannel(contextType, contextId, "ROOM");
        return imFacade.ensureGroup(channel.getId(), "GENERAL");
    }

    private ImConversationPo ensureConversation(String contextType, Long contextId, Long firstUserId,
                                                Long secondUserId) {
        ImGroupPo group = ensureGroup(contextType, contextId);
        return imFacade.ensureConversation(group.getId(), "ROOM", contextId, "PRIVATE",
                List.of(firstUserId, secondUserId));
    }

    @TestConfiguration
    static class ImTestPolicyConfig {

        @Bean
        ImSendPolicy imTestSendPolicy() {
            return new ImSendPolicy() {
                @Override
                public boolean supports(String contextType) {
                    return TEST_CONTEXT.equals(contextType) || PRIVATE_CONTEXT.equals(contextType);
                }

                @Override
                public boolean canSend(ImContext context) {
                    return true;
                }
            };
        }

        @Bean
        ImVisibilityPolicy imTestVisibilityPolicy() {
            return new ImVisibilityPolicy() {
                @Override
                public boolean supports(String contextType) {
                    return TEST_CONTEXT.equals(contextType);
                }

                @Override
                public boolean canRead(ImContext context) {
                    return true;
                }
            };
        }
    }
}
