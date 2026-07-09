package top.egon.mario.clocktower.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService.ClocktowerAgentMemoryRefreshResult;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.strategy.AgentDecision;
import top.egon.mario.clocktower.agent.strategy.AgentDecisionContext;
import top.egon.mario.clocktower.agent.strategy.AgentDecisionSummary;
import top.egon.mario.clocktower.agent.strategy.AgentIntent;
import top.egon.mario.clocktower.agent.strategy.AgentIntentExecutor;
import top.egon.mario.clocktower.agent.strategy.ClocktowerAgentPolicy;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.service.ClocktowerAgentPrivateViewService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentRuntime {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerAgentProfileRepository agentProfileRepository;
    private final ClocktowerAgentMemoryService memoryService;
    private final ClocktowerAgentPrivateViewService privateViewService;
    private final ClocktowerAgentPolicy agentPolicy;
    private final AgentIntentExecutor intentExecutor;
    private final ObjectMapper objectMapper;

    public ClocktowerAgentRuntimeResult handle(ClocktowerAgentTaskPo task) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(
                task.getAgentInstanceId()).orElseThrow(
                        () -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        if (!ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus())) {
            return skipped("AGENT_INSTANCE_NOT_ACTIVE");
        }
        if (ClocktowerAgentAutoMode.PAUSED.equals(instance.getAutoMode())) {
            return skipped("AUTO_MODE_PAUSED");
        }
        if (ClocktowerAgentAutoMode.ST_APPROVAL.equals(instance.getAutoMode())) {
            return skipped("AUTO_MODE_REQUIRES_ST_APPROVAL");
        }

        ClocktowerAgentMemoryRefreshResult memoryRefresh = memoryService.refreshForRuntimeTask(task);
        AgentPrivateView view = privateViewService.build(task.getGameId(), task.getAgentInstanceId());
        ClocktowerAgentProfilePo profile = agentProfileRepository.findByIdAndDeletedFalse(instance.getProfileId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_PROFILE_INVALID"));
        AgentDecisionContext context = new AgentDecisionContext(view, profile, view.legalIntents(),
                task.getTriggerType(), metadata(task), Map.of());
        AgentDecision decision = agentPolicy.decide(context);
        boolean illegalIntentDowngraded = !isLegal(decision.intent(), view.legalIntents());
        if (illegalIntentDowngraded) {
            decision = new AgentDecision(new AgentIntent.Noop("policy selected illegal intent"),
                    "policy selected illegal intent",
                    Map.of("originalIntent", AgentDecisionSummary.intentType(decision.intent())));
        }
        List<ClocktowerGameActionResponse> responses = intentExecutor.execute(task, decision.intent());
        return done(AgentDecisionSummary.build(task, decision, view.legalIntents(), responses, memoryRefresh,
                illegalIntentDowngraded));
    }

    private boolean isLegal(AgentIntent intent, List<AgentLegalIntentView> legalIntents) {
        if (intent instanceof AgentIntent.Noop) {
            return true;
        }
        if (intent instanceof AgentIntent.PublicSpeech) {
            return hasIntent(legalIntents, "PUBLIC_SPEECH");
        }
        if (intent instanceof AgentIntent.GrabMic) {
            return hasIntent(legalIntents, "GRAB_MIC");
        }
        if (intent instanceof AgentIntent.FinishSpeech) {
            return hasIntent(legalIntents, "PUBLIC_SPEECH");
        }
        if (intent instanceof AgentIntent.Pass) {
            return hasIntent(legalIntents, "PASS");
        }
        if (intent instanceof AgentIntent.Nominate nominate) {
            return legalIntents.stream()
                    .filter(intentView -> "NOMINATE".equals(intentView.intentType()))
                    .map(intentView -> longList(payloadValue(intentView, "eligibleTargetGameSeatIds")))
                    .anyMatch(targets -> targets.contains(nominate.targetGameSeatId()));
        }
        if (intent instanceof AgentIntent.Vote vote) {
            return legalIntents.stream()
                    .filter(intentView -> "VOTE".equals(intentView.intentType()))
                    .anyMatch(intentView -> Objects.equals(intentView.nominationId(), vote.nominationId())
                            && Objects.equals(intentView.voteValue(), vote.vote()));
        }
        if (intent instanceof AgentIntent.NightChoice choice) {
            List<Long> selectedTargets = choice.targetGameSeatIds() == null ? List.of() : choice.targetGameSeatIds();
            return legalIntents.stream()
                    .filter(intentView -> "NIGHT_CHOICE".equals(intentView.intentType()))
                    .filter(intentView -> Objects.equals(intentView.taskId(), choice.taskId()))
                    .map(intentView -> longList(payloadValue(intentView, "legalTargetGameSeatIds")))
                    .anyMatch(legalTargets -> legalTargets.containsAll(selectedTargets)
                            || selectedTargets.isEmpty() && legalTargets.isEmpty());
        }
        return false;
    }

    private boolean hasIntent(List<AgentLegalIntentView> legalIntents, String intentType) {
        return legalIntents.stream().map(AgentLegalIntentView::intentType).anyMatch(intentType::equals);
    }

    private Object payloadValue(AgentLegalIntentView intentView, String key) {
        return intentView.payload() == null ? null : intentView.payload().get(key);
    }

    private List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(this::longValue)
                    .toList();
        }
        return List.of();
    }

    private ClocktowerAgentRuntimeResult done(Map<String, Object> result) {
        return new ClocktowerAgentRuntimeResult(ClocktowerAgentTaskStatus.DONE, result);
    }

    private ClocktowerAgentRuntimeResult skipped(String reason) {
        return new ClocktowerAgentRuntimeResult(ClocktowerAgentTaskStatus.CANCELLED,
                Map.of("skipped", true, "reason", reason));
    }

    private Map<String, Object> metadata(ClocktowerAgentTaskPo task) {
        if (task.getMetadataJson() == null || task.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(task.getMetadataJson(), MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_TASK_JSON_INVALID");
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    public record ClocktowerAgentRuntimeResult(String status, Map<String, Object> result) {
    }
}
