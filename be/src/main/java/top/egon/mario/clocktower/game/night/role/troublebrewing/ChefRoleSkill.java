package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChefRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "CHEF";
    }

    @Override
    public boolean actsOnFirstNight() {
        return true;
    }

    @Override
    public boolean actsOnOtherNights() {
        return false;
    }

    @Override
    public List<NightTaskSpec> createTasks(NightTaskContext context) {
        return infoTask();
    }

    @Override
    public List<AvailableTargetSpec> legalTargets(NightTaskContext context) {
        return List.of();
    }

    @Override
    public NightChoice autoChoose(NightTaskContext context) {
        return new NightChoice(List.of(), Map.of());
    }

    @Override
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        List<ClocktowerGameSeatPo> seats = activeSeats(context);
        int evilAdjacentPairs = 0;
        for (int index = 0; index < seats.size(); index++) {
            ClocktowerGameSeatPo current = seats.get(index);
            ClocktowerGameSeatPo next = seats.get(Math.floorMod(index + 1, seats.size()));
            if (evil(current) && evil(next)) {
                evilAdjacentPairs++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", roleCode());
        result.put("infoType", "EVIL_ADJACENT_PAIRS");
        result.put("count", evilAdjacentPairs);
        return privateInfo(context, result);
    }
}
