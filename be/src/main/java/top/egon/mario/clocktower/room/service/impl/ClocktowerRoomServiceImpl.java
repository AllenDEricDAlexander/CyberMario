package top.egon.mario.clocktower.room.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerRoomServiceImpl implements ClocktowerRoomService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerBoardService boardService;
    private final ClocktowerEventService eventService;

    @Override
    @Transactional
    public ClocktowerRoomResponse create(ClocktowerRoomCreateRequest request, RbacPrincipal principal) {
        BoardValidationResponse validation = boardService.validate(new ClocktowerBoardValidateRequest(
                request.scriptCode(), request.playerCount(), request.roleCodes()));
        if (!validation.valid()) {
            throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
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
    public ClocktowerSeatResponse join(Long roomId, ClocktowerRoomJoinRequest request, RbacPrincipal principal) {
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
                .map(ClocktowerSeatResponse::from)
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
