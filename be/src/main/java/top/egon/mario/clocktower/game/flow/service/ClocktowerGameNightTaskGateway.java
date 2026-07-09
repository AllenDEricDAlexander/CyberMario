package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameNightTaskSummary;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

public interface ClocktowerGameNightTaskGateway {

    ClocktowerGameNightTaskSummary summarize(ClocktowerGamePo game);

    void initializeNightTasks(ClocktowerGamePo game);
}
