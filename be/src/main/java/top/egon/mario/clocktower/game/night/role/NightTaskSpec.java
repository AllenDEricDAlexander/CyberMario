package top.egon.mario.clocktower.game.night.role;

import java.util.Map;

public record NightTaskSpec(
        String taskType,
        boolean mandatory,
        Map<String, Object> metadata
) {
}
