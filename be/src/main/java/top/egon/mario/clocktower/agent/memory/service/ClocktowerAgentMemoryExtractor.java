package top.egon.mario.clocktower.agent.memory.service;

import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;

public interface ClocktowerAgentMemoryExtractor {

    List<ClocktowerAgentMemoryPo> extract(ClocktowerGameEventPo event,
                                          Long agentInstanceId,
                                          ClocktowerGameSeatPo agentSeat);
}
