package top.egon.mario.clocktower.room.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerGrimoireEntryRepository;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerRoomServiceImpl implements ClocktowerRoomService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerBoardService boardService;
    private final ClocktowerEventService eventService;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerGrimoireEntryRepository grimoireEntryRepository;

    @Override
    @Transactional
    public ClocktowerRoomResponse create(ClocktowerRoomCreateRequest request, RbacPrincipal principal) {
        List<String> roleCodes = request.roleCodes() == null ? List.of() : request.roleCodes();
        if (!roleCodes.isEmpty()) {
            BoardValidationResponse validation = boardService.validate(new ClocktowerBoardValidateRequest(
                    request.scriptCode(), request.playerCount(), roleCodes));
            if (!validation.valid()) {
                throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
            }
        }
        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setRoomCode(nextRoomCode());
        room.setName(request.name());
        room.setScriptCode(request.scriptCode());
        room.setStatus(ClocktowerRoomStatus.LOBBY);
        room.setPhase(ClocktowerPhase.LOBBY);
        room.setPlayerCount(request.playerCount());
        room.setStorytellerUserId(principal == null ? null : principal.userId());
        room.setStorytellerMode(StringUtils.hasText(request.storytellerMode()) ? request.storytellerMode() : "HUMAN");
        room.setAllowSpectators(request.allowSpectators());
        room.setAllowPrivateChat(request.allowPrivateChat());
        ClocktowerRoomPo savedRoom = roomRepository.save(room);

        for (int i = 1; i <= request.playerCount(); i++) {
            ClocktowerSeatPo seat = new ClocktowerSeatPo();
            seat.setRoomId(savedRoom.getId());
            seat.setSeatNo(i);
            seat.setDisplayName("Seat " + i);
            seatRepository.save(seat);
        }
        appendRoomEvent(savedRoom, ClocktowerEventType.ROOM_CREATED, principal, Map.of("roomCode", savedRoom.getRoomCode()));
        return toResponse(savedRoom);
    }

    @Override
    public List<ClocktowerRoomResponse> list(RbacPrincipal principal) {
        Long userId = principal == null ? null : principal.userId();
        return roomRepository.findByStorytellerUserIdAndDeletedFalseOrderByIdDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ClocktowerRoomResponse get(Long roomId) {
        return toResponse(room(roomId));
    }

    @Override
    @Transactional
    public ClocktowerStartGameResponse start(Long roomId, ClocktowerRoomStartRequest request, RbacPrincipal principal) {
        ClocktowerRoomPo room = room(roomId);
        ClocktowerAccess.requireStoryteller(room, principal);
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        if (request.assignments().size() != seats.size()) {
            throw new ClocktowerException("CLOCKTOWER_ASSIGNMENT_COUNT_MISMATCH");
        }
        if (seats.stream().anyMatch(seat -> seat.getUserId() == null)) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_HAS_EMPTY_SEAT");
        }
        Map<Long, ClocktowerSeatPo> seatById = seats.stream()
                .collect(Collectors.toMap(ClocktowerSeatPo::getId, Function.identity()));
        Map<String, ClocktowerRolePo> roleByCode = roleRepository.findByRoleCodeInAndDeletedFalse(
                        request.assignments().stream().map(RoleAssignmentRequest::roleCode).toList())
                .stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, Function.identity(), (left, right) -> left));

        for (RoleAssignmentRequest assignment : request.assignments()) {
            ClocktowerSeatPo seat = seatById.get(assignment.seatId());
            ClocktowerRolePo role = roleByCode.get(assignment.roleCode());
            if (seat == null || role == null) {
                throw new ClocktowerException("CLOCKTOWER_ASSIGNMENT_INVALID");
            }
            seat.setRoleCode(role.getRoleCode());
            seat.setRoleType(role.getRoleType());
            seat.setAlignment(role.getAlignment());
            seatRepository.save(seat);

            ClocktowerGrimoireEntryPo entry = new ClocktowerGrimoireEntryPo();
            entry.setRoomId(roomId);
            entry.setSeatId(seat.getId());
            entry.setRoleCode(role.getRoleCode());
            entry.setRoleType(role.getRoleType());
            entry.setAlignment(role.getAlignment());
            grimoireEntryRepository.save(entry);

            eventService.append(new ClocktowerEventAppendRequest(roomId, ClocktowerEventType.ROLE_ASSIGNED,
                    ClocktowerPhase.FIRST_NIGHT, 0, 1, principal == null ? null : principal.userId(), null,
                    seat.getId(), ClocktowerVisibility.PRIVATE, List.of(seat.getId()),
                    Map.of("roleCode", role.getRoleCode())));
        }

        room.setStatus(ClocktowerRoomStatus.RUNNING);
        room.setPhase(ClocktowerPhase.FIRST_NIGHT);
        room.setCurrentNightNo(1);
        roomRepository.save(room);
        appendRoomEvent(room, ClocktowerEventType.PHASE_CHANGED, principal, Map.of("phase", "FIRST_NIGHT"));
        return new ClocktowerStartGameResponse(room.getId(), room.getStatus(), room.getPhase());
    }

    @Override
    @Transactional
    public ClocktowerSeatResponse join(Long roomId, ClocktowerRoomJoinRequest request, RbacPrincipal principal) {
        ClocktowerAccess.requireAuthenticated(principal);
        ClocktowerRoomPo room = room(roomId);
        ClocktowerSeatPo seat = request.seatNo() == null
                ? firstOpenSeat(roomId)
                : seatRepository.findByRoomIdAndSeatNoAndDeletedFalse(roomId, request.seatNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        if (seat.getUserId() != null && !seat.getUserId().equals(principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_OCCUPIED");
        }
        seat.setUserId(principal.userId());
        seat.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : principal.username());
        seat.setConnected(true);
        ClocktowerSeatPo savedSeat = seatRepository.save(seat);
        appendRoomEvent(room, ClocktowerEventType.PLAYER_JOINED, principal, Map.of("seatNo", savedSeat.getSeatNo()));
        return ClocktowerSeatResponse.from(savedSeat);
    }

    @Override
    @Transactional
    public void leave(Long roomId, RbacPrincipal principal) {
        ClocktowerAccess.requireAuthenticated(principal);
        ClocktowerRoomPo room = room(roomId);
        ClocktowerSeatPo seat = seatRepository.findByRoomIdAndUserIdAndDeletedFalse(roomId, principal.userId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        if (room.getStatus() == ClocktowerRoomStatus.LOBBY) {
            seat.setUserId(null);
            seat.setDisplayName("Seat " + seat.getSeatNo());
        }
        seat.setConnected(false);
        seatRepository.save(seat);
        appendRoomEvent(room, ClocktowerEventType.PLAYER_LEFT, principal, Map.of("seatNo", seat.getSeatNo()));
    }

    @Override
    @Transactional
    public ClocktowerRoomResponse updateSeat(Long roomId, Long seatId, ClocktowerUpdateSeatRequest request,
                                             RbacPrincipal principal) {
        ClocktowerRoomPo room = room(roomId);
        ClocktowerAccess.requireStoryteller(room, principal);
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(seatId, roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        if (StringUtils.hasText(request.displayName())) {
            seat.setDisplayName(request.displayName());
        }
        if (request.seatNo() != null) {
            seat.setSeatNo(request.seatNo());
        }
        seatRepository.save(seat);
        appendRoomEvent(room, ClocktowerEventType.SEAT_UPDATED, principal, Map.of("seatId", seatId));
        return toResponse(room);
    }

    private ClocktowerRoomPo room(Long roomId) {
        return roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }

    private ClocktowerSeatPo firstOpenSeat(Long roomId) {
        return seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId).stream()
                .filter(seat -> seat.getUserId() == null)
                .findFirst()
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NO_OPEN_SEAT"));
    }

    private ClocktowerRoomResponse toResponse(ClocktowerRoomPo room) {
        List<ClocktowerSeatResponse> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(room.getId())
                .stream()
                .sorted(Comparator.comparingInt(ClocktowerSeatPo::getSeatNo))
                .map(ClocktowerSeatResponse::publicView)
                .toList();
        return ClocktowerRoomResponse.from(room, seats);
    }

    private String nextRoomCode() {
        String roomCode;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                builder.append(ROOM_CODE_CHARS.charAt(RANDOM.nextInt(ROOM_CODE_CHARS.length())));
            }
            roomCode = builder.toString();
        } while (roomRepository.existsByRoomCodeAndDeletedFalse(roomCode));
        return roomCode;
    }

    private void appendRoomEvent(ClocktowerRoomPo room, ClocktowerEventType eventType, RbacPrincipal principal,
                                 Map<String, Object> payload) {
        eventService.append(new ClocktowerEventAppendRequest(room.getId(), eventType, room.getPhase(),
                room.getCurrentDayNo(), room.getCurrentNightNo(), principal == null ? null : principal.userId(),
                null, null, ClocktowerVisibility.PUBLIC, List.of(), payload));
    }
}
