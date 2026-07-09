package top.egon.mario.clocktower.game.night.role.troublebrewing;

import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;
import java.util.Map;

abstract class AbstractTroubleBrewingRoleSkill {

    protected static final String TASK_CHOOSE_TARGET = "CHOOSE_TARGET";
    protected static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";

    protected List<NightTaskSpec> targetTask() {
        return List.of(new NightTaskSpec(TASK_CHOOSE_TARGET, true, Map.of()));
    }

    protected List<NightTaskSpec> infoTask() {
        return List.of(new NightTaskSpec(TASK_RECEIVE_INFO, true, Map.of()));
    }

    protected List<AvailableTargetSpec> allActiveTargets(NightTaskContext context) {
        return context.seats().stream()
                .filter(seat -> "ACTIVE".equals(seat.getStatus()))
                .map(seat -> new AvailableTargetSpec(seat.getId(), seat.getDisplayName(), true, null))
                .toList();
    }

    protected NightChoice firstLegalTarget(NightTaskContext context) {
        return new NightChoice(allActiveTargets(context).stream()
                .filter(AvailableTargetSpec::selectable)
                .map(AvailableTargetSpec::gameSeatId)
                .limit(1)
                .toList(), Map.of());
    }

    protected ClocktowerGameSeatPo seat(Long id, NightTaskContext context) {
        return context.seats().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
