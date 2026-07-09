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
public class MonkRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "MONK";
    }

    @Override
    public boolean actsOnFirstNight() {
        return false;
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
        return allActiveTargets(context).stream()
                .map(target -> target.gameSeatId().equals(context.actorSeat().getId())
                        ? new AvailableTargetSpec(target.gameSeatId(), target.displayName(), false, "SELF_NOT_ALLOWED")
                        : target)
                .toList();
    }

    @Override
    public NightChoice autoChoose(NightTaskContext context) {
        return new NightChoice(legalTargets(context).stream()
                .filter(AvailableTargetSpec::selectable)
                .map(AvailableTargetSpec::gameSeatId)
                .limit(1)
                .toList(), Map.of());
    }

    @Override
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        return NightResolution.done(Map.of("marker", "MONK_PROTECTED", "targetGameSeatIds", choice.targetGameSeatIds()));
    }
}
