package top.egon.mario.clocktower.room;

import org.mockito.stubbing.Answer;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRoleTypeCountResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.room.service.impl.ClocktowerRoomServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ClocktowerRoomTestFactory {

    private ClocktowerRoomTestFactory() {
    }

    static ClocktowerRoomService service() {
        List<ClocktowerRoomPo> rooms = new ArrayList<>();
        List<ClocktowerSeatPo> seats = new ArrayList<>();
        AtomicLong roomId = new AtomicLong(1L);
        AtomicLong seatId = new AtomicLong(1L);

        ClocktowerRoomRepository roomRepository = mock(ClocktowerRoomRepository.class);
        ClocktowerSeatRepository seatRepository = mock(ClocktowerSeatRepository.class);

        when(roomRepository.save(any(ClocktowerRoomPo.class))).thenAnswer(saveRoom(rooms, roomId));
        when(roomRepository.findByIdAndDeletedFalse(any())).thenAnswer(invocation -> rooms.stream()
                .filter(room -> !room.isDeleted() && room.getId().equals(invocation.getArgument(0)))
                .findFirst());
        when(roomRepository.existsByRoomCodeAndDeletedFalse(any())).thenReturn(false);
        when(roomRepository.findByStorytellerUserIdAndDeletedFalseOrderByIdDesc(any())).thenAnswer(invocation -> rooms.stream()
                .filter(room -> !room.isDeleted() && invocation.getArgument(0).equals(room.getStorytellerUserId()))
                .sorted(Comparator.comparing(ClocktowerRoomPo::getId).reversed())
                .toList());

        when(seatRepository.save(any(ClocktowerSeatPo.class))).thenAnswer(saveSeat(seats, seatId));
        when(seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(any())).thenAnswer(invocation -> seats.stream()
                .filter(seat -> !seat.isDeleted() && seat.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparingInt(ClocktowerSeatPo::getSeatNo))
                .toList());
        when(seatRepository.findByRoomIdAndSeatNoAndDeletedFalse(any(), anyInt())).thenAnswer(invocation -> seats.stream()
                .filter(seat -> !seat.isDeleted()
                        && seat.getRoomId().equals(invocation.getArgument(0))
                        && seat.getSeatNo() == (Integer) invocation.getArgument(1))
                .findFirst());
        when(seatRepository.findByRoomIdAndUserIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> seats.stream()
                .filter(seat -> !seat.isDeleted()
                        && seat.getRoomId().equals(invocation.getArgument(0))
                        && invocation.getArgument(1).equals(seat.getUserId()))
                .findFirst());
        when(seatRepository.findByIdAndRoomIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> seats.stream()
                .filter(seat -> !seat.isDeleted()
                        && seat.getId().equals(invocation.getArgument(0))
                        && seat.getRoomId().equals(invocation.getArgument(1)))
                .findFirst());

        ClocktowerBoardService boardService = new AlwaysValidBoardService();
        ClocktowerEventService eventService = request -> new ClocktowerEventResponse(1L, request.roomId(), 1L,
                request.eventType(), request.phase(), request.dayNo(), request.nightNo(), request.actorUserId(),
                request.actorSeatId(), request.targetSeatId(), request.visibility(), request.visibleSeatIds(),
                request.payload(), Instant.now());
        return new ClocktowerRoomServiceImpl(roomRepository, seatRepository, boardService, eventService);
    }

    private static Answer<ClocktowerRoomPo> saveRoom(List<ClocktowerRoomPo> rooms, AtomicLong nextId) {
        return invocation -> {
            ClocktowerRoomPo room = invocation.getArgument(0);
            if (room.getId() == null) {
                room.setId(nextId.getAndIncrement());
                rooms.add(room);
            }
            return room;
        };
    }

    private static Answer<ClocktowerSeatPo> saveSeat(List<ClocktowerSeatPo> seats, AtomicLong nextId) {
        return invocation -> {
            ClocktowerSeatPo seat = invocation.getArgument(0);
            if (seat.getId() == null) {
                seat.setId(nextId.getAndIncrement());
                seats.add(seat);
            }
            return seat;
        };
    }

    private static final class AlwaysValidBoardService implements ClocktowerBoardService {

        @Override
        public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
            return new BoardValidationResponse(true, new ClocktowerRoleTypeCountResponse(3, 0, 1, 1, 0, 0),
                    List.of(), List.of());
        }

        @Override
        public top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse generate(
                top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest request,
                RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse save(
                top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest request,
                RbacPrincipal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse> list() {
            return List.of();
        }

        @Override
        public void delete(Long boardId, RbacPrincipal principal) {
        }
    }
}
