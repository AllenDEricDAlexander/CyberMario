package top.egon.mario.clocktower.agent.memory.service;

import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;

public interface ClocktowerAgentMemoryService {

    ClocktowerAgentMemoryRefreshResult refresh(Long gameId, Long agentInstanceId);

    default ClocktowerAgentMemoryRefreshResult refreshForRuntimeTask(ClocktowerAgentTaskPo task) {
        return refresh(task.getGameId(), task.getAgentInstanceId());
    }

    record ClocktowerAgentMemoryRefreshResult(long lastSeenEventSeq, int insertedCount) {
    }
}
