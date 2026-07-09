package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameVictoryResult;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

public interface ClocktowerGameVictoryService {

    ClocktowerGameVictoryResult evaluate(ClocktowerGamePo game);
}
