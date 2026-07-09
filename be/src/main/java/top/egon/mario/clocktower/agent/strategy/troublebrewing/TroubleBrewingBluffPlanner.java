package top.egon.mario.clocktower.agent.strategy.troublebrewing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.strategy.AgentDecisionContext;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;
import top.egon.mario.clocktower.common.ClocktowerException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TroubleBrewingBluffPlanner {

    private static final String MEMORY_BLUFF_PLAN = "BLUFF_PLAN";

    private final ClocktowerAgentMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public TroubleBrewingBluffPlanner(ClocktowerAgentMemoryRepository memoryRepository, ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadOrCreatePlan(AgentDecisionContext context) {
        return context.view().memories().stream()
                .filter(memory -> MEMORY_BLUFF_PLAN.equals(memory.memoryType()))
                .map(AgentMemoryView::content)
                .findFirst()
                .orElseGet(() -> createPlan(context));
    }

    private Map<String, Object> createPlan(AgentDecisionContext context) {
        List<String> candidates = List.of("CHEF", "SOLDIER", "RAVENKEEPER", "MAYOR");
        String claimRoleCode = candidates.stream()
                .filter(candidate -> !candidate.equals(context.view().myRoleCode()))
                .findFirst()
                .orElse("CHEF");
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("claimRoleCode", claimRoleCode);
        plan.put("backupClaimRoleCode", candidates.stream()
                .filter(candidate -> !candidate.equals(claimRoleCode))
                .findFirst()
                .orElse("SOLDIER"));
        plan.put("fakeInfo", "CHEF".equals(claimRoleCode) ? Map.of("chefNumber", 1) : Map.of());
        plan.put("protectSeats", evilTeamSeatIds(context));
        plan.put("pushTargets", context.view().publicSeats().stream()
                .map(AgentPublicSeatView::gameSeatId)
                .filter(seatId -> !seatId.equals(context.view().myGameSeatId()))
                .filter(seatId -> !evilTeamSeatIds(context).contains(seatId))
                .limit(2)
                .toList());
        persistPlanIfRepositoryAvailable(context, plan);
        return plan;
    }

    private void persistPlanIfRepositoryAvailable(AgentDecisionContext context, Map<String, Object> plan) {
        if (memoryRepository == null || objectMapper == null) {
            return;
        }
        List<ClocktowerAgentMemoryPo> existing = memoryRepository
                .findByGameIdAndAgentInstanceIdAndMemoryTypeInAndDeletedFalseOrderByCreatedAtAscIdAsc(
                        context.view().gameId(), context.view().agentInstanceId(), List.of(MEMORY_BLUFF_PLAN));
        if (!existing.isEmpty()) {
            return;
        }
        ClocktowerAgentMemoryPo memory = new ClocktowerAgentMemoryPo();
        memory.setGameId(context.view().gameId());
        memory.setAgentInstanceId(context.view().agentInstanceId());
        memory.setGameSeatId(context.view().myGameSeatId());
        memory.setMemoryType(MEMORY_BLUFF_PLAN);
        memory.setVisibility("SELF");
        memory.setContentJson(writeJson(plan));
        memory.setConfidence(80);
        memory.setDayNo(context.view().dayNo());
        memory.setNightNo(context.view().nightNo());
        memory.setMetadataJson("{}");
        memoryRepository.saveAndFlush(memory);
    }

    private String writeJson(Map<String, Object> plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_JSON_INVALID");
        }
    }

    private List<Long> evilTeamSeatIds(AgentDecisionContext context) {
        Object evilTeam = context.view().roleSpecificContext().get("evilTeam");
        if (!(evilTeam instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> longValue(entry.get("gameSeatId")))
                .filter(item -> item != null)
                .toList();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }
}
