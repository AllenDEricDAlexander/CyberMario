package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PoisonerRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "POISONER";
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
        return firstLegalTarget(context);
    }

    @Override
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        Long targetGameSeatId = choice.targetGameSeatIds().isEmpty() ? null : choice.targetGameSeatIds().getFirst();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("marker", "POISONED");
        result.put("targetGameSeatId", targetGameSeatId);
        result.put("targetGameSeatIds", choice.targetGameSeatIds());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "MARKER_APPLIED");
        event.put("targetGameSeatId", targetGameSeatId);
        event.put("marker", "POISONED");
        event.put("sourceRole", roleCode());
        return new NightResolution(result, List.of(), List.of(event), List.of(), "DONE");
    }
}
