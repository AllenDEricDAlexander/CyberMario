package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGamePhaseSignal;

public interface ClocktowerGamePhaseSignalScheduler {

    void schedule(ClocktowerGamePhaseSignal signal);
}
