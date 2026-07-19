package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.service.ImException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ImSurfaceServiceTests {

    private static final String CONTEXT_TYPE = "IM_SURFACE_SERVICE_TEST";

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ImChannelRepository channelRepository;

    @Autowired
    private ImGroupRepository groupRepository;

    @Autowired
    private ImConversationRepository conversationRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Test
    void createChannelCreatesMainConversationAndOwnerRowsIdempotently() {
        CreateChannelCommand command = new CreateChannelCommand(
                principal(1001L), CONTEXT_TYPE, null, "announcements", "Announcements", "APPROVAL", "{}");

        ChannelView first = conversationService.createChannel(command);
        ChannelView second = conversationService.createChannel(command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.mainConversationId()).isEqualTo(first.mainConversationId());
        assertThat(second.contextType()).isEqualTo(CONTEXT_TYPE);
        assertThat(second.contextId()).isNull();
        assertThat(second.channelKey()).isEqualTo("announcements");
        assertThat(second.joinKey()).isEqualTo(first.joinKey())
                .matches("^chn_[A-Za-z0-9_-]{22}$");
        assertThat(second.name()).isEqualTo("Announcements");
        assertThat(second.ownerUserId()).isEqualTo(1001L);
        assertThat(second.visibility()).isEqualTo("PUBLIC");
        assertThat(second.joinPolicy()).isEqualTo("APPROVAL");
        assertThat(second.status()).isEqualTo("ACTIVE");
        assertThat(second.memberCount()).isEqualTo(1);
        assertThat(second.lastActiveAt()).isNotNull();
        assertThat(second.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(second.memberRole()).isEqualTo("OWNER");
        assertThat(second.canRead()).isTrue();
        assertThat(second.canPost()).isTrue();

        assertThat(channelRepository.findAll())
                .filteredOn(channel -> CONTEXT_TYPE.equals(channel.getContextType()))
                .filteredOn(channel -> channel.getContextId() == null)
                .filteredOn(channel -> "announcements".equals(channel.getChannelKey()))
                .hasSize(1)
                .first()
                .satisfies(channel -> {
                    assertThat(channel.getJoinPolicy()).isEqualTo(ImJoinPolicy.APPROVAL);
                    assertThat(channel.getStatus()).isEqualTo(ImSurfaceStatus.ACTIVE);
                    assertThat(channel.getMemberCount()).isEqualTo(1);
                    assertThat(channel.getMainConversationId()).isEqualTo(first.mainConversationId());
                });
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> ImSurfaceType.CHANNEL.equals(conversation.getOwnerSurfaceType()))
                .filteredOn(conversation -> first.id().equals(conversation.getOwnerSurfaceId()))
                .filteredOn(conversation -> ImConversationType.CHANNEL_MAIN.equals(conversation.getConversationTypeEnum()))
                .hasSize(1)
                .first()
                .satisfies(conversation -> {
                    assertThat(conversation.getId()).isEqualTo(first.mainConversationId());
                    assertThat(conversation.getContextType()).isEqualTo(CONTEXT_TYPE);
                    assertThat(conversation.getContextId()).isNull();
                    assertThat(conversation.getStatus()).isEqualTo(ImConversationStatus.ACTIVE);
                });
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                ImSurfaceType.CHANNEL, first.id(), 1001L, ImMembershipStatus.ACTIVE)).get()
                .satisfies(membership -> assertThat(membership.getMemberRole()).isEqualTo(ImMembershipRole.OWNER));
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(first.mainConversationId(), 1001L)).get()
                .satisfies(member -> {
                    assertThat(member.getDeliveryMode()).isEqualTo(ImDeliveryMode.INBOX);
                    assertThat(member.getLastReadSeq()).isEqualTo(0L);
                });
    }

    @Test
    void createGroupUnderChannelInheritsChannelContextAndCreatesOwnerRowsIdempotently() {
        ChannelView channel = conversationService.createChannel(new CreateChannelCommand(
                principal(1101L), CONTEXT_TYPE, 9001L, "room", "Room", "OPEN", "{}"));
        CreateGroupCommand command = new CreateGroupCommand(
                principal(1101L), channel.id(), null, null, "general", "General", "OPEN", "{}");

        GroupView first = conversationService.createGroup(command);
        GroupView second = conversationService.createGroup(command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(second.channelId()).isEqualTo(channel.id());
        assertThat(second.contextType()).isEqualTo(CONTEXT_TYPE);
        assertThat(second.contextId()).isEqualTo(9001L);
        assertThat(second.groupKey()).isEqualTo("general");
        assertThat(second.joinKey()).isEqualTo(first.joinKey())
                .matches("^grp_[A-Za-z0-9_-]{22}$");
        assertThat(second.name()).isEqualTo("General");
        assertThat(second.ownerUserId()).isEqualTo(1101L);
        assertThat(second.joinPolicy()).isEqualTo("OPEN");
        assertThat(second.status()).isEqualTo("ACTIVE");
        assertThat(second.memberCount()).isEqualTo(1);
        assertThat(second.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(second.memberRole()).isEqualTo("OWNER");
        assertThat(second.canRead()).isTrue();
        assertThat(second.canPost()).isTrue();

        assertThat(groupRepository.findAll())
                .filteredOn(group -> channel.id().equals(group.getChannelId()))
                .filteredOn(group -> "general".equals(group.getGroupKey()))
                .hasSize(1);
        assertThat(conversationRepository.findAll())
                .filteredOn(conversation -> ImSurfaceType.GROUP.equals(conversation.getOwnerSurfaceType()))
                .filteredOn(conversation -> first.id().equals(conversation.getOwnerSurfaceId()))
                .filteredOn(conversation -> ImConversationType.GROUP.equals(conversation.getConversationTypeEnum()))
                .hasSize(1)
                .extracting(ImConversationPo::getId)
                .containsExactly(first.conversationId());
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                ImSurfaceType.GROUP, first.id(), 1101L, ImMembershipStatus.ACTIVE)).isPresent();
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(first.conversationId(), 1101L))
                .isPresent();
    }

    @Test
    void createStandaloneGroupRequiresContextAndIsIdempotentByContext() {
        CreateGroupCommand command = new CreateGroupCommand(
                principal(1201L), null, CONTEXT_TYPE, null, "global-general", "Global General", "APPROVAL", "{}");

        GroupView first = conversationService.createGroup(command);
        GroupView second = conversationService.createGroup(command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.channelId()).isNull();
        assertThat(second.contextType()).isEqualTo(CONTEXT_TYPE);
        assertThat(second.contextId()).isNull();
        assertThat(second.conversationId()).isNotNull();
        assertThat(second.joinPolicy()).isEqualTo("APPROVAL");
        assertThat(second.joinKey()).isEqualTo(first.joinKey())
                .matches("^grp_[A-Za-z0-9_-]{22}$");
        assertThat(groupRepository.findAll())
                .filteredOn(group -> group.getChannelId() == null)
                .filteredOn(group -> CONTEXT_TYPE.equals(group.getContextType()))
                .filteredOn(group -> group.getContextId() == null)
                .filteredOn(group -> "global-general".equals(group.getGroupKey()))
                .hasSize(1);

        assertThatThrownBy(() -> conversationService.createGroup(new CreateGroupCommand(
                principal(1201L), null, " ", null, "missing-context", "Missing Context", "OPEN", "{}")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CONTEXT_TYPE_REQUIRED");
        assertThatThrownBy(() -> groupRepository.findById(first.id()).orElseThrow()
                .assignJoinKey("grp_0000000000000000000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void createGroupRejectsGroupIdUsedAsChannelId() {
        GroupView standalone = conversationService.createGroup(new CreateGroupCommand(
                principal(1301L), null, CONTEXT_TYPE, 9301L, "standalone", "Standalone", "OPEN", "{}"));

        assertThatThrownBy(() -> conversationService.createGroup(new CreateGroupCommand(
                principal(1301L), standalone.id(), null, null, "nested", "Nested", "OPEN", "{}")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_INVALID_SURFACE_NESTING");
    }

    @Test
    void createChannelValidatesRequiredCommandFields() {
        assertThatThrownBy(() -> conversationService.createChannel(new CreateChannelCommand(
                null, CONTEXT_TYPE, null, "main", "Main", "OPEN", "{}")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PRINCIPAL_REQUIRED");
        assertThatThrownBy(() -> conversationService.createChannel(new CreateChannelCommand(
                principal(1401L), " ", null, "main", "Main", "OPEN", "{}")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CONTEXT_TYPE_REQUIRED");
        assertThatThrownBy(() -> conversationService.createChannel(new CreateChannelCommand(
                principal(1401L), CONTEXT_TYPE, null, " ", "Main", "OPEN", "{}")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CHANNEL_KEY_REQUIRED");
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }
}
