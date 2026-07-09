package top.egon.mario.clocktower.game.night.role;

import java.util.List;
import java.util.Map;

public record NightChoice(List<Long> targetGameSeatIds, Map<String, Object> payload) {
}
