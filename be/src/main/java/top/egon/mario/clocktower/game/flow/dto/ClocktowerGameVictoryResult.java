package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGameVictoryResult(
        boolean ended,
        String winner,
        String reason,
        int aliveCount,
        boolean demonAlive,
        Map<String, Object> counters
) {

    public static ClocktowerGameVictoryResult none(int aliveCount, boolean demonAlive,
                                                   Map<String, Object> counters) {
        return new ClocktowerGameVictoryResult(false, null, null, aliveCount, demonAlive, counters);
    }

    public static ClocktowerGameVictoryResult ended(String winner, String reason, int aliveCount,
                                                    boolean demonAlive, Map<String, Object> counters) {
        return new ClocktowerGameVictoryResult(true, winner, reason, aliveCount, demonAlive, counters);
    }
}
