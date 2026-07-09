package top.egon.mario.clocktower.game.night.role;

import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;
import java.util.Map;

public record NightTaskContext(
        ClocktowerGamePo game,
        ClocktowerGameNightTaskPo task,
        ClocktowerGameSeatPo actorSeat,
        List<ClocktowerGameSeatPo> seats,
        List<ClocktowerGameNightTaskPo> currentNightTasks,
        Map<String, Object> metadata
) {
}
