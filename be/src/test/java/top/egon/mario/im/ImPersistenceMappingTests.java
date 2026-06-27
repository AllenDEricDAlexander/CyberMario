package top.egon.mario.im;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.legacy.LegacyImFacade;
import top.egon.mario.im.po.ImBanPo;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImDmBlockPo;
import top.egon.mario.im.po.ImDmPairPo;
import top.egon.mario.im.po.ImGlobalMutePo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImInboxPo;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.ImWsTicketPo;
import top.egon.mario.im.po.enums.ImChannelVisibility;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImGlobalMuteScopeType;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImMessageStatus;
import top.egon.mario.im.po.enums.ImMessageType;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.po.enums.ImOutboxStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.po.enums.ImWsTicketStatus;
import top.egon.mario.im.repository.ImBanRepository;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImDmBlockRepository;
import top.egon.mario.im.repository.ImDmPairRepository;
import top.egon.mario.im.repository.ImGlobalMuteRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImInboxRepository;
import top.egon.mario.im.repository.ImJoinRequestRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImMessageRepository;
import top.egon.mario.im.repository.ImOutboxRepository;
import top.egon.mario.im.repository.ImWsTicketRepository;
import top.egon.mario.im.service.ImException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ImPersistenceMappingTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImChannelRepository channelRepository;

    @Autowired
    private ImGroupRepository groupRepository;

    @Autowired
    private ImDmPairRepository dmPairRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImJoinRequestRepository joinRequestRepository;

    @Autowired
    private ImConversationRepository conversationRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImMessageRepository messageRepository;

    @Autowired
    private ImOutboxRepository outboxRepository;

    @Autowired
    private ImInboxRepository inboxRepository;

    @Autowired
    private ImGlobalMuteRepository globalMuteRepository;

    @Autowired
    private ImDmBlockRepository dmBlockRepository;

    @Autowired
    private ImBanRepository banRepository;

    @Autowired
    private ImWsTicketRepository wsTicketRepository;

    @Autowired
    private LegacyImFacade imFacade;

    @Test
    void v30PoMappingsRoundTripCoreFieldsThroughRepositories() {
        Instant now = Instant.parse("2026-06-27T01:00:00Z");

        ImChannelPo channel = channelRepository.saveAndFlush(channel("IM_TEST", 1001L, "main", now));
        ImGroupPo group = groupRepository.saveAndFlush(group(channel.getId(), "IM_TEST", 1001L, "general", now));
        ImConversationPo conversation = conversationRepository.saveAndFlush(conversation(
                ImConversationType.CHANNEL_MAIN, ImSurfaceType.CHANNEL, channel.getId(), "IM_TEST", 1001L, now));
        channel.setMainConversationId(conversation.getId());
        channelRepository.saveAndFlush(channel);

        ImDmPairPo dmPair = dmPairRepository.saveAndFlush(dmPair(2001L, 2002L, conversation.getId()));
        ImMembershipPo membership = membershipRepository.saveAndFlush(membership(
                ImSurfaceType.CHANNEL, channel.getId(), 3001L, ImMembershipRole.OWNER, ImMembershipStatus.ACTIVE, now));
        ImJoinRequestPo joinRequest = joinRequestRepository.saveAndFlush(joinRequest(
                ImSurfaceType.GROUP, group.getId(), 3002L, ImJoinRequestStatus.PENDING));
        ImConversationMemberPo member = conversationMemberRepository.saveAndFlush(conversationMember(
                conversation.getId(), 3001L, ImDeliveryMode.INBOX, ImMembershipStatus.ACTIVE));
        ImMessagePo message = messageRepository.saveAndFlush(message(
                conversation.getId(), 3001L, 1L, "client-1", ImMessageType.TEXT, ImMessageStatus.VISIBLE, now));
        ImOutboxPo outbox = outboxRepository.saveAndFlush(outbox(
                conversation.getId(), message.getId(), 1L, ImOutboxEventType.MESSAGE_CREATED,
                ImOutboxStatus.PENDING, now));
        ImInboxPo inbox = inboxRepository.saveAndFlush(inbox(3002L, conversation.getId(), message.getId(), 1L));
        ImGlobalMutePo globalMute = globalMuteRepository.saveAndFlush(globalMute(
                3003L, ImGlobalMuteScopeType.CONTEXT, 1001L, ImGovernanceStatus.ACTIVE, now.plusSeconds(3600)));
        ImDmBlockPo block = dmBlockRepository.saveAndFlush(dmBlock(3004L, 3005L, ImGovernanceStatus.ACTIVE));
        ImBanPo ban = banRepository.saveAndFlush(ban(
                ImSurfaceType.CHANNEL, channel.getId(), 3006L, 3001L, ImGovernanceStatus.ACTIVE, now.plusSeconds(3600)));
        ImWsTicketPo ticket = wsTicketRepository.saveAndFlush(wsTicket(
                "ticket-hash-1", 3007L, ImWsTicketStatus.ACTIVE, now.plusSeconds(60)));

        entityManager.flush();
        entityManager.clear();

        assertThat(channelRepository.findByIdAndDeletedFalse(channel.getId())).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getContextType()).isEqualTo("IM_TEST");
                    assertThat(reloaded.getContextId()).isEqualTo(1001L);
                    assertThat(reloaded.getChannelKey()).isEqualTo("main");
                    assertThat(reloaded.getVisibility()).isEqualTo(ImChannelVisibility.PUBLIC);
                    assertThat(reloaded.getJoinPolicy()).isEqualTo(ImJoinPolicy.OPEN);
                    assertThat(reloaded.getStatus()).isEqualTo(ImSurfaceStatus.ACTIVE);
                    assertThat(reloaded.getMainConversationId()).isEqualTo(conversation.getId());
                });
        assertThat(groupRepository.findByIdAndDeletedFalse(group.getId())).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getChannelId()).isEqualTo(channel.getId());
                    assertThat(reloaded.getGroupKey()).isEqualTo("general");
                    assertThat(reloaded.getStatus()).isEqualTo(ImSurfaceStatus.ACTIVE);
                });
        assertThat(dmPairRepository.findByOrderedUsers(2001L, 2002L)).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getConversationId()).isEqualTo(conversation.getId());
                    assertThat(reloaded.getFrozen()).isFalse();
                });
        assertThat(membershipRepository.findByIdAndDeletedFalse(membership.getId())).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getSurfaceType()).isEqualTo(ImSurfaceType.CHANNEL);
                    assertThat(reloaded.getMemberRole()).isEqualTo(ImMembershipRole.OWNER);
                    assertThat(reloaded.getStatus()).isEqualTo(ImMembershipStatus.ACTIVE);
                });
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(joinRequest.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getStatus()).isEqualTo(ImJoinRequestStatus.PENDING));
        assertThat(conversationRepository.findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.getId(), ImConversationType.CHANNEL_MAIN)).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getContextType()).isEqualTo("IM_TEST");
                    assertThat(reloaded.getMessageSeq()).isEqualTo(0L);
                    assertThat(reloaded.getStatus()).isEqualTo(ImConversationStatus.ACTIVE);
                });
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                conversation.getId(), 3001L)).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getLastReadSeq()).isEqualTo(0L);
                    assertThat(reloaded.getDeliveryMode()).isEqualTo(ImDeliveryMode.INBOX);
                });
        assertThat(messageRepository.findByConversationIdAndSenderUserIdAndClientMsgIdAndDeletedFalse(
                conversation.getId(), 3001L, "client-1")).get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getMessageSeq()).isEqualTo(1L);
                    assertThat(reloaded.getMessageTypeEnum()).isEqualTo(ImMessageType.TEXT);
                    assertThat(reloaded.getStatus()).isEqualTo(ImMessageStatus.VISIBLE);
                });
        assertThat(outboxRepository.findByIdAndDeletedFalse(outbox.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getEventType()).isEqualTo(ImOutboxEventType.MESSAGE_CREATED));
        assertThat(inboxRepository.findByIdAndDeletedFalse(inbox.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getRead()).isFalse());
        assertThat(globalMuteRepository.findByIdAndDeletedFalse(globalMute.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE));
        assertThat(dmBlockRepository.findByIdAndDeletedFalse(block.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getStatus()).isEqualTo(ImGovernanceStatus.ACTIVE));
        assertThat(banRepository.findByIdAndDeletedFalse(ban.getId())).get()
                .satisfies(reloaded -> assertThat(reloaded.getActorUserId()).isEqualTo(3001L));
        assertThat(wsTicketRepository.findByTokenHashAndDeletedFalse(ticket.getTokenHash())).get()
                .satisfies(reloaded -> assertThat(reloaded.getStatus()).isEqualTo(ImWsTicketStatus.ACTIVE));
    }

    @Test
    void enumColumnsPersistStringNamesAndReloadAsEnums() {
        Instant now = Instant.parse("2026-06-27T02:00:00Z");

        ImChannelPo channel = channel("IM_ENUM", null, "global", now);
        channel.setJoinPolicy(ImJoinPolicy.APPROVAL);
        channel.setStatus(ImSurfaceStatus.ARCHIVED);
        channel = channelRepository.saveAndFlush(channel);
        ImGroupPo group = group(null, "IM_ENUM", null, "standalone", now);
        group.setJoinPolicy(ImJoinPolicy.APPROVAL);
        group = groupRepository.saveAndFlush(group);
        ImConversationPo conversation = conversation(
                ImConversationType.DM, ImSurfaceType.DM_PAIR, 9001L, "IM_ENUM", null, now);
        conversation.setStatus(ImConversationStatus.ARCHIVED);
        conversation = conversationRepository.saveAndFlush(conversation);
        ImMembershipPo membership = membershipRepository.saveAndFlush(membership(
                ImSurfaceType.GROUP, group.getId(), 9002L, ImMembershipRole.ADMIN, ImMembershipStatus.PENDING, now));
        ImJoinRequestPo joinRequest = joinRequestRepository.saveAndFlush(joinRequest(
                ImSurfaceType.GROUP, group.getId(), 9003L, ImJoinRequestStatus.REJECTED));
        ImConversationMemberPo member = conversationMemberRepository.saveAndFlush(conversationMember(
                conversation.getId(), 9002L, ImDeliveryMode.CURSOR, ImMembershipStatus.LEFT));
        ImMessagePo message = messageRepository.saveAndFlush(message(
                conversation.getId(), 9002L, 2L, "client-2", ImMessageType.SYSTEM, ImMessageStatus.EDITED, now));
        ImOutboxPo outbox = outboxRepository.saveAndFlush(outbox(
                conversation.getId(), message.getId(), 2L, ImOutboxEventType.READ_UPDATED,
                ImOutboxStatus.FAILED, now));
        ImGlobalMutePo mute = globalMuteRepository.saveAndFlush(globalMute(
                9004L, ImGlobalMuteScopeType.PLATFORM, 0L, ImGovernanceStatus.EXPIRED, now.minusSeconds(1)));
        ImWsTicketPo ticket = wsTicketRepository.saveAndFlush(wsTicket(
                "ticket-hash-enum", 9005L, ImWsTicketStatus.CONSUMED, now.plusSeconds(60)));

        entityManager.flush();
        entityManager.clear();

        assertColumn(channel.getId(), "im_channel", "visibility", "PUBLIC");
        assertColumn(channel.getId(), "im_channel", "join_policy", "APPROVAL");
        assertColumn(channel.getId(), "im_channel", "status", "ARCHIVED");
        assertColumn(group.getId(), "im_group", "join_policy", "APPROVAL");
        assertColumn(conversation.getId(), "im_conversation", "owner_surface_type", "DM_PAIR");
        assertColumn(conversation.getId(), "im_conversation", "conversation_type", "DM");
        assertColumn(conversation.getId(), "im_conversation", "status", "ARCHIVED");
        assertColumn(membership.getId(), "im_membership", "member_role", "ADMIN");
        assertColumn(membership.getId(), "im_membership", "status", "PENDING");
        assertColumn(joinRequest.getId(), "im_join_request", "status", "REJECTED");
        assertColumn(member.getId(), "im_conversation_member", "delivery_mode", "CURSOR");
        assertColumn(message.getId(), "im_message", "message_type", "SYSTEM");
        assertColumn(message.getId(), "im_message", "status", "EDITED");
        assertColumn(outbox.getId(), "im_outbox", "event_type", "READ_UPDATED");
        assertColumn(outbox.getId(), "im_outbox", "status", "FAILED");
        assertColumn(mute.getId(), "im_global_mute", "scope_type", "PLATFORM");
        assertColumn(mute.getId(), "im_global_mute", "status", "EXPIRED");
        assertColumn(ticket.getId(), "im_ws_ticket", "status", "CONSUMED");

        assertThat(conversationRepository.findByIdAndDeletedFalse(conversation.getId())).get()
                .extracting(ImConversationPo::getConversationTypeEnum)
                .isEqualTo(ImConversationType.DM);
    }

    @Test
    void lockedConversationQueryReturnsActiveConversation() {
        Instant now = Instant.parse("2026-06-27T03:00:00Z");
        ImConversationPo conversation = conversationRepository.saveAndFlush(conversation(
                ImConversationType.GROUP, ImSurfaceType.GROUP, 9101L, "IM_LOCK", 9102L, now));

        entityManager.flush();
        entityManager.clear();

        assertThat(conversationRepository.findLockedByIdAndDeletedFalse(conversation.getId())).get()
                .extracting(ImConversationPo::getId)
                .isEqualTo(conversation.getId());
    }

    @Test
    void outboxClaimReturnsPendingRowsByAvailableTime() {
        Instant now = Instant.parse("2026-06-27T04:00:00Z");
        ImOutboxPo second = outboxRepository.save(outbox(
                9201L, 9301L, 2L, ImOutboxEventType.MESSAGE_CREATED, ImOutboxStatus.PENDING, now.minusSeconds(10)));
        ImOutboxPo dispatched = outboxRepository.save(outbox(
                9201L, 9302L, 3L, ImOutboxEventType.MESSAGE_CREATED, ImOutboxStatus.DISPATCHED, now.minusSeconds(20)));
        ImOutboxPo first = outboxRepository.save(outbox(
                9201L, 9303L, 1L, ImOutboxEventType.MESSAGE_CREATED, ImOutboxStatus.PENDING, now.minusSeconds(20)));
        ImOutboxPo later = outboxRepository.save(outbox(
                9201L, 9304L, 4L, ImOutboxEventType.MESSAGE_CREATED, ImOutboxStatus.PENDING, now.plusSeconds(20)));
        outboxRepository.saveAllAndFlush(List.of(second, dispatched, first, later));

        entityManager.flush();
        entityManager.clear();

        List<ImOutboxPo> claimed = outboxRepository.claimPendingForDispatch(now, PageRequest.of(0, 2));

        assertThat(claimed)
                .extracting(ImOutboxPo::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(claimed)
                .extracting(ImOutboxPo::getStatus)
                .containsOnly(ImOutboxStatus.PENDING);
    }

    @Test
    void activeGovernanceQueriesFilterDeletedInactiveAndExpiredRows() {
        Instant now = Instant.parse("2026-06-27T05:00:00Z");
        ImGlobalMutePo activeMute = globalMuteRepository.save(globalMute(
                9401L, ImGlobalMuteScopeType.CONTEXT, 9402L, ImGovernanceStatus.ACTIVE, now.plusSeconds(60)));
        globalMuteRepository.save(globalMute(
                9401L, ImGlobalMuteScopeType.CONTEXT, 9403L, ImGovernanceStatus.ACTIVE, now.minusSeconds(60)));
        ImDmBlockPo activeBlock = dmBlockRepository.save(dmBlock(9404L, 9405L, ImGovernanceStatus.ACTIVE));
        ImDmBlockPo inactiveBlock = dmBlock(9404L, 9406L, ImGovernanceStatus.INACTIVE);
        dmBlockRepository.save(inactiveBlock);
        ImBanPo activeBan = banRepository.save(ban(
                ImSurfaceType.GROUP, 9407L, 9408L, 9409L, ImGovernanceStatus.ACTIVE, now.plusSeconds(60)));
        ImBanPo deletedBan = ban(ImSurfaceType.GROUP, 9407L, 9410L, 9409L, ImGovernanceStatus.ACTIVE,
                now.plusSeconds(60));
        deletedBan.setDeleted(true);
        banRepository.saveAndFlush(deletedBan);

        entityManager.flush();
        entityManager.clear();

        assertThat(globalMuteRepository.findActiveMute(9401L, ImGlobalMuteScopeType.CONTEXT, 9402L, now)).get()
                .extracting(ImGlobalMutePo::getId)
                .isEqualTo(activeMute.getId());
        assertThat(globalMuteRepository.findActiveMute(9401L, ImGlobalMuteScopeType.CONTEXT, 9403L, now)).isEmpty();
        assertThat(dmBlockRepository.findActiveBlock(9404L, 9405L)).get()
                .extracting(ImDmBlockPo::getId)
                .isEqualTo(activeBlock.getId());
        assertThat(dmBlockRepository.findActiveBlock(9404L, 9406L)).isEmpty();
        assertThat(banRepository.findActiveBan(ImSurfaceType.GROUP, 9407L, 9408L, now)).get()
                .extracting(ImBanPo::getId)
                .isEqualTo(activeBan.getId());
        assertThat(banRepository.findActiveBan(ImSurfaceType.GROUP, 9407L, 9410L, now)).isEmpty();
    }

    @Test
    void legacyFacadeMutationPathsFailFastDuringPersistenceTransition() {
        assertThatThrownBy(() -> imFacade.ensureGroup(1L, "GENERAL"))
                .isInstanceOf(ImException.class)
                .hasMessageContaining("IM_LEGACY_FACADE_REPLACED")
                .extracting("code")
                .isEqualTo("IM_LEGACY_FACADE_REPLACED");

        assertThatThrownBy(() -> imFacade.ensureConversation(1L, "ROOM", 2L, "PRIVATE", List.of(10L, 20L)))
                .isInstanceOf(ImException.class)
                .hasMessageContaining("IM_LEGACY_FACADE_REPLACED")
                .extracting("code")
                .isEqualTo("IM_LEGACY_FACADE_REPLACED");
    }

    @Test
    void legacyConversationRepositoryQueriesFailExplicitly() {
        assertThatThrownBy(() -> conversationRepository
                .findByGroupIdAndScopeTypeAndScopeIdAndConversationTypeAndParticipantKeyAndDeletedFalse(
                        1L, "ROOM", 2L, "PRIVATE", "10:20"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("IM_LEGACY_CONVERSATION_QUERY_REPLACED");

        assertThatThrownBy(() -> conversationRepository
                .findByContextTypeAndScopeTypeAndScopeIdAndDeletedFalseOrderByGroupIdAscIdAsc(
                        "CLOCKTOWER", "ROOM", 2L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("IM_LEGACY_CONVERSATION_QUERY_REPLACED");
    }

    private ImChannelPo channel(String contextType, Long contextId, String channelKey, Instant now) {
        ImChannelPo channel = new ImChannelPo();
        channel.setContextType(contextType);
        channel.setContextId(contextId);
        channel.setChannelKey(channelKey);
        channel.setName("Channel " + channelKey);
        channel.setOwnerUserId(3001L);
        channel.setVisibility(ImChannelVisibility.PUBLIC);
        channel.setJoinPolicy(ImJoinPolicy.OPEN);
        channel.setStatus(ImSurfaceStatus.ACTIVE);
        channel.setAnnouncement("");
        channel.setMemberCount(0);
        channel.setLastActiveAt(now);
        channel.setMetadataJson("{}");
        return channel;
    }

    private ImGroupPo group(Long channelId, String contextType, Long contextId, String groupKey, Instant now) {
        ImGroupPo group = new ImGroupPo();
        group.setChannelId(channelId);
        group.setContextType(contextType);
        group.setContextId(contextId);
        group.setGroupKey(groupKey);
        group.setName("Group " + groupKey);
        group.setOwnerUserId(3001L);
        group.setJoinPolicy(ImJoinPolicy.OPEN);
        group.setStatus(ImSurfaceStatus.ACTIVE);
        group.setAnnouncement("");
        group.setMemberCount(0);
        group.setLastActiveAt(now);
        group.setMetadataJson("{}");
        return group;
    }

    private ImDmPairPo dmPair(Long userLoId, Long userHiId, Long conversationId) {
        ImDmPairPo dmPair = new ImDmPairPo();
        dmPair.setUserLoId(userLoId);
        dmPair.setUserHiId(userHiId);
        dmPair.setConversationId(conversationId);
        dmPair.setFrozen(false);
        dmPair.setMetadataJson("{}");
        return dmPair;
    }

    private ImMembershipPo membership(ImSurfaceType surfaceType, Long surfaceId, Long userId,
                                      ImMembershipRole role, ImMembershipStatus status, Instant now) {
        ImMembershipPo membership = new ImMembershipPo();
        membership.setSurfaceType(surfaceType);
        membership.setSurfaceId(surfaceId);
        membership.setUserId(userId);
        membership.setMemberRole(role);
        membership.setStatus(status);
        membership.setJoinedAt(now);
        membership.setMetadataJson("{}");
        return membership;
    }

    private ImJoinRequestPo joinRequest(ImSurfaceType surfaceType, Long surfaceId, Long userId,
                                        ImJoinRequestStatus status) {
        ImJoinRequestPo joinRequest = new ImJoinRequestPo();
        joinRequest.setSurfaceType(surfaceType);
        joinRequest.setSurfaceId(surfaceId);
        joinRequest.setUserId(userId);
        joinRequest.setStatus(status);
        joinRequest.setMetadataJson("{}");
        return joinRequest;
    }

    private ImConversationPo conversation(ImConversationType type, ImSurfaceType ownerSurfaceType,
                                          Long ownerSurfaceId, String contextType, Long contextId, Instant now) {
        ImConversationPo conversation = new ImConversationPo();
        conversation.setConversationType(type);
        conversation.setOwnerSurfaceType(ownerSurfaceType);
        conversation.setOwnerSurfaceId(ownerSurfaceId);
        conversation.setContextType(contextType);
        conversation.setContextId(contextId);
        conversation.setMessageSeq(0L);
        conversation.setLastActiveAt(now);
        conversation.setStatus(ImConversationStatus.ACTIVE);
        conversation.setMetadataJson("{}");
        return conversation;
    }

    private ImConversationMemberPo conversationMember(Long conversationId, Long userId, ImDeliveryMode deliveryMode,
                                                      ImMembershipStatus status) {
        ImConversationMemberPo member = new ImConversationMemberPo();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setLastReadSeq(0L);
        member.setDeliveryMode(deliveryMode);
        member.setMuted(false);
        member.setStatus(status);
        member.setMetadataJson("{}");
        return member;
    }

    private ImMessagePo message(Long conversationId, Long senderUserId, Long messageSeq, String clientMsgId,
                                ImMessageType type, ImMessageStatus status, Instant now) {
        ImMessagePo message = new ImMessagePo();
        message.setConversationId(conversationId);
        message.setSenderUserId(senderUserId);
        message.setMessageSeq(messageSeq);
        message.setClientMsgId(clientMsgId);
        message.setMessageType(type);
        message.setContent("hello");
        message.setPayloadJson("{\"body\":\"hello\"}");
        message.setStatus(status);
        message.setSentAt(now);
        message.setMetadataJson("{}");
        return message;
    }

    private ImOutboxPo outbox(Long conversationId, Long messageId, Long messageSeq, ImOutboxEventType eventType,
                              ImOutboxStatus status, Instant availableAt) {
        ImOutboxPo outbox = new ImOutboxPo();
        outbox.setConversationId(conversationId);
        outbox.setMessageId(messageId);
        outbox.setMessageSeq(messageSeq);
        outbox.setEventType(eventType);
        outbox.setStatus(status);
        outbox.setAvailableAt(availableAt);
        outbox.setAttempts(0);
        outbox.setMetadataJson("{}");
        return outbox;
    }

    private ImInboxPo inbox(Long userId, Long conversationId, Long messageId, Long messageSeq) {
        ImInboxPo inbox = new ImInboxPo();
        inbox.setUserId(userId);
        inbox.setConversationId(conversationId);
        inbox.setMessageId(messageId);
        inbox.setMessageSeq(messageSeq);
        inbox.setRead(false);
        inbox.setMetadataJson("{}");
        return inbox;
    }

    private ImGlobalMutePo globalMute(Long userId, ImGlobalMuteScopeType scopeType, Long scopeId,
                                      ImGovernanceStatus status, Instant expiresAt) {
        ImGlobalMutePo mute = new ImGlobalMutePo();
        mute.setUserId(userId);
        mute.setScopeType(scopeType);
        mute.setScopeId(scopeId);
        mute.setStatus(status);
        mute.setExpiresAt(expiresAt);
        mute.setReason("test");
        mute.setMetadataJson("{}");
        return mute;
    }

    private ImDmBlockPo dmBlock(Long blockerUserId, Long blockedUserId, ImGovernanceStatus status) {
        ImDmBlockPo block = new ImDmBlockPo();
        block.setBlockerUserId(blockerUserId);
        block.setBlockedUserId(blockedUserId);
        block.setStatus(status);
        block.setReason("test");
        block.setMetadataJson("{}");
        return block;
    }

    private ImBanPo ban(ImSurfaceType surfaceType, Long surfaceId, Long userId, Long actorUserId,
                        ImGovernanceStatus status, Instant expiresAt) {
        ImBanPo ban = new ImBanPo();
        ban.setSurfaceType(surfaceType);
        ban.setSurfaceId(surfaceId);
        ban.setUserId(userId);
        ban.setActorUserId(actorUserId);
        ban.setStatus(status);
        ban.setReason("test");
        ban.setExpiresAt(expiresAt);
        ban.setMetadataJson("{}");
        return ban;
    }

    private ImWsTicketPo wsTicket(String tokenHash, Long userId, ImWsTicketStatus status, Instant expiresAt) {
        ImWsTicketPo ticket = new ImWsTicketPo();
        ticket.setTokenHash(tokenHash);
        ticket.setUserId(userId);
        ticket.setRolesJson("[\"USER\"]");
        ticket.setStatus(status);
        ticket.setExpiresAt(expiresAt);
        ticket.setMetadataJson("{}");
        return ticket;
    }

    private void assertColumn(Long id, String tableName, String columnName, String expected) {
        String actual = jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where id = ?",
                String.class,
                id);
        assertThat(actual).isEqualTo(expected);
    }
}
