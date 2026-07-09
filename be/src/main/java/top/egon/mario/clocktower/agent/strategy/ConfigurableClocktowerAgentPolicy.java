package top.egon.mario.clocktower.agent.strategy;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.decision.ClocktowerAgentDecisionPolicyType;
import top.egon.mario.clocktower.agent.decision.ClocktowerAgentDecisionStatus;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmPolicy;
import top.egon.mario.clocktower.agent.strategy.llm.ClocktowerAgentLlmPolicyException;
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;

import java.util.Locale;
import java.util.Map;

@Service
@Primary
public class ConfigurableClocktowerAgentPolicy implements ClocktowerAgentPolicy {

    private final ClocktowerAgentPolicyProperties properties;
    private final ClocktowerFeatureProperties featureProperties;
    private final HeuristicAgentPolicy heuristicPolicy;
    private final ClocktowerAgentLlmPolicy llmPolicy;

    public ConfigurableClocktowerAgentPolicy(ClocktowerAgentPolicyProperties properties,
                                             ClocktowerFeatureProperties featureProperties,
                                             HeuristicAgentPolicy heuristicPolicy,
                                             ClocktowerAgentLlmPolicy llmPolicy) {
        this.properties = properties;
        this.featureProperties = featureProperties;
        this.heuristicPolicy = heuristicPolicy;
        this.llmPolicy = llmPolicy;
    }

    @Override
    public AgentDecision decide(AgentDecisionContext context) {
        return decideWithMetadata(context).decision();
    }

    @Override
    public AgentPolicyResult decideWithMetadata(AgentDecisionContext context) {
        String mode = properties.policy().toUpperCase(Locale.ROOT);
        if (ClocktowerAgentDecisionPolicyType.HEURISTIC.equals(mode)
                || !featureProperties.llmAgent().enabled()
                || !properties.llm().enabled()) {
            return new AgentPolicyResult(heuristicPolicy.decide(context),
                    ClocktowerAgentDecisionPolicyType.HEURISTIC,
                    ClocktowerAgentDecisionStatus.ACCEPTED,
                    null, null, null, null,
                    Map.of("configuredPolicy", mode,
                            "llmAgentEnabled", featureProperties.llmAgent().enabled(),
                            "agentLlmEnabled", properties.llm().enabled()));
        }
        try {
            ClocktowerAgentLlmPolicy.ClocktowerAgentLlmPolicyResult llm = llmPolicy.decide(context);
            return new AgentPolicyResult(llm.decision(), ClocktowerAgentDecisionPolicyType.LLM,
                    ClocktowerAgentDecisionStatus.ACCEPTED, null, llm.provider(), llm.model(), llm.promptHash(),
                    Map.of("configuredPolicy", mode));
        } catch (ClocktowerAgentLlmPolicyException ex) {
            return fallback(context, ex, fallbackStatus(ex), mode);
        } catch (RuntimeException ex) {
            return fallback(context, ex, ClocktowerAgentDecisionStatus.LLM_ERROR_FALLBACK, mode);
        }
    }

    private AgentPolicyResult fallback(AgentDecisionContext context, RuntimeException ex, String status, String mode) {
        AgentDecision decision = heuristicPolicy.decide(context);
        String errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return new AgentPolicyResult(decision, ClocktowerAgentDecisionPolicyType.FALLBACK_HEURISTIC,
                status, errorMessage, null, null, null,
                Map.of("configuredPolicy", mode, "fallbackReason", errorMessage));
    }

    private String fallbackStatus(ClocktowerAgentLlmPolicyException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.contains("INTENT") || message.contains("TARGET")
                ? ClocktowerAgentDecisionStatus.ILLEGAL_INTENT_FALLBACK
                : ClocktowerAgentDecisionStatus.LLM_ERROR_FALLBACK;
    }
}
