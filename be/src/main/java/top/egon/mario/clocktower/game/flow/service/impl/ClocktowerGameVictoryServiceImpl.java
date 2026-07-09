package top.egon.mario.clocktower.game.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameVictoryResult;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameVictoryService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerGameVictoryServiceImpl implements ClocktowerGameVictoryService {

    private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_TYPE_DEMON = "DEMON";
    private static final String LIFE_ALIVE = "ALIVE";

    private final ClocktowerGameSeatRepository gameSeatRepository;

    @Override
    public ClocktowerGameVictoryResult evaluate(ClocktowerGamePo game) {
        List<ClocktowerGameSeatPo> activeSeats = gameSeatRepository
                .findByGameIdAndDeletedFalseOrderBySeatNoAsc(game.getId())
                .stream()
                .filter(seat -> SEAT_STATUS_ACTIVE.equals(seat.getStatus()))
                .toList();
        int aliveCount = (int) activeSeats.stream()
                .filter(seat -> LIFE_ALIVE.equals(seat.getLifeStatus()))
                .count();
        long demonCount = activeSeats.stream()
                .filter(seat -> ROLE_TYPE_DEMON.equals(seat.getRoleType()))
                .count();
        long aliveDemonCount = activeSeats.stream()
                .filter(seat -> ROLE_TYPE_DEMON.equals(seat.getRoleType()))
                .filter(seat -> LIFE_ALIVE.equals(seat.getLifeStatus()))
                .count();
        boolean demonAlive = aliveDemonCount > 0;
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("aliveCount", aliveCount);
        counters.put("demonCount", demonCount);
        counters.put("aliveDemonCount", aliveDemonCount);
        counters.put("demonAlive", demonAlive);
        if (demonCount > 0 && aliveDemonCount == 0) {
            return ClocktowerGameVictoryResult.ended("GOOD", "ALL_DEMONS_DEAD", aliveCount, false, counters);
        }
        if (aliveCount <= 2 && demonAlive) {
            return ClocktowerGameVictoryResult.ended("EVIL", "TWO_ALIVE_AND_DEMON_ALIVE", aliveCount, true, counters);
        }
        return ClocktowerGameVictoryResult.none(aliveCount, demonAlive, counters);
    }
}
