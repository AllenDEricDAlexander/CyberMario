package top.egon.mario.room.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.room.context.RoomContext;
import top.egon.mario.room.context.RoomPrincipal;
import top.egon.mario.room.policy.RoomMutation;
import top.egon.mario.room.policy.RoomMutationPolicy;
import top.egon.mario.room.policy.RoomPolicyRegistry;
import top.egon.mario.room.po.RoomBanPo;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomBanRepository;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoomService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DECLINED = "DECLINED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_KICKED = "KICKED";
    private static final String STATUS_BANNED = "BANNED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String MEMBER_TYPE_OWNER = "OWNER";
    private static final String MEMBER_TYPE_MEMBER = "MEMBER";
    private static final String MEMBER_TYPE_SPECTATOR = "SPECTATOR";

    private final RoomSpaceRepository roomSpaceRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final RoomBanRepository roomBanRepository;
    private final RoomPolicyRegistry roomPolicyRegistry;
    private final ObjectMapper objectMapper;

    public RoomService(RoomSpaceRepository roomSpaceRepository,
                       RoomMemberRepository roomMemberRepository,
                       RoomInvitationRepository roomInvitationRepository,
                       RoomBanRepository roomBanRepository,
                       RoomPolicyRegistry roomPolicyRegistry,
                       ObjectMapper objectMapper) {
        this.roomSpaceRepository = roomSpaceRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.roomInvitationRepository = roomInvitationRepository;
        this.roomBanRepository = roomBanRepository;
        this.roomPolicyRegistry = roomPolicyRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RoomSpacePo createRoom(String contextType, Long contextId, Long ownerUserId, String visibility) {
        if (!StringUtils.hasText(contextType)) {
            throw new RoomException("ROOM_CONTEXT_TYPE_REQUIRED");
        }
        if (contextId == null) {
            throw new RoomException("ROOM_CONTEXT_ID_REQUIRED");
        }
        String effectiveVisibility = StringUtils.hasText(visibility) ? visibility : "PRIVATE";
        assertMutationAllowed(new RoomContext(contextType, contextId, null, ownerUserId, ownerUserId,
                ownerUserId == null ? null : MEMBER_TYPE_OWNER, STATUS_ACTIVE, effectiveVisibility, null),
                RoomMutation.CREATE_ROOM);
        RoomSpacePo room = new RoomSpacePo();
        room.setContextType(contextType);
        room.setContextId(contextId);
        room.setRoomCode(nextRoomCode());
        room.setName(contextType + "-" + contextId);
        room.setOwnerUserId(ownerUserId);
        room.setVisibility(effectiveVisibility);
        room.setStatus(STATUS_ACTIVE);
        room.setCapacity(0);
        room.setCurrentMemberCount(ownerUserId == null ? 0 : 1);
        room.setLastActiveAt(Instant.now());
        RoomSpacePo savedRoom = roomSpaceRepository.save(room);
        if (ownerUserId != null) {
            roomMemberRepository.save(newMember(savedRoom.getId(), ownerUserId, MEMBER_TYPE_OWNER,
                    null, "User " + ownerUserId, Instant.now()));
        }
        return savedRoom;
    }

    @Transactional
    public RoomMemberPo enterRoom(Long roomId, RoomPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        RoomPrincipal checkedPrincipal = requirePrincipal(principal);
        Instant now = Instant.now();
        assertMutationAllowed(room, checkedPrincipal.userId(),
                activeMemberRole(room.getId(), checkedPrincipal.userId()), RoomMutation.ENTER_ROOM, null);
        if (!VISIBILITY_PUBLIC.equals(room.getVisibility())
                && !Objects.equals(room.getOwnerUserId(), checkedPrincipal.userId())) {
            throw new RoomException("ROOM_NOT_PUBLIC");
        }
        assertNotBanned(room.getId(), checkedPrincipal.userId(), now);
        return roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), checkedPrincipal.userId())
                .orElseGet(() -> {
                    RoomMemberPo member = newMember(room.getId(), checkedPrincipal.userId(), MEMBER_TYPE_SPECTATOR,
                            null, displayName(checkedPrincipal), now);
                    room.setCurrentMemberCount(room.getCurrentMemberCount() + 1);
                    return roomMemberRepository.save(member);
                });
    }

    @Transactional
    public RoomInvitationPo invite(Long roomId, Long inviteeUserId, String invitationType,
                                   Integer targetSeatNo, Instant expiresAt) {
        RoomSpacePo room = lockedRoom(roomId);
        Long viewerUserId = ownerViewerUserId(room);
        assertMutationAllowed(room, viewerUserId, activeMemberRole(room.getId(), viewerUserId),
                RoomMutation.INVITE, targetSeatNo);
        Instant now = Instant.now();
        releaseExpiredInvitations(room.getId(), now);
        if (targetSeatNo != null) {
            if (roomMemberRepository.existsByRoomIdAndSeatNoAndActiveStatusTrueAndDeletedFalse(
                    room.getId(), targetSeatNo)) {
                throw new RoomException("ROOM_SEAT_OCCUPIED");
            }
            if (roomInvitationRepository.existsByRoomIdAndTargetSeatNoAndActiveStatusTrueAndDeletedFalse(
                    room.getId(), targetSeatNo)) {
                throw new RoomException("ROOM_SEAT_RESERVED");
            }
        }
        RoomInvitationPo invitation = new RoomInvitationPo();
        invitation.setRoomId(room.getId());
        invitation.setInviterUserId(room.getOwnerUserId() == null ? 0L : room.getOwnerUserId());
        invitation.setInviteeUserId(inviteeUserId);
        invitation.setInvitationCode(nextInvitationCode());
        invitation.setStatus(STATUS_PENDING);
        invitation.setActiveStatus(true);
        invitation.setTargetSeatNo(targetSeatNo);
        invitation.setExpiresAt(expiresAt);
        invitation.setMetadataJson(invitationMetadata(invitationType));
        return roomInvitationRepository.save(invitation);
    }

    @Transactional
    public RoomInvitationPo acceptInvitation(Long roomId, Long invitationId, RoomPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        RoomPrincipal checkedPrincipal = requirePrincipal(principal);
        Instant now = Instant.now();
        assertNotBanned(room.getId(), checkedPrincipal.userId(), now);
        RoomInvitationPo invitation = activeInvitation(room.getId(), invitationId);
        assertMutationAllowed(room, checkedPrincipal.userId(),
                activeMemberRole(room.getId(), checkedPrincipal.userId()), RoomMutation.ACCEPT_INVITATION,
                invitation.getTargetSeatNo());
        assertInvitationTarget(invitation, checkedPrincipal);
        if (isExpired(invitation, now)) {
            terminalizeInvitation(invitation, STATUS_EXPIRED, now);
            throw new RoomException("ROOM_INVITATION_EXPIRED");
        }
        if (invitation.getTargetSeatNo() != null
                && roomMemberRepository.existsByRoomIdAndSeatNoAndActiveStatusTrueAndDeletedFalse(
                room.getId(), invitation.getTargetSeatNo())) {
            throw new RoomException("ROOM_SEAT_OCCUPIED");
        }
        RoomMemberPo member = roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), checkedPrincipal.userId())
                .orElse(null);
        if (member == null) {
            roomMemberRepository.save(newMember(room.getId(), checkedPrincipal.userId(), MEMBER_TYPE_MEMBER,
                    invitation.getTargetSeatNo(), displayName(checkedPrincipal), now));
            room.setCurrentMemberCount(room.getCurrentMemberCount() + 1);
        } else {
            member.setMemberType(MEMBER_TYPE_MEMBER);
            member.setSeatNo(invitation.getTargetSeatNo());
            member.setDisplayName(displayName(checkedPrincipal));
        }
        terminalizeInvitation(invitation, STATUS_ACCEPTED, now);
        return invitation;
    }

    @Transactional
    public RoomInvitationPo declineInvitation(Long roomId, Long invitationId, RoomPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        RoomPrincipal checkedPrincipal = requirePrincipal(principal);
        RoomInvitationPo invitation = activeInvitation(room.getId(), invitationId);
        assertMutationAllowed(room, checkedPrincipal.userId(),
                activeMemberRole(room.getId(), checkedPrincipal.userId()), RoomMutation.DECLINE_INVITATION,
                invitation.getTargetSeatNo());
        assertInvitationTarget(invitation, checkedPrincipal);
        terminalizeInvitation(invitation, STATUS_DECLINED, Instant.now());
        return invitation;
    }

    @Transactional
    public void kick(Long roomId, Long targetUserId, String reason) {
        RoomSpacePo room = lockedRoom(roomId);
        Long viewerUserId = ownerViewerUserId(room);
        assertMutationAllowed(room, viewerUserId, activeMemberRole(room.getId(), viewerUserId),
                RoomMutation.KICK, null);
        RoomMemberPo member = roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), targetUserId)
                .orElseThrow(() -> new RoomException("ROOM_MEMBER_NOT_FOUND"));
        terminalizeMember(member, STATUS_KICKED, Instant.now());
        room.setCurrentMemberCount(Math.max(0, room.getCurrentMemberCount() - 1));
    }

    @Transactional
    public RoomBanPo ban(Long roomId, Long targetUserId, Duration duration, String reason) {
        RoomSpacePo room = lockedRoom(roomId);
        Long viewerUserId = ownerViewerUserId(room);
        assertMutationAllowed(room, viewerUserId, activeMemberRole(room.getId(), viewerUserId),
                RoomMutation.BAN, null);
        Instant now = Instant.now();
        Instant expiresAt = banExpiresAt(duration, now);
        roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), targetUserId)
                .ifPresent(member -> {
                    terminalizeMember(member, STATUS_BANNED, now);
                    room.setCurrentMemberCount(Math.max(0, room.getCurrentMemberCount() - 1));
                });
        cancelActiveInvitationsForUser(room.getId(), targetUserId, now);
        RoomBanPo ban = roomBanRepository.findByRoomIdAndUserIdAndDeletedFalse(room.getId(), targetUserId)
                .orElseGet(RoomBanPo::new);
        ban.setRoomId(room.getId());
        ban.setUserId(targetUserId);
        ban.setBannedByUserId(room.getOwnerUserId() == null ? 0L : room.getOwnerUserId());
        ban.setReason(reason);
        ban.setStatus(STATUS_ACTIVE);
        ban.setExpiresAt(expiresAt);
        return roomBanRepository.save(ban);
    }

    @Transactional
    public void heartbeat(Long roomId, RoomPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        RoomPrincipal checkedPrincipal = requirePrincipal(principal);
        assertMutationAllowed(room, checkedPrincipal.userId(),
                activeMemberRole(room.getId(), checkedPrincipal.userId()), RoomMutation.HEARTBEAT, null);
        int updated = roomMemberRepository.updateLastActiveAtForActiveMember(
                room.getId(), checkedPrincipal.userId(), Instant.now());
        if (updated == 0) {
            throw new RoomException("ROOM_MEMBER_NOT_FOUND");
        }
    }

    @Transactional
    public List<RoomInvitationPo> activeReservations(Long roomId) {
        RoomSpacePo room = lockedRoom(roomId);
        Long viewerUserId = ownerViewerUserId(room);
        assertMutationAllowed(room, viewerUserId, activeMemberRole(room.getId(), viewerUserId),
                RoomMutation.REFRESH_RESERVATIONS, null);
        Instant now = Instant.now();
        releaseExpiredInvitations(room.getId(), now);
        return roomInvitationRepository.findActiveTargetSeatReservations(room.getId(), now);
    }

    private RoomSpacePo lockedRoom(Long roomId) {
        if (roomId == null) {
            throw new RoomException("ROOM_ID_REQUIRED");
        }
        return roomSpaceRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new RoomException("ROOM_NOT_FOUND"));
    }

    private RoomInvitationPo activeInvitation(Long roomId, Long invitationId) {
        if (invitationId == null) {
            throw new RoomException("ROOM_INVITATION_ID_REQUIRED");
        }
        return roomInvitationRepository.findActiveByIdAndRoomId(invitationId, roomId)
                .orElseThrow(() -> new RoomException("ROOM_INVITATION_NOT_FOUND"));
    }

    private void assertMutationAllowed(RoomSpacePo room, Long viewerUserId, String memberRole,
                                       RoomMutation mutation, Integer requestedSeatNo) {
        assertMutationAllowed(new RoomContext(room.getContextType(), room.getContextId(), room.getId(),
                room.getOwnerUserId(), viewerUserId, memberRole, room.getStatus(), room.getVisibility(),
                requestedSeatNo), mutation);
    }

    private void assertMutationAllowed(RoomContext context, RoomMutation mutation) {
        RoomMutationPolicy policy = roomPolicyRegistry.resolve(context.contextType(), RoomMutationPolicy.class);
        if (!policy.canMutate(context, mutation)) {
            throw new RoomException("ROOM_MUTATION_FORBIDDEN");
        }
    }

    private Long ownerViewerUserId(RoomSpacePo room) {
        return room.getOwnerUserId() == null ? 0L : room.getOwnerUserId();
    }

    private String activeMemberRole(Long roomId, Long userId) {
        if (userId == null) {
            return null;
        }
        return roomMemberRepository.findActiveByRoomIdAndUserId(roomId, userId)
                .map(RoomMemberPo::getMemberType)
                .orElse(null);
    }

    private RoomMemberPo newMember(Long roomId, Long userId, String memberType, Integer seatNo,
                                   String displayName, Instant now) {
        RoomMemberPo member = new RoomMemberPo();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setMemberType(memberType);
        member.setStatus(STATUS_ACTIVE);
        member.setActiveStatus(true);
        member.setSeatNo(seatNo);
        member.setDisplayName(displayName);
        member.setJoinedAt(now);
        return member;
    }

    private void terminalizeMember(RoomMemberPo member, String status, Instant now) {
        member.setStatus(status);
        member.setActiveStatus(null);
        member.setLeftAt(now);
    }

    private void terminalizeInvitation(RoomInvitationPo invitation, String status, Instant now) {
        invitation.setStatus(status);
        invitation.setActiveStatus(null);
        if (STATUS_ACCEPTED.equals(status)) {
            invitation.setAcceptedAt(now);
        }
    }

    private void releaseExpiredInvitations(Long roomId, Instant now) {
        for (RoomInvitationPo invitation : roomInvitationRepository.findActiveByRoomId(roomId)) {
            if (isExpired(invitation, now)) {
                terminalizeInvitation(invitation, STATUS_EXPIRED, now);
            }
        }
    }

    private void cancelActiveInvitationsForUser(Long roomId, Long inviteeUserId, Instant now) {
        for (RoomInvitationPo invitation : roomInvitationRepository.findActiveByRoomIdAndInviteeUserId(
                roomId, inviteeUserId)) {
            terminalizeInvitation(invitation, STATUS_CANCELLED, now);
        }
    }

    private void assertNotBanned(Long roomId, Long userId, Instant now) {
        roomBanRepository.findActiveByRoomIdAndUserId(roomId, userId, now)
                .ifPresent(ban -> {
                    throw new RoomException("ROOM_USER_BANNED");
                });
    }

    private void assertInvitationTarget(RoomInvitationPo invitation, RoomPrincipal principal) {
        if (invitation.getInviteeUserId() != null && !invitation.getInviteeUserId().equals(principal.userId())) {
            throw new RoomException("ROOM_INVITATION_FORBIDDEN");
        }
    }

    private boolean isExpired(RoomInvitationPo invitation, Instant now) {
        return invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(now);
    }

    private Instant banExpiresAt(Duration duration, Instant now) {
        if (duration == null) {
            return null;
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new RoomException("ROOM_BAN_DURATION_INVALID");
        }
        return now.plus(duration);
    }

    private RoomPrincipal requirePrincipal(RoomPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new RoomException("ROOM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private String displayName(RoomPrincipal principal) {
        if (StringUtils.hasText(principal.displayName())) {
            return principal.displayName();
        }
        return "User " + principal.userId();
    }

    private String nextRoomCode() {
        String code;
        do {
            code = "ROOM-" + randomCode();
        } while (roomSpaceRepository.existsByRoomCodeAndDeletedFalse(code));
        return code;
    }

    private String nextInvitationCode() {
        String code;
        do {
            code = "INV-" + randomCode();
        } while (roomInvitationRepository.existsByInvitationCodeAndDeletedFalse(code));
        return code;
    }

    private String randomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String invitationMetadata(String invitationType) {
        if (!StringUtils.hasText(invitationType)) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(Map.of("invitationType", invitationType));
        } catch (JsonProcessingException ex) {
            throw new RoomException("ROOM_INVITATION_METADATA_INVALID");
        }
    }
}
