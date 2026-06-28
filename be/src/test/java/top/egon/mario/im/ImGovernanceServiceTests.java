package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.GovFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.AnnounceCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.po.ImBanPo;
import top.egon.mario.im.po.ImGlobalMutePo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImGlobalMuteScopeType;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImBanRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGlobalMuteRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.facade.ImException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImGovernanceServiceTests {

    private static final String CONTEXT_TYPE = "IM_GOVERNANCE_SERVICE_TEST";

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private DmFacade dmFacade;

    @Autowired
    private GovFacade govFacade;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImGlobalMuteRepository globalMuteRepository;

    @Autowired
    private ImBanRepository banRepository;

    @Autowired
    private ImGroupRepository groupRepository;

    @Test
    void surfaceMuteDeniesChannelSendUntilExpiryMovesPast() {
        ChannelView channel = channel(9001L, "channel-mute");
        join(9002L, "CHANNEL", channel.id());

        govFacade.mute(new MuteUserCommand(
                principal(9001L), "CHANNEL", channel.id(), 9002L, Instant.now().plusSeconds(600), "noise"));

        assertThatThrownBy(() -> imFacade.send(send(9002L, channel.mainConversationId(), "muted-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");

        govFacade.mute(new MuteUserCommand(
                principal(9001L), "CHANNEL", channel.id(), 9002L, Instant.now().minusSeconds(60), "expired"));

        assertThat(imFacade.send(send(9002L, channel.mainConversationId(), "muted-expired")).messageSeq())
                .isEqualTo(1L);
    }

    @Test
    void platformGlobalMuteDeniesChannelGroupAndDmSendsAndDefaultsScopeId() {
        ChannelView channel = channel(9011L, "global-channel");
        GroupView group = group(9011L, "global-group");
        join(9012L, "CHANNEL", channel.id());
        join(9012L, "GROUP", group.id());
        ConversationView dm = dmFacade.openDm(new OpenDmCommand(principal(9012L), 9013L));
        Instant firstExpiry = Instant.now().plusSeconds(600);
        Instant updatedExpiry = Instant.now().plusSeconds(1200);

        govFacade.globalMute(new GlobalMuteCommand(
                principal(9010L), "PLATFORM", null, 9012L, firstExpiry, "first"));
        govFacade.globalMute(new GlobalMuteCommand(
                principal(9010L), "PLATFORM", null, 9012L, updatedExpiry, "updated"));

        assertThat(globalMuteRepository.findActiveMute(
                9012L, ImGlobalMuteScopeType.PLATFORM, 0L, Instant.now())).get()
                .satisfies(mute -> {
                    assertThat(mute.getExpiresAt()).isEqualTo(updatedExpiry);
                    assertThat(mute.getReason()).isEqualTo("updated");
                    assertThat(mute.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE);
                });
        assertThat(globalMuteRepository.findAll().stream()
                .filter(mute -> Long.valueOf(9012L).equals(mute.getUserId()))
                .filter(mute -> ImGlobalMuteScopeType.PLATFORM.equals(mute.getScopeType()))
                .filter(mute -> Long.valueOf(0L).equals(mute.getScopeId()))
                .toList())
                .hasSize(1);
        assertThatThrownBy(() -> imFacade.send(send(9012L, channel.mainConversationId(), "global-channel-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
        assertThatThrownBy(() -> imFacade.send(send(9012L, group.conversationId(), "global-group-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
        assertThatThrownBy(() -> imFacade.send(send(9012L, dm.id(), "global-dm-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
    }

    @Test
    void platformGlobalMuteCanonicalizesProvidedScopeIdAndIsEnforced() {
        ChannelView channel = channel(9051L, "global-nonzero-scope");
        join(9052L, "CHANNEL", channel.id());

        govFacade.globalMute(new GlobalMuteCommand(
                principal(9050L), "PLATFORM", 123L, 9052L, Instant.now().plusSeconds(600), "platform"));

        assertThat(globalMutes(9052L, ImGlobalMuteScopeType.PLATFORM))
                .singleElement()
                .satisfies(mute -> {
                    assertThat(mute.getScopeId()).isEqualTo(0L);
                    assertThat(mute.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE);
                });
        assertThat(globalMuteRepository.findActiveMute(
                9052L, ImGlobalMuteScopeType.PLATFORM, 0L, Instant.now())).isPresent();
        assertThatThrownBy(() -> imFacade.send(send(9052L, channel.mainConversationId(), "global-nonzero-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
    }

    @Test
    void banGroupMemberMarksMembershipConversationMemberAuditAndBlocksRejoin() {
        GroupView group = group(9021L, "ban-group");
        join(9022L, "GROUP", group.id());

        govFacade.ban(new BanUserCommand(principal(9021L), "GROUP", group.id(), 9022L, "abuse"));

        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.GROUP, group.id(), 9022L)).get()
                .extracting(ImMembershipPo::getStatus)
                .isEqualTo(ImMembershipStatus.BANNED);
        assertThat(conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                group.conversationId(), 9022L)).get()
                .extracting(member -> member.getStatus())
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                group.conversationId(), 9022L)).isEmpty();
        assertThat(banRepository.findActiveBan(ImSurfaceType.GROUP, group.id(), 9022L, Instant.now())).get()
                .satisfies(ban -> {
                    assertThat(ban.getActorUserId()).isEqualTo(9021L);
                    assertThat(ban.getReason()).isEqualTo("abuse");
                });
        assertThat(groupRepository.findByIdAndDeletedFalse(group.id())).get()
                .extracting(groupPo -> groupPo.getMemberCount())
                .isEqualTo(1);
        assertThatThrownBy(() -> join(9022L, "GROUP", group.id()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_MEMBER_BANNED");
    }

    @Test
    void repeatedBanReusesActiveAuditAndDoesNotDecrementMemberCountTwice() {
        GroupView group = group(9061L, "ban-repeat");
        join(9062L, "GROUP", group.id());

        govFacade.ban(new BanUserCommand(principal(9061L), "GROUP", group.id(), 9062L, "first"));
        govFacade.ban(new BanUserCommand(principal(9061L), "GROUP", group.id(), 9062L, "second"));

        assertThat(activeBans(ImSurfaceType.GROUP, group.id(), 9062L))
                .singleElement()
                .satisfies(ban -> {
                    assertThat(ban.getActorUserId()).isEqualTo(9061L);
                    assertThat(ban.getReason()).isEqualTo("second");
                });
        assertThat(groupRepository.findByIdAndDeletedFalse(group.id())).get()
                .extracting(groupPo -> groupPo.getMemberCount())
                .isEqualTo(1);
        assertThatThrownBy(() -> imFacade.send(send(9062L, group.conversationId(), "ban-repeat-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
        assertThatThrownBy(() -> join(9062L, "GROUP", group.id()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_MEMBER_BANNED");
    }

    @Test
    void globalMuteUpsertMergesDuplicateExactRowsAndKeepsEnforcementStable() {
        ChannelView channel = channel(9071L, "global-duplicate");
        join(9072L, "CHANNEL", channel.id());
        globalMuteRepository.saveAndFlush(globalMute(9072L, ImGlobalMuteScopeType.PLATFORM, 0L,
                Instant.now().plusSeconds(300), "first"));
        globalMuteRepository.saveAndFlush(globalMute(9072L, ImGlobalMuteScopeType.PLATFORM, 0L,
                Instant.now().plusSeconds(400), "duplicate"));

        assertThatCode(() -> govFacade.globalMute(new GlobalMuteCommand(
                principal(9070L), "PLATFORM", null, 9072L, Instant.now().plusSeconds(600), "merged")))
                .doesNotThrowAnyException();

        assertThat(activeGlobalMutes(9072L, ImGlobalMuteScopeType.PLATFORM, 0L))
                .singleElement()
                .satisfies(mute -> assertThat(mute.getReason()).isEqualTo("merged"));
        assertThat(globalMuteRepository.findActiveMute(
                9072L, ImGlobalMuteScopeType.PLATFORM, 0L, Instant.now())).isPresent();
        assertThatThrownBy(() -> imFacade.send(send(9072L, channel.mainConversationId(), "global-duplicate-denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
    }

    @Test
    void announcementsArePersistedInChannelAndGroupViews() {
        ChannelView channel = channel(9031L, "announce-channel");
        GroupView group = group(9031L, "announce-group");

        govFacade.announce(new AnnounceCommand(principal(9031L), "CHANNEL", channel.id(), "channel notice"));
        govFacade.announce(new AnnounceCommand(principal(9031L), "GROUP", group.id(), "group notice"));

        assertThat(conversationService.listChannels(new ListChannelsQuery(principal(9031L), CONTEXT_TYPE, null)))
                .filteredOn(view -> channel.id().equals(view.id()))
                .singleElement()
                .extracting(ChannelView::announcement)
                .isEqualTo("channel notice");
        assertThat(conversationService.listGroups(new ListGroupsQuery(principal(9031L), null, CONTEXT_TYPE, null)))
                .filteredOn(view -> group.id().equals(view.id()))
                .singleElement()
                .extracting(GroupView::announcement)
                .isEqualTo("group notice");
    }

    @Test
    void surfaceGovernanceRequiresActiveOwnerOrAdminActor() {
        ChannelView channel = channel(9041L, "governance-role");
        join(9042L, "CHANNEL", channel.id());
        join(9043L, "CHANNEL", channel.id());

        assertThatThrownBy(() -> govFacade.mute(new MuteUserCommand(
                principal(9042L), "CHANNEL", channel.id(), 9043L, Instant.now().plusSeconds(600), "member")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_GOVERNANCE_ACTOR_REQUIRED");

        ImMembershipPo adminMembership = membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 9042L).orElseThrow();
        adminMembership.setMemberRole(ImMembershipRole.ADMIN);
        membershipRepository.saveAndFlush(adminMembership);
        Instant mutedUntil = Instant.now().plusSeconds(600);

        govFacade.mute(new MuteUserCommand(principal(9042L), "CHANNEL", channel.id(), 9043L, mutedUntil, "admin"));

        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 9043L)).get()
                .extracting(ImMembershipPo::getMutedUntil)
                .isEqualTo(mutedUntil);
    }

    private ChannelView channel(Long ownerUserId, String key) {
        return conversationService.createChannel(new CreateChannelCommand(
                principal(ownerUserId), CONTEXT_TYPE, null, key, key, "OPEN", "{}"));
    }

    private GroupView group(Long ownerUserId, String key) {
        return conversationService.createGroup(new CreateGroupCommand(
                principal(ownerUserId), null, CONTEXT_TYPE, null, key, key, "OPEN", "{}"));
    }

    private void join(Long userId, String surfaceType, Long surfaceId) {
        roomFacade.applyJoin(new JoinCommand(principal(userId), surfaceType, surfaceId, "join"));
    }

    private SendMessageCommand send(Long userId, Long conversationId, String clientMsgId) {
        return new SendMessageCommand(principal(userId), conversationId, clientMsgId, "TEXT", clientMsgId, "{}", "{}");
    }

    private ImGlobalMutePo globalMute(Long userId, ImGlobalMuteScopeType scopeType, Long scopeId,
                                      Instant expiresAt, String reason) {
        ImGlobalMutePo mute = new ImGlobalMutePo();
        mute.setUserId(userId);
        mute.setScopeType(scopeType);
        mute.setScopeId(scopeId);
        mute.setExpiresAt(expiresAt);
        mute.setReason(reason);
        mute.setStatus(ImGovernanceStatus.ACTIVE);
        mute.setMetadataJson("{}");
        return mute;
    }

    private List<ImGlobalMutePo> globalMutes(Long userId, ImGlobalMuteScopeType scopeType) {
        return globalMuteRepository.findAll().stream()
                .filter(mute -> userId.equals(mute.getUserId()))
                .filter(mute -> scopeType.equals(mute.getScopeType()))
                .toList();
    }

    private List<ImGlobalMutePo> activeGlobalMutes(Long userId, ImGlobalMuteScopeType scopeType, Long scopeId) {
        return globalMutes(userId, scopeType).stream()
                .filter(mute -> scopeId.equals(mute.getScopeId()))
                .filter(mute -> ImGovernanceStatus.ACTIVE.equals(mute.getStatus()))
                .toList();
    }

    private List<ImBanPo> activeBans(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        return banRepository.findAll().stream()
                .filter(ban -> surfaceType.equals(ban.getSurfaceType()))
                .filter(ban -> surfaceId.equals(ban.getSurfaceId()))
                .filter(ban -> userId.equals(ban.getUserId()))
                .filter(ban -> ImGovernanceStatus.ACTIVE.equals(ban.getStatus()))
                .toList();
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }
}
