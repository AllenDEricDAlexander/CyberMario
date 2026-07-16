package top.egon.mario.investment.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentPresetConfig;
import top.egon.mario.agent.service.model.AgentRuntimeDefaults;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fixed code preset for Investment analysis and automatic paper trading. */
@Service
public class InvestmentAgentPresetRegistry {

    public static final String PRESET_CODE = "INVESTMENT_ANALYST_V1";
    public static final Set<String> READ_TOOL_NAMES = Set.of(
            "get_investment_market_snapshot",
            "get_investment_candles",
            "get_investment_indicators",
            "get_investment_funding_rates",
            "get_investment_position_tiers",
            "get_investment_portfolio",
            "get_investment_risk_state",
            "get_investment_backtest"
    );

    private static final String SYSTEM_PROMPT = """
            You are INVESTMENT_ANALYST_V1 for cryptocurrency USDT perpetual futures in a paper-only system.
            Use only the read-only Investment tools supplied for this run. Never claim that you placed an order.
            The server, not you, binds actor, workspace, account, instruments, and dataAsOf.
            Return exactly one JSON object with no markdown and only these fields:
            instrumentId, action, confidence, horizon, thesis, risks, invalidation,
            requestedQuantity, requestedNotional, requestedLeverage, orderType, limitPrice,
            dataAsOf, expiresAt.
            action must be HOLD, OPEN_LONG, OPEN_SHORT, CLOSE, or REDUCE.
            For HOLD, all requested trade fields, orderType, and limitPrice must be null.
            For other actions, requested values must be positive; MARKET requires null limitPrice and LIMIT requires it.
            Use the exact server dataAsOf supplied in the user task. This is analysis for a simulated account only.
            """;

    private final ObjectMapper objectMapper;
    private final AgentRuntimeSpec runtimeSpec;
    private final String effectiveConfigJson;

    public InvestmentAgentPresetRegistry(AgentRuntimeDefaults defaults, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        ModelOptions defaultOptions = defaults.modelOptions();
        ModelOptions modelOptions = new ModelOptions(
                new BigDecimal("0.2"), defaultOptions.maxTokens(), defaultOptions.topP(), defaultOptions.topK(),
                defaultOptions.enableThinking(), defaultOptions.thinkingBudget(), false, false,
                defaultOptions.providerOptions());
        AgentOptions agentOptions = new AgentOptions(false, 1, 120);
        String fingerprint = ResearchHashSupport.sha256(PRESET_CODE + "\n" + SYSTEM_PROMPT + "\n"
                + defaults.modelConfig() + "\n" + modelOptions + "\n" + agentOptions);
        this.runtimeSpec = new AgentRuntimeSpec(null, defaults.modelConfig(), modelOptions, SYSTEM_PROMPT,
                new AgentToolConfig(Set.of()), agentOptions, fingerprint);
        this.effectiveConfigJson = json(new AgentPresetConfig(
                runtimeSpec.modelConfig(), runtimeSpec.modelOptions(), runtimeSpec.systemPrompt(),
                runtimeSpec.toolConfig(), runtimeSpec.agentOptions()));
    }

    public AgentRuntimeSpec runtimeSpec() {
        return runtimeSpec;
    }

    public String effectiveConfigJson() {
        return effectiveConfigJson;
    }

    public Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors() {
        Map<String, AgentRunAuditContext.ToolDescriptor> descriptors = new LinkedHashMap<>();
        READ_TOOL_NAMES.stream().sorted().forEach(name -> descriptors.put(
                name, new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.LOCAL, null)));
        return Map.copyOf(descriptors);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize the fixed Investment Agent preset", exception);
        }
    }
}
