package top.egon.mario.clocktower.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.stubbing.Answer;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRoleTypeCountResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStatusMarkerPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerVotePo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerGrimoireEntryRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStatusMarkerRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.room.service.impl.ClocktowerRoomServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ClocktowerRoomTestFactory {

    private ClocktowerRoomTestFactory() {
    }

    public static ClocktowerRoomService service() {
        return context().roomService();
    }

    public static Context context() {
        List<ClocktowerRoomPo> rooms = new ArrayList<>();
        List<ClocktowerSeatPo> seats = new ArrayList<>();
        List<ClocktowerGrimoireEntryPo> entries = new ArrayList<>();
        List<ClocktowerEventPo> events = new ArrayList<>();
        List<ClocktowerNominationPo> nominations = new ArrayList<>();
        List<ClocktowerVotePo> votes = new ArrayList<>();
        List<ClocktowerStatusMarkerPo> markers = new ArrayList<>();
        List<ClocktowerStorytellerTaskPo> tasks = new ArrayList<>();
        AtomicLong roomId = new AtomicLong(1L);
        AtomicLong seatId = new AtomicLong(1L);
        AtomicLong entryId = new AtomicLong(1L);
        AtomicLong eventId = new AtomicLong(1L);
        AtomicLong nominationId = new AtomicLong(1L);
        AtomicLong voteId = new AtomicLong(1L);
        AtomicLong markerId = new AtomicLong(1L);
        ObjectMapper objectMapper = new ObjectMapper();

        ClocktowerRoomRepository roomRepository = mock(ClocktowerRoomRepository.class);
        ClocktowerSeatRepository seatRepository = mock(ClocktowerSeatRepository.class);
        ClocktowerEventRepository eventRepository = mock(ClocktowerEventRepository.class);
        ClocktowerNightOrderRepository nightOrderRepository = mock(ClocktowerNightOrderRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerGrimoireEntryRepository entryRepository = mock(ClocktowerGrimoireEntryRepository.class);
        ClocktowerNominationRepository nominationRepository = mock(ClocktowerNominationRepository.class);
        ClocktowerVoteRepository voteRepository = mock(ClocktowerVoteRepository.class);
        ClocktowerStatusMarkerRepository markerRepository = mock(ClocktowerStatusMarkerRepository.class);
        ClocktowerStorytellerTaskRepository taskRepository = mock(ClocktowerStorytellerTaskRepository.class);

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
        when(roleRepository.findByRoleCodeInAndDeletedFalse(any())).thenAnswer(invocation -> {
            Collection<String> roleCodes = invocation.getArgument(0);
            return roleCodes.stream().map(ClocktowerRoomTestFactory::role).toList();
        });
        when(nightOrderRepository.findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(
                any(), any(), any())).thenAnswer(invocation -> {
            String nightType = invocation.getArgument(1);
            List<String> roleCodes = new ArrayList<>(invocation.getArgument(2));
            return nightOrder(nightType).stream()
                    .filter(order -> roleCodes.contains(order.getRoleCode()))
                    .toList();
        });
        when(entryRepository.save(any(ClocktowerGrimoireEntryPo.class))).thenAnswer(invocation -> {
            ClocktowerGrimoireEntryPo entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(entryId.getAndIncrement());
                entries.add(entry);
            }
            return entry;
        });
        when(entryRepository.findByRoomIdAndDeletedFalseOrderBySeatIdAsc(any())).thenAnswer(invocation -> entries.stream()
                .filter(entry -> !entry.isDeleted() && entry.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerGrimoireEntryPo::getSeatId))
                .toList());
        when(eventRepository.save(any(ClocktowerEventPo.class))).thenAnswer(saveEvent(events, eventId));
        when(eventRepository.findTopByRoomIdAndDeletedFalseOrderByEventSeqDesc(any())).thenAnswer(invocation -> events.stream()
                .filter(event -> !event.isDeleted() && event.getRoomId().equals(invocation.getArgument(0)))
                .max(Comparator.comparing(ClocktowerEventPo::getEventSeq)));
        when(eventRepository.findByRoomIdAndDeletedFalseOrderByEventSeqAsc(any())).thenAnswer(invocation -> events.stream()
                .filter(event -> !event.isDeleted() && event.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerEventPo::getEventSeq))
                .toList());
        when(nominationRepository.save(any(ClocktowerNominationPo.class)))
                .thenAnswer(saveNomination(nominations, nominationId));
        when(nominationRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> nominations.stream()
                .filter(nomination -> !nomination.isDeleted() && nomination.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerNominationPo::getId))
                .toList());
        when(nominationRepository.findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(any(), any()))
                .thenAnswer(invocation -> nominations.stream()
                        .filter(nomination -> !nomination.isDeleted()
                                && nomination.getRoomId().equals(invocation.getArgument(0))
                                && nomination.getStatus().equals(invocation.getArgument(1)))
                        .max(Comparator.comparing(ClocktowerNominationPo::getId)));
        when(voteRepository.save(any(ClocktowerVotePo.class))).thenAnswer(saveVote(votes, voteId));
        when(voteRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> votes.stream()
                .filter(vote -> !vote.isDeleted() && vote.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerVotePo::getId))
                .toList());
        when(voteRepository.findByNominationIdAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> votes.stream()
                .filter(vote -> !vote.isDeleted() && vote.getNominationId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerVotePo::getId))
                .toList());
        when(voteRepository.findByNominationIdAndVoterSeatIdAndDeletedFalse(any(), any()))
                .thenAnswer(invocation -> votes.stream()
                        .filter(vote -> !vote.isDeleted()
                                && vote.getNominationId().equals(invocation.getArgument(0))
                                && vote.getVoterSeatId().equals(invocation.getArgument(1)))
                        .findFirst());
        when(markerRepository.save(any(ClocktowerStatusMarkerPo.class))).thenAnswer(saveMarker(markers, markerId));
        when(markerRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> markers.stream()
                .filter(marker -> !marker.isDeleted() && marker.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerStatusMarkerPo::getId))
                .toList());
        when(markerRepository.findByRoomIdAndActiveTrueAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> markers.stream()
                .filter(marker -> !marker.isDeleted()
                        && marker.isActive()
                        && marker.getRoomId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(ClocktowerStatusMarkerPo::getId))
                .toList());
        when(markerRepository.findByIdAndRoomIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> markers.stream()
                .filter(marker -> !marker.isDeleted()
                        && marker.getId().equals(invocation.getArgument(0))
                        && marker.getRoomId().equals(invocation.getArgument(1)))
                .findFirst());
        when(taskRepository.findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(any(), any()))
                .thenAnswer(invocation -> tasks.stream()
                        .filter(task -> !task.isDeleted()
                                && task.getRoomId().equals(invocation.getArgument(0))
                                && task.getStatus().equals(invocation.getArgument(1)))
                        .sorted(Comparator.comparing(ClocktowerStorytellerTaskPo::getSortOrder))
                        .toList());

        ClocktowerBoardService boardService = new AlwaysValidBoardService();
        ClocktowerEventService eventService = request -> {
            ClocktowerEventPo event = new ClocktowerEventPo();
            event.setRoomId(request.roomId());
            event.setEventSeq(events.stream()
                    .filter(saved -> saved.getRoomId().equals(request.roomId()))
                    .map(ClocktowerEventPo::getEventSeq)
                    .max(Long::compareTo)
                    .orElse(0L) + 1);
            event.setEventType(request.eventType());
            event.setPhase(request.phase());
            event.setDayNo(request.dayNo());
            event.setNightNo(request.nightNo());
            event.setActorUserId(request.actorUserId());
            event.setActorSeatId(request.actorSeatId());
            event.setTargetSeatId(request.targetSeatId());
            event.setVisibility(request.visibility());
            List<Long> visibleSeatIds = request.visibleSeatIds() == null ? List.of() : request.visibleSeatIds();
            java.util.Map<String, Object> payload = request.payload() == null ? java.util.Map.of() : request.payload();
            event.setVisibleSeatIdsJson(writeJson(objectMapper, visibleSeatIds));
            event.setPayloadJson(writeJson(objectMapper, payload));
            ClocktowerEventPo saved = eventRepository.save(event);
            return new ClocktowerEventResponse(saved.getId(), saved.getRoomId(), saved.getEventSeq(),
                    saved.getEventType(), saved.getPhase(), saved.getDayNo(), saved.getNightNo(),
                    saved.getActorUserId(), saved.getActorSeatId(), saved.getTargetSeatId(), saved.getVisibility(),
                    visibleSeatIds, payload, saved.getCreatedAt());
        };
        ClocktowerRoomService roomService = new ClocktowerRoomServiceImpl(roomRepository, seatRepository, boardService,
                eventService, roleRepository, entryRepository);
        return new Context(roomService, roomRepository, seatRepository, entryRepository, nightOrderRepository,
                roleRepository, eventRepository, eventService, objectMapper, nominationRepository, voteRepository,
                markerRepository, taskRepository);
    }

    static ClocktowerRolePo role(String roleCode) {
        ClocktowerRolePo role = new ClocktowerRolePo();
        role.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        role.setRoleCode(roleCode);
        role.setName(roleCode);
        role.setAbilityText(roleCode);
        role.setAlignment(switch (roleCode) {
            case "POISONER", "IMP" -> "EVIL";
            default -> "GOOD";
        });
        role.setRoleType(switch (roleCode) {
            case "POISONER" -> ClocktowerRoleType.MINION;
            case "IMP" -> ClocktowerRoleType.DEMON;
            default -> ClocktowerRoleType.TOWNSFOLK;
        });
        return role;
    }

    private static List<ClocktowerNightOrderPo> nightOrder(String nightType) {
        ClocktowerNightOrderPo poisoner = nightOrder("POISONER", nightType, 5);
        ClocktowerNightOrderPo empath = nightOrder("EMPATH", nightType, 20);
        return List.of(poisoner, empath);
    }

    private static ClocktowerNightOrderPo nightOrder(String roleCode, String nightType, int orderNo) {
        ClocktowerNightOrderPo order = new ClocktowerNightOrderPo();
        order.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        order.setRoleCode(roleCode);
        order.setNightType(nightType);
        order.setOrderNo(orderNo);
        order.setSortOrder(orderNo);
        order.setReminderText(roleCode);
        return order;
    }

    public record Context(
            ClocktowerRoomService roomService,
            ClocktowerRoomRepository roomRepository,
            ClocktowerSeatRepository seatRepository,
            ClocktowerGrimoireEntryRepository grimoireEntryRepository,
            ClocktowerNightOrderRepository nightOrderRepository,
            ClocktowerRoleRepository roleRepository,
            ClocktowerEventRepository eventRepository,
            ClocktowerEventService eventService,
            ObjectMapper objectMapper,
            ClocktowerNominationRepository nominationRepository,
            ClocktowerVoteRepository voteRepository,
            ClocktowerStatusMarkerRepository markerRepository,
            ClocktowerStorytellerTaskRepository storytellerTaskRepository
    ) {
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

    private static Answer<ClocktowerEventPo> saveEvent(List<ClocktowerEventPo> events, AtomicLong nextId) {
        return invocation -> {
            ClocktowerEventPo event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(nextId.getAndIncrement());
                event.setCreatedAt(Instant.now());
                events.add(event);
            }
            return event;
        };
    }

    private static Answer<ClocktowerNominationPo> saveNomination(List<ClocktowerNominationPo> nominations,
                                                                 AtomicLong nextId) {
        return invocation -> {
            ClocktowerNominationPo nomination = invocation.getArgument(0);
            if (nomination.getId() == null) {
                nomination.setId(nextId.getAndIncrement());
                nominations.add(nomination);
            }
            return nomination;
        };
    }

    private static Answer<ClocktowerVotePo> saveVote(List<ClocktowerVotePo> votes, AtomicLong nextId) {
        return invocation -> {
            ClocktowerVotePo vote = invocation.getArgument(0);
            if (vote.getId() == null) {
                vote.setId(nextId.getAndIncrement());
                votes.add(vote);
            }
            return vote;
        };
    }

    private static Answer<ClocktowerStatusMarkerPo> saveMarker(List<ClocktowerStatusMarkerPo> markers,
                                                               AtomicLong nextId) {
        return invocation -> {
            ClocktowerStatusMarkerPo marker = invocation.getArgument(0);
            if (marker.getId() == null) {
                marker.setId(nextId.getAndIncrement());
                markers.add(marker);
            }
            return marker;
        };
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_EVENT_JSON_INVALID", e);
        }
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
