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
public class EmpathRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "EMPATH";
    }

    @Override
    public boolean actsOnFirstNight() {
        return true;
    }

    @Override
    public boolean actsOnOtherNights() {
        return true;
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
        List<ClocktowerGameSeatPo> neighbors = nearestAliveNeighbors(context);
        long evilNeighborCount = neighbors.stream().filter(this::evil).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", roleCode());
        result.put("infoType", "EVIL_NEIGHBOR_COUNT");
        result.put("count", evilNeighborCount);
        result.put("neighborGameSeatIds", neighbors.stream().map(ClocktowerGameSeatPo::getId).toList());
        return privateInfo(context, result);
    }
}
