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
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FortuneTellerRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "FORTUNETELLER";
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
        return targetTask();
    }

    @Override
    public List<AvailableTargetSpec> legalTargets(NightTaskContext context) {
        return allActiveTargets(context);
    }

    @Override
    public NightChoice autoChoose(NightTaskContext context) {
        return new NightChoice(allActiveTargets(context).stream()
                .filter(AvailableTargetSpec::selectable)
                .map(AvailableTargetSpec::gameSeatId)
                .limit(2)
                .toList(), Map.of());
    }

    @Override
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        Set<Long> targetIds = choice.targetGameSeatIds().stream().collect(Collectors.toSet());
        boolean hasDemon = context.seats().stream()
                .filter(seat -> targetIds.contains(seat.getId()))
                .map(ClocktowerGameSeatPo::getRoleType)
                .anyMatch("DEMON"::equals);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", roleCode());
        result.put("infoType", "DEMON_CHECK");
        result.put("targetGameSeatIds", choice.targetGameSeatIds());
        result.put("answer", hasDemon ? "YES" : "NO");
        return privateInfo(context, result);
    }
}
