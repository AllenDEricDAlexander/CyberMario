package top.egon.mario.clocktower.game.flow.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGamePhaseSignal;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGamePhaseSignalScheduler;

@Service
public class NoopClocktowerGamePhaseSignalScheduler implements ClocktowerGamePhaseSignalScheduler {

    @Override
    public void schedule(ClocktowerGamePhaseSignal signal) {
    }
}
