package top.egon.mario.clocktower.game.night.role;

import java.util.List;

public interface RoleSkill {

    String roleCode();

    boolean actsOnFirstNight();

    boolean actsOnOtherNights();

    List<NightTaskSpec> createTasks(NightTaskContext context);

    List<AvailableTargetSpec> legalTargets(NightTaskContext context);

    NightChoice autoChoose(NightTaskContext context);

    NightResolution resolve(NightTaskContext context, NightChoice choice);
}
