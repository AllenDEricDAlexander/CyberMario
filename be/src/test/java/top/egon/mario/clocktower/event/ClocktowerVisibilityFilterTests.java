package top.egon.mario.clocktower.event;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerVisibilityFilter;
import top.egon.mario.clocktower.event.service.ViewerContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerVisibilityFilterTests {

    @Test
    void playerSeesPublicEventsAndOwnPrivateEventsOnly() {
        ClocktowerEventResponse publicEvent = event(1L, ClocktowerVisibility.PUBLIC, List.of());
        ClocktowerEventResponse ownPrivate = event(2L, ClocktowerVisibility.PRIVATE, List.of(10L));
        ClocktowerEventResponse otherPrivate = event(3L, ClocktowerVisibility.PRIVATE, List.of(20L));
        ClocktowerEventResponse storyteller = event(4L, ClocktowerVisibility.STORYTELLER, List.of());

        ClocktowerVisibilityFilter filter = new ClocktowerVisibilityFilter();

        List<ClocktowerEventResponse> visible = filter.visibleEvents(
                List.of(publicEvent, ownPrivate, otherPrivate, storyteller),
                ViewerContext.player(10L));

        assertThat(visible).extracting(ClocktowerEventResponse::eventId).containsExactly(1L, 2L);
    }

    private static ClocktowerEventResponse event(Long eventId, ClocktowerVisibility visibility,
                                                 List<Long> visibleSeatIds) {
        return new ClocktowerEventResponse(eventId, 1L, eventId, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                ClocktowerPhase.DAY, 1, 0, null, null, null, visibility, visibleSeatIds, Map.of(), Instant.now());
    }
}
