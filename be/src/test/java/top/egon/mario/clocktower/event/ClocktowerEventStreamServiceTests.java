package top.egon.mario.clocktower.event;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventProjector;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.event.service.ViewerContext;
import top.egon.mario.clocktower.event.service.impl.ClocktowerEventServiceImpl;
import top.egon.mario.clocktower.event.service.impl.ClocktowerEventStreamServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerEventStreamServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerEventStreamService streamService = new ClocktowerEventStreamServiceImpl(
            context.eventRepository(), context.objectMapper());
    private final ClocktowerEventProjector projector = new ClocktowerEventProjector();
    private final ClocktowerEventServiceImpl eventService = new ClocktowerEventServiceImpl(
            context.eventRepository(), projector, context.objectMapper(), streamService);

    @Test
    void streamBackfillsEventsAfterLastSeqForViewer() {
        append(ClocktowerVisibility.PUBLIC, List.of(), "public-1");
        append(ClocktowerVisibility.PUBLIC, List.of(), "public-2");
        append(ClocktowerVisibility.STORYTELLER, List.of(), "hidden");
        ViewerContext viewer = ViewerContext.player(10L);

        List<ClocktowerEventResponse> backfill = streamService.backfill(1L, 1L, viewer);

        assertThat(backfill).allSatisfy(event -> assertThat(event.seqNo()).isGreaterThan(1L));
        assertThat(backfill).noneMatch(event -> event.visibility() == ClocktowerVisibility.STORYTELLER);
    }

    private void append(ClocktowerVisibility visibility, List<Long> visibleSeatIds, String content) {
        eventService.append(new ClocktowerEventAppendRequest(1L, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                ClocktowerPhase.DAY, 1, 0, 1L, 10L, null, visibility, visibleSeatIds,
                Map.of("content", content)));
    }
}
