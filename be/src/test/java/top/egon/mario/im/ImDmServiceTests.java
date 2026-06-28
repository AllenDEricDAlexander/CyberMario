package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImDmPairPo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImDmBlockRepository;
import top.egon.mario.im.repository.ImDmPairRepository;
import top.egon.mario.im.facade.ImException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImDmServiceTests {

    private static final String CONTEXT_TYPE = "IM_DM_SERVICE_TEST";

    @Autowired
    private DmFacade dmFacade;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImDmPairRepository dmPairRepository;

    @Autowired
    private ImConversationRepository conversationRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImDmBlockRepository dmBlockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void openDmNormalizesPairAndReturnsSameConversationInReverse() {
        ConversationView first = dmFacade.openDm(new OpenDmCommand(principal(7002L), 7001L));
        ConversationView reverse = dmFacade.openDm(new OpenDmCommand(principal(7001L), 7002L));

        assertThat(reverse.id()).isEqualTo(first.id());
        assertThat(reverse.ownerSurfaceId()).isEqualTo(first.ownerSurfaceId());
        assertThat(reverse.conversationType()).isEqualTo("DM");
        assertThat(reverse.ownerSurfaceType()).isEqualTo("DM_PAIR");
        assertThat(reverse.contextType()).isEqualTo(CONTEXT_TYPE);
        assertThat(reverse.contextId()).isNull();
        assertThat(reverse.messageSeq()).isEqualTo(0L);
        assertThat(reverse.status()).isEqualTo("ACTIVE");
        assertThat(reverse.lastActiveAt()).isNotNull();

        assertThat(dmPairRepository.findByOrderedUsers(7001L, 7002L)).get()
                .satisfies(pair -> {
                    assertThat(pair.getUserLoId()).isEqualTo(7001L);
                    assertThat(pair.getUserHiId()).isEqualTo(7002L);
                    assertThat(pair.getConversationId()).isEqualTo(first.id());
                    assertThat(pair.getFrozen()).isFalse();
                    assertThat(pair.getMetadataJson()).isEqualTo("{}");
                    assertThat(pair.getId()).isEqualTo(first.ownerSurfaceId());
                });
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> ImSurfaceType.DM_PAIR.equals(conversation.getOwnerSurfaceType()))
                .filteredOn(conversation -> first.ownerSurfaceId().equals(conversation.getOwnerSurfaceId()))
                .filteredOn(conversation -> ImConversationType.DM.equals(conversation.getConversationTypeEnum()))
                .hasSize(1)
                .first()
                .satisfies(conversation -> {
                    assertThat(conversation.getId()).isEqualTo(first.id());
                    assertThat(conversation.getStatus()).isEqualTo(ImConversationStatus.ACTIVE);
                    assertThat(conversation.getMessageSeq()).isEqualTo(0L);
                });
        assertThat(conversationMemberRepository.findByConversationIdAndDeletedFalse(first.id()))
                .hasSize(2)
                .extracting(ImConversationMemberPo::getUserId)
                .containsExactlyInAnyOrder(7001L, 7002L);
        assertThat(conversationMemberRepository.findByConversationIdAndDeletedFalse(first.id()))
                .allSatisfy(member -> {
                    assertThat(member.getStatus()).isEqualTo(ImMembershipStatus.ACTIVE);
                    assertThat(member.getDeliveryMode()).isEqualTo(ImDeliveryMode.INBOX);
                    assertThat(member.getMuted()).isFalse();
                    assertThat(member.getLastReadSeq()).isEqualTo(0L);
                });
    }

    @Test
    void openDmValidatesPrincipalTargetAndSelfDm() {
        assertThatThrownBy(() -> dmFacade.openDm(new OpenDmCommand(null, 7101L)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PRINCIPAL_REQUIRED");
        assertThatThrownBy(() -> dmFacade.openDm(new OpenDmCommand(principal(7101L), null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_DM_TARGET_REQUIRED");
        assertThatThrownBy(() -> dmFacade.openDm(new OpenDmCommand(principal(7101L), 7101L)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_DM_SELF_DENIED");
    }

    @Test
    void blockAndUnblockTogglePairFrozenUntilBothDirectionsAreInactive() {
        ConversationView conversation = dmFacade.openDm(new OpenDmCommand(principal(7201L), 7202L));

        dmFacade.block(new BlockUserCommand(principal(7201L), 7202L, "spam"));

        assertThat(dmPairRepository.findByOrderedUsers(7201L, 7202L)).get()
                .satisfies(pair -> {
                    assertThat(pair.getId()).isEqualTo(conversation.ownerSurfaceId());
                    assertThat(pair.getFrozen()).isTrue();
                });
        assertThat(dmBlockRepository.findActiveBlock(7201L, 7202L)).get()
                .satisfies(block -> {
                    assertThat(block.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE);
                    assertThat(block.getReason()).isEqualTo("spam");
                });
        assertThat(conversationMemberRepository.findByConversationIdAndDeletedFalse(conversation.id()))
                .hasSize(2)
                .allSatisfy(member -> assertThat(member.getStatus()).isEqualTo(ImMembershipStatus.ACTIVE));

        dmFacade.block(new BlockUserCommand(principal(7202L), 7201L, "abuse"));
        dmFacade.unblock(new BlockUserCommand(principal(7201L), 7202L, null));

        assertThat(dmBlockRepository.findActiveBlock(7201L, 7202L)).isEmpty();
        assertThat(dmBlockRepository.findActiveBlock(7202L, 7201L)).isPresent();
        assertThat(dmPairRepository.findByOrderedUsers(7201L, 7202L)).get()
                .extracting(pair -> pair.getFrozen())
                .isEqualTo(true);

        dmFacade.unblock(new BlockUserCommand(principal(7202L), 7201L, null));

        assertThat(dmBlockRepository.findActiveBlock(7202L, 7201L)).isEmpty();
        assertThat(dmPairRepository.findByOrderedUsers(7201L, 7202L)).get()
                .extracting(pair -> pair.getFrozen())
                .isEqualTo(false);
    }

    @Test
    void openDmRetriesUniquePairRaceInFreshTransactionAndReturnsWinningPair() {
        ConversationView winning = runInNewTransaction(() ->
                dmFacade.openDm(new OpenDmCommand(principal(7401L), 7402L)));
        ImDmPairPo winningPair = dmPairRepository.findByOrderedUsers(7401L, 7402L).orElseThrow();
        clearInvocations(dmPairRepository);
        doReturn(java.util.Optional.empty())
                .doReturn(java.util.Optional.of(winningPair))
                .when(dmPairRepository)
                .findLockedByUserLoIdAndUserHiIdAndDeletedFalse(7401L, 7402L);
        doThrow(new DataIntegrityViolationException("uk_im_dm_pair_users"))
                .when(dmPairRepository)
                .saveAndFlush(any(ImDmPairPo.class));

        ConversationView raced = dmFacade.openDm(new OpenDmCommand(principal(7402L), 7401L));

        assertThat(raced.id()).isEqualTo(winning.id());
        assertThat(raced.ownerSurfaceId()).isEqualTo(winning.ownerSurfaceId());
        verify(dmPairRepository, times(2)).findLockedByUserLoIdAndUserHiIdAndDeletedFalse(7401L, 7402L);
        verify(dmPairRepository, times(1)).saveAndFlush(any(ImDmPairPo.class));
    }

    @Test
    void unblockLocksPairBeforeMutatingBlockRow() {
        dmFacade.block(new BlockUserCommand(principal(7501L), 7502L, "spam"));
        clearInvocations(dmPairRepository, dmBlockRepository);

        dmFacade.unblock(new BlockUserCommand(principal(7501L), 7502L, null));

        InOrder order = inOrder(dmPairRepository, dmBlockRepository);
        order.verify(dmPairRepository)
                .findLockedByUserLoIdAndUserHiIdAndDeletedFalse(7501L, 7502L);
        order.verify(dmBlockRepository)
                .findByBlockerUserIdAndBlockedUserIdAndDeletedFalse(7501L, 7502L);
    }

    @Test
    void blockReactivatesExistingInactiveBlockAndRejectsSelfBlock() {
        dmFacade.block(new BlockUserCommand(principal(7301L), 7302L, "first"));
        dmFacade.unblock(new BlockUserCommand(principal(7301L), 7302L, null));
        dmFacade.block(new BlockUserCommand(principal(7301L), 7302L, "second"));

        assertThat(dmBlockRepository.findAll())
                .filteredOn(block -> Long.valueOf(7301L).equals(block.getBlockerUserId()))
                .filteredOn(block -> Long.valueOf(7302L).equals(block.getBlockedUserId()))
                .hasSize(1)
                .first()
                .satisfies(block -> {
                    assertThat(block.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE);
                    assertThat(block.getReason()).isEqualTo("second");
                });
        assertThat(dmPairRepository.findByOrderedUsers(7301L, 7302L)).get()
                .extracting(pair -> pair.getFrozen())
                .isEqualTo(true);
        assertThatThrownBy(() -> dmFacade.block(new BlockUserCommand(principal(7301L), 7301L, "self")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_DM_SELF_DENIED");
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }

    private <T> T runInNewTransaction(java.util.function.Supplier<T> supplier) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> supplier.get());
    }
}
