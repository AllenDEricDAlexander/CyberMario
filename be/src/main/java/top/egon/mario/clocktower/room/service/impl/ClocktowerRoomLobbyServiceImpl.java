package top.egon.mario.clocktower.room.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomBoardSwitchRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomInvitationCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomMemberActionRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatReleaseRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomInvitationResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomMemberResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomReservationResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomMutationPolicy;
import top.egon.mario.clocktower.room.policy.ClocktowerSeatAssignmentPolicy;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.context.RoomPrincipal;
import top.egon.mario.room.facade.RoomFacade;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;
import top.egon.mario.room.service.RoomException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerRoomLobbyServiceImpl implements ClocktowerRoomLobbyService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOBBY = "LOBBY";
    private static final String SEAT_STATUS_OPEN = "OPEN";
    private static final String SEAT_STATUS_OCCUPIED = "OCCUPIED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String MEMBER_TYPE_OWNER = "OWNER";
    private static final String MEMBER_TYPE_MEMBER = "MEMBER";
    private static final String MEMBER_TYPE_SPECTATOR = "SPECTATOR";
    private static final String IM_CHANNEL_ROOM = "ROOM";
    private static final String IM_GROUP_PUBLIC = "PUBLIC";
    private static final String IM_CONVERSATION_ROOM = "ROOM";
    private static final String INVITATION_TYPE_SEAT_REQUEST = "SEAT_REQUEST";

    private final RoomFacade roomFacade;
    private final ImFacade imFacade;
    private final RoomSpaceRepository roomSpaceRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final ClocktowerRoomSeatRepository seatRepository;
    private final ClocktowerBoardService boardService;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final ClocktowerSeatAssignmentPolicy seatAssignmentPolicy;
    private final ImChannelRepository imChannelRepository;
    private final ImGroupRepository imGroupRepository;
    private final ImConversationRepository imConversationRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ClocktowerRoomResponse createRoom(ClocktowerRoomCreateRequest request, RbacPrincipal principal) {
        accessPolicy.requireAuthenticated(principal);
        ClocktowerScriptCode scriptCode = requireScriptCode(request.scriptCode());
        int playerCount = requirePlayerCount(request.playerCount());
        List<String> roleCodes = resolveCreateRoomRoleCodes(request, principal);
        validateBoard(scriptCode, playerCount, roleCodes);
        String resolvedSeatingPolicy = seatingPolicy(request.seatingPolicy());

        Long temporaryContextId = nextTemporaryContextId();
        RoomFacade.RoomView created = roomCall(() -> roomFacade.createRoom(
                ClocktowerRoomMutationPolicy.CONTEXT_TYPE, temporaryContextId, principal.userId(),
                visibility(request)));
        RoomSpacePo room = lockedRoom(created.roomId());
        room.setContextId(room.getId());
        room.setName(StringUtils.hasText(request.name()) ? request.name() : "Clocktower Room " + room.getId());
        room.setCapacity(playerCount);
        room.setLastActiveAt(Instant.now());

        ClocktowerRoomProfilePo profile = new ClocktowerRoomProfilePo();
        profile.setRoomId(room.getId());
        profile.setScriptCode(scriptCode.name());
        profile.setStorytellerUserId(principal.userId());
        profile.setPlayerCount(playerCount);
        profile.setStatus(STATUS_LOBBY);
        profile.setLastActiveAt(Instant.now());
        profile.setMetadataJson(writeJson(Map.of("seatingPolicy", resolvedSeatingPolicy)));
        profileRepository.save(profile);

        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            String roleCode = seatNo <= roleCodes.size() ? roleCodes.get(seatNo - 1) : null;
            seatRepository.save(openSeat(room.getId(), seatNo, roleCode));
        }

        Long publicConversationId = ensurePublicConversation(room.getId(), principal.userId());
        return toResponse(room, profile, publicConversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerRoomResponse> listVisibleRooms(RbacPrincipal principal) {
        List<RoomSpacePo> rooms = roomSpaceRepository
                .findByContextTypeAndVisibilityInAndStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(
                        ClocktowerRoomMutationPolicy.CONTEXT_TYPE, List.of(VISIBILITY_PUBLIC, "PRIVATE"),
                        STATUS_ACTIVE);
        Map<Long, ClocktowerRoomProfilePo> profiles = profileRepository.findByRoomIdInAndDeletedFalse(
                        rooms.stream().map(RoomSpacePo::getId).toList())
                .stream()
                .collect(Collectors.toMap(ClocktowerRoomProfilePo::getRoomId, Function.identity(),
                        (left, right) -> left));
        return rooms.stream()
                .filter(room -> profiles.containsKey(room.getId()))
                .filter(room -> accessPolicy.canView(room, profiles.get(room.getId()), principal))
                .map(room -> toResponse(room, profiles.get(room.getId()), publicConversationId(room.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClocktowerRoomResponse lobby(Long roomId, RbacPrincipal principal) {
        RoomSpacePo room = room(roomId);
        ClocktowerRoomProfilePo profile = profile(roomId);
        if (!accessPolicy.canView(room, profile, principal)) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_FORBIDDEN");
        }
        return toResponse(room, profile, publicConversationId(room.getId()));
    }

    @Override
    @Transactional
    public ClocktowerRoomResponse switchBoard(Long roomId, ClocktowerRoomBoardSwitchRequest request,
                                              RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireOwner(room, principal);
        requireLobby(profile);
        int nextPlayerCount = requirePlayerCount(request.playerCount());
        ClocktowerScriptCode scriptCode = request.scriptCode() == null
                ? ClocktowerScriptCode.valueOf(profile.getScriptCode())
                : request.scriptCode();
        List<String> roleCodes = resolveSwitchRoleCodes(request, scriptCode, nextPlayerCount, principal);
        validateBoard(scriptCode, nextPlayerCount, roleCodes);

        List<ClocktowerRoomSeatPo> seats = seatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        if (nextPlayerCount < profile.getPlayerCount()) {
            if (seats.stream().anyMatch(seat -> seat.getSeatNo() > nextPlayerCount && seat.getUserId() != null)) {
                throw new ClocktowerException("CLOCKTOWER_SEAT_OCCUPIED");
            }
            if (roomCall(() -> roomFacade.activeReservations(roomId)).stream()
                    .anyMatch(reservation -> reservation.targetSeatNo() != null
                            && reservation.targetSeatNo() > nextPlayerCount)) {
                throw new ClocktowerException("CLOCKTOWER_SEAT_RESERVED");
            }
        }

        Map<Integer, ClocktowerRoomSeatPo> seatByNo = seats.stream()
                .collect(Collectors.toMap(ClocktowerRoomSeatPo::getSeatNo, Function.identity(),
                        (left, right) -> left));
        for (int seatNo = 1; seatNo <= nextPlayerCount; seatNo++) {
            if (!seatByNo.containsKey(seatNo)) {
                String roleCode = seatNo <= roleCodes.size() ? roleCodes.get(seatNo - 1) : null;
                seatRepository.save(openSeat(roomId, seatNo, roleCode));
            }
        }
        profile.setScriptCode(scriptCode.name());
        profile.setPlayerCount(nextPlayerCount);
        profile.setLastActiveAt(Instant.now());
        room.setCapacity(nextPlayerCount);
        room.setLastActiveAt(Instant.now());
        return toResponse(room, profile, publicConversationId(roomId));
    }

    @Override
    @Transactional
    public ClocktowerRoomResponse enterRoom(Long roomId, RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireEnterAllowed(room, profile, principal);
        RoomMemberPo member = enterOrTouchMember(room, principal, null);
        ensurePublicConversation(room.getId(), member.getUserId());
        profile.setLastActiveAt(Instant.now());
        room.setLastActiveAt(Instant.now());
        return toResponse(room, profile, publicConversationId(roomId));
    }

    @Override
    @Transactional
    public void heartbeat(Long roomId, RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireEnterAllowed(room, profile, principal);
        roomRun(() -> roomFacade.heartbeat(roomId, roomPrincipal(principal, null)));
        profile.setLastActiveAt(Instant.now());
        room.setLastActiveAt(Instant.now());
    }

    @Override
    @Transactional
    public ClocktowerSeatResponse claimSeat(Long roomId, int seatNo, ClocktowerSeatClaimRequest request,
                                            RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireEnterAllowed(room, profile, principal);
        ClocktowerRoomSeatPo seat = lockedSeat(roomId, seatNo);
        requireSeatInBoard(profile, seatNo);
        RoomMemberPo member = enterOrTouchMember(room, principal, request == null ? null : request.displayName());
        ClocktowerSeatAssignmentPolicy.SeatClaimDecision decision = seatAssignmentPolicy.decideClaim(
                profile, seat, principal, seatingPolicy(profile));
        if (decision == ClocktowerSeatAssignmentPolicy.SeatClaimDecision.RESERVE) {
            if (seatAssignmentPolicy.activeSeatInvitation(roomId, principal.userId(), seatNo) == null) {
                roomCall(() -> roomFacade.invite(roomId, principal.userId(), INVITATION_TYPE_SEAT_REQUEST,
                        seatNo, null));
            }
            ensurePublicConversation(room.getId(), member.getUserId());
            return toSeatResponse(seat);
        }
        if (decision == ClocktowerSeatAssignmentPolicy.SeatClaimDecision.ACCEPT_INVITATION) {
            RoomInvitationPo invitation = seatAssignmentPolicy.activeSeatInvitation(roomId, principal.userId(), seatNo);
            acceptInvitation(roomId, invitation.getId(), principal);
            return toSeatResponse(seatRepository.findByRoomIdAndSeatNo(roomId, seatNo).orElseThrow());
        }
        assignSeat(room, seat, member, principal, request == null ? null : request.displayName());
        ensurePublicConversation(room.getId(), member.getUserId());
        return toSeatResponse(seat);
    }

    @Override
    @Transactional
    public ClocktowerSeatResponse releaseSeat(Long roomId, int seatNo, ClocktowerSeatReleaseRequest request,
                                              RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        requireLobby(profile);
        ClocktowerRoomSeatPo seat = lockedSeat(roomId, seatNo);
        Long targetUserId = request == null || request.targetUserId() == null ? seat.getUserId() : request.targetUserId();
        if (targetUserId == null) {
            return toSeatResponse(seat);
        }
        if (!Objects.equals(room.getOwnerUserId(), principal == null ? null : principal.userId())
                && !Objects.equals(targetUserId, principal == null ? null : principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_FORBIDDEN");
        }
        if (!Objects.equals(seat.getUserId(), targetUserId)) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND");
        }
        releaseSeatAssignment(room, seat, true);
        return toSeatResponse(seat);
    }

    @Override
    @Transactional
    public ClocktowerRoomInvitationResponse createInvitation(Long roomId, ClocktowerRoomInvitationCreateRequest request,
                                                             RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireOwner(room, principal);
        requireLobby(profile);
        if (request == null || request.inviteeUserId() == null) {
            throw new ClocktowerException("CLOCKTOWER_INVITEE_REQUIRED");
        }
        if (request.targetSeatNo() != null) {
            requireSeatInBoard(profile, request.targetSeatNo());
            ClocktowerRoomSeatPo seat = lockedSeat(roomId, request.targetSeatNo());
            if (seat.getUserId() != null) {
                throw new ClocktowerException("CLOCKTOWER_SEAT_OCCUPIED");
            }
        }
        return ClocktowerRoomInvitationResponse.from(roomCall(() -> roomFacade.invite(roomId, request.inviteeUserId(),
                StringUtils.hasText(request.invitationType()) ? request.invitationType() : "SEAT",
                request.targetSeatNo(), request.expiresAt())));
    }

    @Override
    @Transactional
    public ClocktowerRoomInvitationResponse acceptInvitation(Long roomId, Long invitationId, RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireEnterAllowed(room, profile, principal);
        RoomInvitationPo invitation = lockedActiveInvitation(roomId, invitationId);
        if (isSeatRequest(invitation)) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_REQUEST_PENDING_APPROVAL");
        }
        if (invitation.getTargetSeatNo() == null) {
            RoomFacade.RoomInvitationView accepted = roomCall(() -> roomFacade.acceptInvitation(
                    roomId, invitationId, roomPrincipal(principal, null)));
            ensurePublicConversation(room.getId(), principal.userId());
            return ClocktowerRoomInvitationResponse.from(accepted);
        }
        requireSeatInBoard(profile, invitation.getTargetSeatNo());
        ClocktowerRoomSeatPo targetSeat = lockedSeat(roomId, invitation.getTargetSeatNo());
        if (targetSeat.getUserId() != null && !targetSeat.getUserId().equals(principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_OCCUPIED");
        }
        RoomFacade.RoomInvitationView accepted = roomCall(() -> roomFacade.acceptInvitation(
                roomId, invitationId, roomPrincipal(principal, null)));
        RoomMemberPo member = roomMemberRepository.findActiveByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MEMBER_NOT_FOUND"));
        assignSeat(room, targetSeat, member, principal, null);
        ensurePublicConversation(room.getId(), member.getUserId());
        return ClocktowerRoomInvitationResponse.from(accepted);
    }

    @Override
    @Transactional
    public ClocktowerRoomInvitationResponse declineInvitation(Long roomId, Long invitationId, RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        ClocktowerRoomProfilePo profile = lockedProfile(roomId);
        accessPolicy.requireEnterAllowed(room, profile, principal);
        lockedActiveInvitation(roomId, invitationId);
        return ClocktowerRoomInvitationResponse.from(roomCall(() -> roomFacade.declineInvitation(
                roomId, invitationId, roomPrincipal(principal, null))));
    }

    @Override
    @Transactional
    public void kickMember(Long roomId, Long userId, ClocktowerRoomMemberActionRequest request,
                           RbacPrincipal principal) {
        RoomSpacePo room = lockedRoom(roomId);
        lockedProfile(roomId);
        accessPolicy.requireOwner(room, principal);
        ClocktowerRoomSeatPo seat = seatRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (request != null && request.ban()) {
            roomCall(() -> roomFacade.ban(roomId, userId, banDuration(request), request.reason()));
        } else {
            roomRun(() -> roomFacade.kick(roomId, userId, request == null ? null : request.reason()));
        }
        if (seat != null) {
            releaseSeatAssignment(room, seat, false);
        }
    }

    private void roomRun(Runnable operation) {
        roomCall(() -> {
            operation.run();
            return null;
        });
    }

    private <T> T roomCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RoomException ex) {
            throw new ClocktowerException(clocktowerRoomCode(ex.getMessage()));
        }
    }

    private String clocktowerRoomCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "CLOCKTOWER_ROOM_ERROR";
        }
        if (code.startsWith("ROOM_")) {
            return "CLOCKTOWER_" + code.substring("ROOM_".length());
        }
        return "CLOCKTOWER_ROOM_ERROR";
    }

    private List<String> resolveCreateRoomRoleCodes(ClocktowerRoomCreateRequest request, RbacPrincipal principal) {
        if (request.boardConfigId() != null || StringUtils.hasText(request.boardCode())) {
            ClocktowerBoardConfigResponse board = boardService.usableBoard(request.boardConfigId(), request.boardCode(),
                    principal);
            if (!board.valid() || board.scriptCode() != request.scriptCode()
                    || board.playerCount() != request.playerCount()) {
                throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
            }
            return board.roleCodes();
        }
        return request.roleCodes() == null ? List.of() : request.roleCodes();
    }

    private List<String> resolveSwitchRoleCodes(ClocktowerRoomBoardSwitchRequest request,
                                                ClocktowerScriptCode scriptCode,
                                                int playerCount,
                                                RbacPrincipal principal) {
        if (request.boardConfigId() != null || StringUtils.hasText(request.boardCode())) {
            ClocktowerBoardConfigResponse board = boardService.usableBoard(request.boardConfigId(), request.boardCode(),
                    principal);
            if (!board.valid() || board.scriptCode() != scriptCode || board.playerCount() != playerCount) {
                throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
            }
            return board.roleCodes();
        }
        return request.roleCodes() == null ? List.of() : request.roleCodes();
    }

    private void validateBoard(ClocktowerScriptCode scriptCode, int playerCount, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        BoardValidationResponse validation = boardService.validate(new ClocktowerBoardValidateRequest(
                scriptCode, playerCount, roleCodes));
        if (!validation.valid()) {
            throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
        }
    }

    private RoomMemberPo enterOrTouchMember(RoomSpacePo room, RbacPrincipal principal, String displayName) {
        Instant now = Instant.now();
        return roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), principal.userId())
                .map(member -> {
                    if (StringUtils.hasText(displayName)) {
                        member.setDisplayName(displayName);
                    }
                    member.setLastActiveAt(now);
                    return member;
                })
                .orElseGet(() -> {
                    RoomMemberPo member = new RoomMemberPo();
                    member.setRoomId(room.getId());
                    member.setUserId(principal.userId());
                    member.setMemberType(MEMBER_TYPE_SPECTATOR);
                    member.setStatus(STATUS_ACTIVE);
                    member.setActiveStatus(true);
                    member.setDisplayName(displayName(principal, displayName));
                    member.setJoinedAt(now);
                    member.setLastActiveAt(now);
                    room.setCurrentMemberCount(room.getCurrentMemberCount() + 1);
                    return roomMemberRepository.save(member);
                });
    }

    private void assignSeat(RoomSpacePo room, ClocktowerRoomSeatPo seat, RoomMemberPo member,
                            RbacPrincipal principal, String displayName) {
        seatRepository.findByRoomIdAndUserId(room.getId(), member.getUserId())
                .filter(previous -> !previous.getId().equals(seat.getId()))
                .ifPresent(previous -> releaseSeatAssignment(room, previous, false));
        if (!MEMBER_TYPE_OWNER.equals(member.getMemberType())) {
            member.setMemberType(MEMBER_TYPE_MEMBER);
        }
        member.setSeatNo(seat.getSeatNo());
        member.setDisplayName(displayName(principal, displayName));
        seat.setRoomMemberId(member.getId());
        seat.setUserId(member.getUserId());
        seat.setDisplayName(displayName(principal, displayName));
        seat.setStatus(SEAT_STATUS_OCCUPIED);
        setReady(seat, true);
    }

    private void releaseSeatAssignment(RoomSpacePo room, ClocktowerRoomSeatPo seat, boolean keepMember) {
        Long userId = seat.getUserId();
        if (keepMember && userId != null) {
            roomMemberRepository.findActiveByRoomIdAndUserId(room.getId(), userId).ifPresent(member -> {
                member.setMemberType(Objects.equals(room.getOwnerUserId(), userId)
                        ? MEMBER_TYPE_OWNER : MEMBER_TYPE_SPECTATOR);
                member.setSeatNo(null);
            });
        }
        seat.setRoomMemberId(null);
        seat.setUserId(null);
        seat.setDisplayName("Seat " + seat.getSeatNo());
        seat.setStatus(SEAT_STATUS_OPEN);
        setReady(seat, false);
    }

    private ClocktowerRoomResponse toResponse(RoomSpacePo room, ClocktowerRoomProfilePo profile,
                                              Long publicConversationId) {
        List<ClocktowerSeatResponse> seats = seatRepository.findByRoomIdOrderBySeatNoAsc(room.getId()).stream()
                .filter(seat -> seat.getSeatNo() <= profile.getPlayerCount())
                .sorted(Comparator.comparingInt(ClocktowerRoomSeatPo::getSeatNo))
                .map(this::toSeatResponse)
                .toList();
        List<ClocktowerRoomMemberResponse> members = roomMemberRepository.findActiveByRoomId(room.getId()).stream()
                .map(ClocktowerRoomMemberResponse::from)
                .toList();
        List<ClocktowerRoomReservationResponse> reservations = roomInvitationRepository
                .findActiveTargetSeatReservations(room.getId(), Instant.now()).stream()
                .map(ClocktowerRoomReservationResponse::from)
                .toList();
        return new ClocktowerRoomResponse(room.getId(), room.getRoomCode(), room.getName(),
                ClocktowerScriptCode.valueOf(profile.getScriptCode()), roomStatus(profile.getStatus()),
                ClocktowerPhase.LOBBY, profile.getPlayerCount(), profile.getStorytellerUserId(),
                profile.getCurrentGameId(), seats, publicConversationId, members, reservations);
    }

    private ClocktowerSeatResponse toSeatResponse(ClocktowerRoomSeatPo seat) {
        return ClocktowerSeatResponse.from(seat, ready(seat));
    }

    private Long ensurePublicConversation(Long roomId, Long participantUserId) {
        ImChannelPo channel = imFacade.ensureChannel(ClocktowerRoomMutationPolicy.CONTEXT_TYPE, roomId,
                IM_CHANNEL_ROOM);
        ImGroupPo group = imFacade.ensureGroup(channel.getId(), IM_GROUP_PUBLIC);
        Collection<Long> participants = participantUserId == null ? List.of() : List.of(participantUserId);
        return imFacade.ensureConversation(group.getId(), IM_CONVERSATION_ROOM, roomId, IM_CONVERSATION_ROOM,
                participants).getId();
    }

    private Long publicConversationId(Long roomId) {
        return imChannelRepository
                .findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
                        ClocktowerRoomMutationPolicy.CONTEXT_TYPE, roomId, IM_CHANNEL_ROOM)
                .flatMap(channel -> imGroupRepository.findByChannelIdAndGroupKeyAndDeletedFalse(
                        channel.getId(), IM_GROUP_PUBLIC))
                .flatMap(group -> imConversationRepository
                        .findByGroupIdAndScopeTypeAndScopeIdAndConversationTypeAndParticipantKeyAndDeletedFalse(
                                group.getId(), IM_CONVERSATION_ROOM, roomId, IM_CONVERSATION_ROOM,
                                IM_CONVERSATION_ROOM + ":" + roomId))
                .map(ImConversationPo::getId)
                .orElse(null);
    }

    private ClocktowerRoomSeatPo openSeat(Long roomId, int seatNo, String roleCode) {
        ClocktowerRoomSeatPo seat = new ClocktowerRoomSeatPo();
        seat.setRoomId(roomId);
        seat.setSeatNo(seatNo);
        seat.setRoleCode(roleCode);
        seat.setDisplayName("Seat " + seatNo);
        seat.setStatus(SEAT_STATUS_OPEN);
        seat.setMetadataJson(writeJson(Map.of("ready", false)));
        return seat;
    }

    private RoomSpacePo lockedRoom(Long roomId) {
        if (roomId == null) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_ID_REQUIRED");
        }
        return roomSpaceRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }

    private RoomSpacePo room(Long roomId) {
        if (roomId == null) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_ID_REQUIRED");
        }
        return roomSpaceRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }

    private ClocktowerRoomProfilePo lockedProfile(Long roomId) {
        return profileRepository.findLockedByRoomId(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_PROFILE_NOT_FOUND"));
    }

    private ClocktowerRoomProfilePo profile(Long roomId) {
        return profileRepository.findByRoomIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_PROFILE_NOT_FOUND"));
    }

    private ClocktowerRoomSeatPo lockedSeat(Long roomId, int seatNo) {
        return seatRepository.findLockedByRoomIdAndSeatNo(roomId, seatNo)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
    }

    private RoomInvitationPo lockedActiveInvitation(Long roomId, Long invitationId) {
        RoomInvitationPo invitation = roomInvitationRepository.findActiveByIdAndRoomId(invitationId, roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_INVITATION_NOT_FOUND"));
        entityManager.lock(invitation, LockModeType.PESSIMISTIC_WRITE);
        return invitation;
    }

    private void requireLobby(ClocktowerRoomProfilePo profile) {
        if (!STATUS_LOBBY.equals(profile.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_NOT_LOBBY");
        }
    }

    private void requireSeatInBoard(ClocktowerRoomProfilePo profile, int seatNo) {
        if (seatNo < 1 || seatNo > profile.getPlayerCount()) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND");
        }
    }

    private ClocktowerScriptCode requireScriptCode(ClocktowerScriptCode scriptCode) {
        if (scriptCode == null) {
            throw new ClocktowerException("CLOCKTOWER_SCRIPT_REQUIRED");
        }
        return scriptCode;
    }

    private int requirePlayerCount(int playerCount) {
        if (playerCount <= 0) {
            throw new ClocktowerException("CLOCKTOWER_PLAYER_COUNT_INVALID");
        }
        return playerCount;
    }

    private Long nextTemporaryContextId() {
        for (int attempt = 0; attempt < 16; attempt++) {
            long candidate = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            if (roomSpaceRepository.findByContextTypeAndContextIdAndDeletedFalse(
                    ClocktowerRoomMutationPolicy.CONTEXT_TYPE, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ClocktowerException("CLOCKTOWER_ROOM_CONTEXT_UNAVAILABLE");
    }

    private String visibility(ClocktowerRoomCreateRequest request) {
        return StringUtils.hasText(request.visibility()) ? request.visibility() : VISIBILITY_PUBLIC;
    }

    private String seatingPolicy(String seatingPolicy) {
        if (!StringUtils.hasText(seatingPolicy)) {
            return ClocktowerSeatAssignmentPolicy.OPEN_SEATING;
        }
        if (ClocktowerSeatAssignmentPolicy.OPEN_SEATING.equals(seatingPolicy)
                || ClocktowerSeatAssignmentPolicy.INVITE_ONLY.equals(seatingPolicy)) {
            return seatingPolicy;
        }
        if (ClocktowerSeatAssignmentPolicy.APPROVAL_REQUIRED.equals(seatingPolicy)) {
            throw new ClocktowerException("CLOCKTOWER_SEATING_POLICY_UNSUPPORTED");
        }
        throw new ClocktowerException("CLOCKTOWER_SEATING_POLICY_INVALID");
    }

    private String seatingPolicy(ClocktowerRoomProfilePo profile) {
        Object value = metadata(profile.getMetadataJson()).get("seatingPolicy");
        return seatingPolicy(value == null ? null : value.toString());
    }

    private boolean isSeatRequest(RoomInvitationPo invitation) {
        Object value = metadata(invitation.getMetadataJson()).get("invitationType");
        return INVITATION_TYPE_SEAT_REQUEST.equals(value);
    }

    private RoomPrincipal roomPrincipal(RbacPrincipal principal, String displayName) {
        accessPolicy.requireAuthenticated(principal);
        return new RoomPrincipal(principal.userId(), displayName(principal, displayName));
    }

    private String displayName(RbacPrincipal principal, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (principal != null && StringUtils.hasText(principal.username())) {
            return principal.username();
        }
        return "User " + (principal == null ? "" : principal.userId());
    }

    private boolean ready(ClocktowerRoomSeatPo seat) {
        Object value = metadata(seat.getMetadataJson()).get("ready");
        return value instanceof Boolean ready && ready;
    }

    private void setReady(ClocktowerRoomSeatPo seat, boolean ready) {
        Map<String, Object> metadata = new LinkedHashMap<>(metadata(seat.getMetadataJson()));
        metadata.put("ready", ready);
        seat.setMetadataJson(writeJson(metadata));
    }

    private Map<String, Object> metadata(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }

    private ClocktowerRoomStatus roomStatus(String status) {
        if ("IN_GAME".equals(status)) {
            return ClocktowerRoomStatus.RUNNING;
        }
        if ("DISBANDED".equals(status)) {
            return ClocktowerRoomStatus.ENDED;
        }
        return ClocktowerRoomStatus.valueOf(status);
    }

    private Duration banDuration(ClocktowerRoomMemberActionRequest request) {
        if (request == null || request.banDurationSeconds() == null) {
            return null;
        }
        return Duration.ofSeconds(request.banDurationSeconds());
    }
}
