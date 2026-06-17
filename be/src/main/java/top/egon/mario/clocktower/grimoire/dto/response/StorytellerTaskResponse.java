package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;

public record StorytellerTaskResponse(
        Long taskId,
        String taskType,
        ClocktowerPhase phase,
        String roleCode,
        Long seatId,
        String status,
        String note
) {

    public static StorytellerTaskResponse from(ClocktowerStorytellerTaskPo task) {
        return new StorytellerTaskResponse(task.getId(), task.getTaskType(), task.getPhase(), task.getRoleCode(),
                task.getSeatId(), task.getStatus(), task.getNote());
    }
}
