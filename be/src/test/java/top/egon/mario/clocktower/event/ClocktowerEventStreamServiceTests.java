package top.egon.mario.clocktower.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventProjector;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.event.service.ViewerContext;
import top.egon.mario.clocktower.event.service.impl.ClocktowerEventServiceImpl;
import top.egon.mario.clocktower.event.service.impl.ClocktowerEventStreamServiceImpl;
import top.egon.mario.clocktower.event.web.ClocktowerEventStreamController;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClocktowerEventStreamServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerEventStreamService streamService = new ClocktowerEventStreamServiceImpl(
            context.eventRepository(), context.objectMapper());
    private final ClocktowerEventProjector projector = new ClocktowerEventProjector();
    private final ClocktowerEventServiceImpl eventService = new ClocktowerEventServiceImpl(
            context.eventRepository(), context.roomRepository(), projector, context.objectMapper(), streamService);

    @Test
    void streamBackfillsEventsAfterLastSeqForViewer() {
        Long roomId = createRoom();
        append(roomId, ClocktowerVisibility.PUBLIC, List.of(), "public-1");
        append(roomId, ClocktowerVisibility.PUBLIC, List.of(), "public-2");
        append(roomId, ClocktowerVisibility.STORYTELLER, List.of(), "hidden");
        ViewerContext viewer = ViewerContext.player(10L);

        List<ClocktowerEventResponse> backfill = streamService.backfill(roomId, 1L, viewer);

        assertThat(backfill).allSatisfy(event -> assertThat(event.seqNo()).isGreaterThan(1L));
        assertThat(backfill).noneMatch(event -> event.visibility() == ClocktowerVisibility.STORYTELLER);
    }

    @Test
    void appendLocksRoomBeforeCalculatingNextSeq() {
        ClocktowerEventRepository repository = mock(ClocktowerEventRepository.class);
        ClocktowerRoomRepository roomRepository = mock(ClocktowerRoomRepository.class);
        ClocktowerEventStreamService stream = mock(ClocktowerEventStreamService.class);
        ClocktowerEventPo existing = event(1L, 1L);
        AtomicLong nextId = new AtomicLong(2L);
        when(roomRepository.findLockedByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room(1L)));
        when(repository.findTopByRoomIdAndDeletedFalseOrderByEventSeqDesc(1L))
                .thenReturn(Optional.of(existing));
        when(repository.save(org.mockito.ArgumentMatchers.any(ClocktowerEventPo.class))).thenAnswer(invocation -> {
            ClocktowerEventPo event = invocation.getArgument(0);
            event.setId(nextId.getAndIncrement());
            return event;
        });
        ClocktowerEventServiceImpl retryingService = new ClocktowerEventServiceImpl(repository, roomRepository,
                new ClocktowerEventProjector(), new ObjectMapper(), stream);

        ClocktowerEventResponse response = retryingService.append(new ClocktowerEventAppendRequest(1L,
                ClocktowerEventType.PUBLIC_MESSAGE_SENT, ClocktowerPhase.DAY, 1, 0, 1L,
                10L, null, ClocktowerVisibility.PUBLIC, List.of(), Map.of("content", "public")));

        assertThat(response.seqNo()).isEqualTo(2L);
        verify(roomRepository).findLockedByIdAndDeletedFalse(1L);
    }

    @Test
    void streamControllerResolvesViewerSeatFromPrincipalInsteadOfTrustingQuerySeatId() {
        ClocktowerRoomService roomService = context.roomService();
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", top.egon.mario.clocktower.common.enums.ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, null, null, null, false, true, 0), principal(1L, "mario"));
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        Long otherSeatId = room.seats().get(1).seatId();
        ClocktowerEventStreamController controller = new ClocktowerEventStreamController(streamService,
                context.roomRepository(), context.seatRepository());

        assertThatThrownBy(() -> controller.stream(room.roomId(), otherSeatId, null, principal(2L, "player-1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_FORBIDDEN");

        Flux<ServerSentEvent<ClocktowerEventResponse>> stream = controller.stream(room.roomId(), null, 0L,
                principal(2L, "player-1"));
        StepVerifier.create(stream.take(1))
                .assertNext(event -> assertThat(event.data()).isNotNull())
                .verifyComplete();
    }

    @Test
    void streamControllerUsesStorytellerVisibilityForRoomOwner() {
        ClocktowerRoomService roomService = context.roomService();
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", top.egon.mario.clocktower.common.enums.ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, null, null, null, false, true, 0), principal(1L, "mario"));
        eventService.append(new ClocktowerEventAppendRequest(room.roomId(), ClocktowerEventType.STORYTELLER_RULING,
                ClocktowerPhase.DAY, 1, 0, 1L, null, null, ClocktowerVisibility.STORYTELLER, List.of(),
                Map.of("content", "storyteller-only")));
        ClocktowerEventStreamController controller = new ClocktowerEventStreamController(streamService,
                context.roomRepository(), context.seatRepository());

        StepVerifier.create(controller.stream(room.roomId(), null, 0L, principal(1L, "mario")).take(2).collectList())
                .assertNext(events -> assertThat(events)
                        .anySatisfy(event -> assertThat(event.data().visibility()).isEqualTo(ClocktowerVisibility.STORYTELLER)))
                .verifyComplete();
    }

    private Long createRoom() {
        return context.roomService().create(new ClocktowerRoomCreateRequest(
                "周五暗流", top.egon.mario.clocktower.common.enums.ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, null, null, null, false, true, 0), principal(1L, "mario")).roomId();
    }

    private void append(Long roomId, ClocktowerVisibility visibility, List<Long> visibleSeatIds, String content) {
        eventService.append(new ClocktowerEventAppendRequest(roomId, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                ClocktowerPhase.DAY, 1, 0, 1L, 10L, null, visibility, visibleSeatIds,
                Map.of("content", content)));
    }

    private static ClocktowerEventPo event(Long id, Long seqNo) {
        ClocktowerEventPo event = new ClocktowerEventPo();
        event.setId(id);
        event.setRoomId(1L);
        event.setEventSeq(seqNo);
        event.setEventType(ClocktowerEventType.PUBLIC_MESSAGE_SENT);
        event.setPhase(ClocktowerPhase.DAY);
        event.setDayNo(1);
        event.setNightNo(0);
        event.setVisibility(ClocktowerVisibility.PUBLIC);
        event.setVisibleSeatIdsJson("[]");
        event.setPayloadJson("{}");
        return event;
    }

    private static ClocktowerRoomPo room(Long id) {
        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setId(id);
        return room;
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
