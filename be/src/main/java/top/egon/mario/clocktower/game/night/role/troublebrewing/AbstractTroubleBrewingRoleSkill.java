package top.egon.mario.clocktower.game.night.role.troublebrewing;

import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class AbstractTroubleBrewingRoleSkill {

    protected static final String TASK_CHOOSE_TARGET = "CHOOSE_TARGET";
    protected static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";
    protected static final String ALIGNMENT_EVIL = "EVIL";
    protected static final String LIFE_ALIVE = "ALIVE";

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

    protected NightResolution privateInfo(NightTaskContext context, Map<String, Object> result) {
        Map<String, Object> event = new LinkedHashMap<>(result);
        event.put("eventType", "PRIVATE_INFO_RECEIVED");
        event.put("recipientGameSeatId", context.actorSeat().getId());
        event.put("targetGameSeatId", context.actorSeat().getId());
        return new NightResolution(result, List.of(event), List.of(), List.of(), "DONE");
    }

    protected List<ClocktowerGameSeatPo> activeSeats(NightTaskContext context) {
        return context.seats().stream()
                .filter(seat -> "ACTIVE".equals(seat.getStatus()))
                .sorted(Comparator.comparingInt(ClocktowerGameSeatPo::getSeatNo))
                .toList();
    }

    protected List<ClocktowerGameSeatPo> aliveSeats(NightTaskContext context) {
        return activeSeats(context).stream()
                .filter(seat -> LIFE_ALIVE.equals(seat.getLifeStatus()))
                .toList();
    }

    protected boolean evil(ClocktowerGameSeatPo seat) {
        return ALIGNMENT_EVIL.equals(seat.getAlignment());
    }

    protected Map<String, Object> seatSummary(ClocktowerGameSeatPo seat, boolean revealRole) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("gameSeatId", seat.getId());
        summary.put("seatNo", seat.getSeatNo());
        summary.put("displayName", seat.getDisplayName());
        if (revealRole) {
            summary.put("roleCode", seat.getRoleCode());
            summary.put("roleType", seat.getRoleType());
            summary.put("alignment", seat.getAlignment());
            summary.put("lifeStatus", seat.getLifeStatus());
            summary.put("publicLifeStatus", seat.getPublicLifeStatus());
        }
        return summary;
    }

    protected List<ClocktowerGameSeatPo> nearestAliveNeighbors(NightTaskContext context) {
        List<ClocktowerGameSeatPo> seats = activeSeats(context);
        if (seats.size() <= 1) {
            return List.of();
        }
        int actorIndex = -1;
        for (int index = 0; index < seats.size(); index++) {
            if (Objects.equals(seats.get(index).getId(), context.actorSeat().getId())) {
                actorIndex = index;
                break;
            }
        }
        if (actorIndex < 0) {
            return List.of();
        }
        List<ClocktowerGameSeatPo> neighbors = new ArrayList<>();
        addNearestAlive(seats, actorIndex, -1, neighbors);
        addNearestAlive(seats, actorIndex, 1, neighbors);
        return neighbors;
    }

    private void addNearestAlive(List<ClocktowerGameSeatPo> seats, int actorIndex, int direction,
                                 List<ClocktowerGameSeatPo> neighbors) {
        for (int offset = 1; offset < seats.size(); offset++) {
            ClocktowerGameSeatPo candidate = seats.get(Math.floorMod(actorIndex + direction * offset, seats.size()));
            if (Objects.equals(candidate.getId(), seats.get(actorIndex).getId())) {
                continue;
            }
            if (LIFE_ALIVE.equals(candidate.getLifeStatus())) {
                neighbors.add(candidate);
                return;
            }
        }
    }
}
