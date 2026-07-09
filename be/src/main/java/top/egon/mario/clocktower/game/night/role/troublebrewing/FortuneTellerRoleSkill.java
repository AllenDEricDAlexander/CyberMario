package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.List;
import java.util.Map;

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
        return NightResolution.done(Map.of("targetGameSeatIds", choice.targetGameSeatIds()));
    }
}
