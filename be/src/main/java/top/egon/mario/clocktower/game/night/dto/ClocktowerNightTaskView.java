package top.egon.mario.clocktower.game.night.dto;

import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;

import java.util.Map;

public record ClocktowerNightTaskView(
        Long taskId,
        Long gameId,
        int nightNo,
        Long actorGameSeatId,
        String roleCode,
        String taskType,
        String status,
        boolean mandatory,
        int sortOrder,
        Map<String, Object> choice,
        Map<String, Object> result,
        Map<String, Object> metadata
) {

    public static ClocktowerNightTaskView from(ClocktowerGameNightTaskPo task,
                                               Map<String, Object> choice,
                                               Map<String, Object> result,
                                               Map<String, Object> metadata) {
        return new ClocktowerNightTaskView(task.getId(), task.getGameId(), task.getNightNo(),
                task.getActorGameSeatId(), task.getRoleCode(), task.getTaskType(), task.getStatus(),
                task.isMandatory(), task.getSortOrder(), choice, result, metadata);
    }
}
