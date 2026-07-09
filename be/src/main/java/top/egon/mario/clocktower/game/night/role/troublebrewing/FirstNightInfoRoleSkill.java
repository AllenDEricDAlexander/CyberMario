package top.egon.mario.clocktower.game.night.role.troublebrewing;

import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.List;
import java.util.Map;

public class FirstNightInfoRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    private final String roleCode;

    public FirstNightInfoRoleSkill(String roleCode) {
        this.roleCode = roleCode;
    }

    @Override
    public String roleCode() {
        return roleCode;
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
        return NightResolution.done(Map.of("roleCode", roleCode));
    }
}
