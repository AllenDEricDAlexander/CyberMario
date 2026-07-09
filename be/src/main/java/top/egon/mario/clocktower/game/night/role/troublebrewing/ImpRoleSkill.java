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
import java.util.Objects;

@Component
public class ImpRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {

    @Override
    public String roleCode() {
        return "IMP";
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
        return allActiveTargets(context);
    }

    @Override
    public NightChoice autoChoose(NightTaskContext context) {
        return firstLegalTarget(context);
    }

    @Override
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        Long targetGameSeatId = choice.targetGameSeatIds().isEmpty() ? null : choice.targetGameSeatIds().getFirst();
        boolean protectedTarget = protectedByMonk(context, targetGameSeatId) || protectedBySoldier(context, targetGameSeatId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("killTargetGameSeatId", targetGameSeatId);
        result.put("killTargetGameSeatIds", choice.targetGameSeatIds());
        result.put("protected", protectedTarget);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("targetGameSeatId", targetGameSeatId);
        event.put("sourceRole", roleCode());
        if (protectedTarget) {
            event.put("eventType", "DEMON_KILL_PROTECTED");
            return new NightResolution(result, List.of(), List.of(event), List.of(), "DONE");
        }
        event.put("eventType", "PLAYER_DIED");
        event.put("cause", "DEMON_KILL");
        return new NightResolution(result, List.of(), List.of(), List.of(event), "DONE");
    }

    private boolean protectedByMonk(NightTaskContext context, Long targetGameSeatId) {
        Object markers = context.metadata().get("nightMarkers");
        if (!(markers instanceof List<?> markerList)) {
            return false;
        }
        return markerList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(marker -> "MONK_PROTECTED".equals(marker.get("marker"))
                        && Objects.equals(longValue(marker.get("targetGameSeatId")), targetGameSeatId));
    }

    private boolean protectedBySoldier(NightTaskContext context, Long targetGameSeatId) {
        return context.seats().stream()
                .filter(seat -> Objects.equals(seat.getId(), targetGameSeatId))
                .map(ClocktowerGameSeatPo::getRoleCode)
                .anyMatch("SOLDIER"::equals);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }
}
