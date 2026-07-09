package top.egon.mario.clocktower.game.night.role.troublebrewing;

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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", roleCode);
        result.putAll(solveFirstNightInfo(context));
        return privateInfo(context, result);
    }

    private Map<String, Object> solveFirstNightInfo(NightTaskContext context) {
        return switch (roleCode) {
            case "WASHERWOMAN" -> candidateInfo(context, "TOWNSFOLK", "TOWNSFOLK_CANDIDATES");
            case "LIBRARIAN" -> candidateInfo(context, "OUTSIDER", "OUTSIDER_CANDIDATES");
            case "INVESTIGATOR" -> candidateInfo(context, "MINION", "MINION_CANDIDATES");
            default -> Map.of("infoType", "FIRST_NIGHT_INFO");
        };
    }

    private Map<String, Object> candidateInfo(NightTaskContext context, String roleType, String infoType) {
        ClocktowerGameSeatPo matchingSeat = activeSeats(context).stream()
                .filter(seat -> roleType.equals(seat.getRoleType()))
                .filter(seat -> !Objects.equals(seat.getId(), context.actorSeat().getId()))
                .findFirst()
                .orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("infoType", infoType);
        if (matchingSeat == null) {
            result.put("count", 0);
            result.put("candidateGameSeatIds", List.of());
            return result;
        }
        ClocktowerGameSeatPo decoySeat = activeSeats(context).stream()
                .filter(seat -> !Objects.equals(seat.getId(), context.actorSeat().getId()))
                .filter(seat -> !Objects.equals(seat.getId(), matchingSeat.getId()))
                .findFirst()
                .orElse(matchingSeat);
        result.put("count", 1);
        result.put("shownRoleCode", matchingSeat.getRoleCode());
        result.put("candidateGameSeatIds", List.of(matchingSeat.getId(), decoySeat.getId()));
        return result;
    }
}
