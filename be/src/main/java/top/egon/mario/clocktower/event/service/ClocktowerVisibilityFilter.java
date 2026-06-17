package top.egon.mario.clocktower.event.service;

import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

import java.util.List;

public class ClocktowerVisibilityFilter {

    public List<ClocktowerEventResponse> visibleEvents(List<ClocktowerEventResponse> events, ViewerContext viewer) {
        return events.stream()
                .filter(event -> isVisible(event, viewer))
                .toList();
    }

    public boolean isVisible(ClocktowerEventResponse event, ViewerContext viewer) {
        if (event.visibility() == ClocktowerVisibility.PUBLIC) {
            return true;
        }
        if (viewer.mode() == ClocktowerViewerMode.ADMIN) {
            return event.visibility() == ClocktowerVisibility.AUDIT || event.visibility() == ClocktowerVisibility.STORYTELLER
                    || event.visibility() == ClocktowerVisibility.PRIVATE;
        }
        if (viewer.mode() == ClocktowerViewerMode.STORYTELLER) {
            return event.visibility() == ClocktowerVisibility.PRIVATE
                    || event.visibility() == ClocktowerVisibility.STORYTELLER;
        }
        return event.visibility() == ClocktowerVisibility.PRIVATE
                && event.visibleSeatIds().contains(viewer.seatId());
    }
}
