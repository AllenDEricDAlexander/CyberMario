package top.egon.mario.clocktower.event.service;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

@Component
public class ClocktowerEventProjector {

    public void project(ClocktowerEventResponse event) {
        // Phase 1 projections are added by room, grimoire, and action tasks.
    }
}
