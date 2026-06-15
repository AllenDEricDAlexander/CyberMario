package top.egon.mario.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.dto.request.AgentPresetRequest;
import top.egon.mario.agent.dto.request.AgentPresetStatusRequest;
import top.egon.mario.agent.dto.response.AgentPresetResponse;
import top.egon.mario.agent.mcp.runtime.McpAgentToolProvider;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.po.AgentChatPresetPo;
import top.egon.mario.agent.repository.AgentChatPresetRepository;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.model.AgentModelConfig;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentPresetConfig;
import top.egon.mario.agent.service.model.AgentRuntimeDefaults;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of agent debug preset management.
 */
@Service
@Validated
public class AgentPresetServiceImpl implements AgentPresetService {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final AgentChatPresetRepository presetRepository;
    private final ObjectMapper objectMapper;
    private final AgentRuntimeDefaults defaults;
    private final List<ToolCallback> toolCallbacks;
    private final McpAgentToolProvider mcpAgentToolProvider;

    public AgentPresetServiceImpl(AgentChatPresetRepository presetRepository, ObjectMapper objectMapper,
                                  AgentRuntimeDefaults defaults, List<ToolCallback> toolCallbacks) {
        this(presetRepository, objectMapper, defaults, toolCallbacks, (McpAgentToolProvider) null);
    }

    @Autowired
    public AgentPresetServiceImpl(AgentChatPresetRepository presetRepository, ObjectMapper objectMapper,
                                  AgentRuntimeDefaults defaults, List<ToolCallback> toolCallbacks,
                                  ObjectProvider<McpAgentToolProvider> mcpAgentToolProvider) {
        this(presetRepository, objectMapper, defaults, toolCallbacks,
                mcpAgentToolProvider == null ? null : mcpAgentToolProvider.getIfAvailable());
    }

    AgentPresetServiceImpl(AgentChatPresetRepository presetRepository, ObjectMapper objectMapper,
                           AgentRuntimeDefaults defaults, List<ToolCallback> toolCallbacks,
                           McpAgentToolProvider mcpAgentToolProvider) {
        this.presetRepository = presetRepository;
        this.objectMapper = objectMapper;
        this.defaults = defaults;
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
        this.mcpAgentToolProvider = mcpAgentToolProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentPresetResponse> page(Pageable pageable) {
        return presetRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentPresetResponse detail(Long id) {
        return toResponse(getPreset(id));
    }

    @Override
    @Transactional
    public AgentPresetResponse create(AgentPresetRequest request, RbacPrincipal principal) {
        Long actorId = requireActor(principal);
        AgentPresetConfig config = merge(defaultConfig(), request.config());
        validateModelConfig(config.modelConfig());
        validateToolConfig(config.toolConfig());
        AgentChatPresetPo preset = new AgentChatPresetPo();
        preset.setName(request.name().trim());
        preset.setDescription(trimToNull(request.description()));
        preset.setEnabled(request.enabled() == null || request.enabled());
        preset.setCreatedBy(actorId);
        preset.setUpdatedBy(actorId);
        writeConfig(preset, config);
        return toResponse(presetRepository.save(preset));
    }

    @Override
    @Transactional
    public AgentPresetResponse update(Long id, AgentPresetRequest request, RbacPrincipal principal) {
        AgentChatPresetPo preset = getPreset(id);
        requireCreator(preset, principal);
        AgentPresetConfig config = merge(defaultConfig(), request.config());
        validateModelConfig(config.modelConfig());
        validateToolConfig(config.toolConfig());
        preset.setName(request.name().trim());
        preset.setDescription(trimToNull(request.description()));
        if (request.enabled() != null) {
            preset.setEnabled(request.enabled());
        }
        preset.setUpdatedBy(requireActor(principal));
        writeConfig(preset, config);
        return toResponse(presetRepository.save(preset));
    }

    @Override
    @Transactional
    public AgentPresetResponse updateStatus(Long id, AgentPresetStatusRequest request, RbacPrincipal principal) {
        AgentChatPresetPo preset = getPreset(id);
        requireCreator(preset, principal);
        preset.setEnabled(request.enabled());
        preset.setUpdatedBy(requireActor(principal));
        return toResponse(presetRepository.save(preset));
    }

    @Override
    @Transactional
    public void delete(Long id, RbacPrincipal principal) {
        AgentChatPresetPo preset = getPreset(id);
        requireCreator(preset, principal);
        preset.setDeleted(true);
        preset.setUpdatedBy(requireActor(principal));
        presetRepository.save(preset);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRuntimeSpec resolveRuntimeSpec(AgentDebugChatRequest request) {
        AgentPresetConfig base = defaultConfig();
        Long presetId = null;
        if (request != null && request.presetId() != null) {
            AgentChatPresetPo preset = getPreset(request.presetId());
            if (!preset.isEnabled()) {
                throw new AgentException("AGENT_PRESET_DISABLED", "agent preset is disabled");
            }
            presetId = preset.getId();
            base = merge(base, readConfig(preset));
        }
        AgentPresetConfig resolved = merge(base, request == null ? null : request.overrides());
        validateModelConfig(resolved.modelConfig());
        validateToolConfig(resolved.toolConfig());
        return toRuntimeSpec(presetId, resolved);
    }

    @Override
    public AgentRuntimeSpec defaultRuntimeSpec() {
        AgentPresetConfig config = defaultConfig();
        return toRuntimeSpec(null, config);
    }

    @Override
    public String serializeRuntimeSpec(AgentRuntimeSpec spec) {
        if (spec == null) {
            return null;
        }
        return writeValue(new AgentPresetConfig(spec.modelConfig(), spec.modelOptions(), spec.systemPrompt(),
                spec.toolConfig(), spec.agentOptions()));
    }

    private AgentPresetConfig defaultConfig() {
        return new AgentPresetConfig(defaults.modelConfig(), defaults.modelOptions(), defaults.systemPrompt(),
                new AgentToolConfig(defaultToolNames()), defaults.agentOptions());
    }

    private AgentPresetConfig merge(AgentPresetConfig base, AgentPresetConfig override) {
        if (override == null) {
            return base;
        }
        return new AgentPresetConfig(
                override.modelConfig() == null ? base.modelConfig() : override.modelConfig(),
                mergeModelOptions(base.modelOptions(), override.modelOptions()),
                StringUtils.hasText(override.systemPrompt()) ? override.systemPrompt().trim() : base.systemPrompt(),
                override.toolConfig() == null ? base.toolConfig() : override.toolConfig(),
                mergeAgentOptions(base.agentOptions(), override.agentOptions())
        );
    }

    private ModelOptions mergeModelOptions(ModelOptions base, ModelOptions override) {
        if (override == null) {
            return base;
        }
        return new ModelOptions(
                override.temperature() == null ? base.temperature() : override.temperature(),
                override.maxTokens() == null ? base.maxTokens() : override.maxTokens(),
                override.topP() == null ? base.topP() : override.topP(),
                override.topK() == null ? base.topK() : override.topK(),
                override.enableThinking() == null ? base.enableThinking() : override.enableThinking(),
                override.thinkingBudget() == null ? base.thinkingBudget() : override.thinkingBudget(),
                override.enableSearch() == null ? base.enableSearch() : override.enableSearch(),
                override.multiModel() == null ? base.multiModel() : override.multiModel(),
                override.providerOptions() == null || override.providerOptions().isEmpty()
                        ? base.providerOptions()
                        : override.providerOptions()
        );
    }

    private AgentOptions mergeAgentOptions(AgentOptions base, AgentOptions override) {
        if (override == null) {
            return base;
        }
        return new AgentOptions(
                override.parallelToolExecution() == null ? base.parallelToolExecution() : override.parallelToolExecution(),
                override.maxParallelTools() == null ? base.maxParallelTools() : override.maxParallelTools(),
                override.toolExecutionTimeoutSeconds() == null ? base.toolExecutionTimeoutSeconds() : override.toolExecutionTimeoutSeconds()
        );
    }

    private AgentRuntimeSpec toRuntimeSpec(Long presetId, AgentPresetConfig config) {
        String fingerprint = digest(writeValue(config));
        return new AgentRuntimeSpec(presetId, config.modelConfig(), config.modelOptions(), config.systemPrompt(),
                config.toolConfig(), config.agentOptions(), fingerprint);
    }

    private AgentPresetResponse toResponse(AgentChatPresetPo preset) {
        return new AgentPresetResponse(preset.getId(), preset.getName(), preset.getDescription(), readConfig(preset),
                preset.isEnabled(), preset.getCreatedBy(), preset.getUpdatedBy(), preset.getCreatedAt(), preset.getUpdatedAt());
    }

    private AgentPresetConfig readConfig(AgentChatPresetPo preset) {
        AgentPresetConfig base = defaultConfig();
        AgentModelConfig modelConfig = readValue(preset.getModelConfigJson(), AgentModelConfig.class, base.modelConfig());
        ModelOptions modelOptions = readValue(preset.getModelOptionsJson(), ModelOptions.class, base.modelOptions());
        AgentToolConfig toolConfig = readValue(preset.getToolConfigJson(), AgentToolConfig.class, base.toolConfig());
        AgentOptions agentOptions = readValue(preset.getAgentOptionsJson(), AgentOptions.class, base.agentOptions());
        String systemPrompt = StringUtils.hasText(preset.getSystemPrompt()) ? preset.getSystemPrompt() : base.systemPrompt();
        return new AgentPresetConfig(modelConfig, modelOptions, systemPrompt, toolConfig, agentOptions);
    }

    private void writeConfig(AgentChatPresetPo preset, AgentPresetConfig config) {
        preset.setModelConfigJson(writeValue(config.modelConfig()));
        preset.setModelOptionsJson(writeValue(config.modelOptions()));
        preset.setSystemPrompt(config.systemPrompt());
        preset.setToolConfigJson(writeValue(config.toolConfig()));
        preset.setAgentOptionsJson(writeValue(config.agentOptions()));
    }

    private <T> T readValue(String value, Class<T> type, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new AgentException("AGENT_PRESET_JSON_INVALID", "agent preset config is invalid");
        }
    }

    private String writeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AgentException("AGENT_PRESET_JSON_INVALID", "agent preset config cannot be serialized");
        }
    }

    private AgentChatPresetPo getPreset(Long id) {
        return presetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AgentException("AGENT_PRESET_NOT_FOUND", "agent preset not found"));
    }

    private void requireCreator(AgentChatPresetPo preset, RbacPrincipal principal) {
        Long userId = requireActor(principal);
        if (userId == null || !userId.equals(preset.getCreatedBy())) {
            throw new AgentException("AGENT_PRESET_FORBIDDEN", "preset can only be modified by creator");
        }
    }

    private Long requireActor(RbacPrincipal principal) {
        Long userId = principal == null ? null : principal.userId();
        if (userId == null) {
            throw new AgentException("AGENT_PRESET_FORBIDDEN", "preset creator is required");
        }
        return userId;
    }

    private void validateModelConfig(AgentModelConfig modelConfig) {
        AgentModelConfig defaultModelConfig = defaults.modelConfig();
        if (modelConfig == null || (defaultModelConfig.provider() == modelConfig.provider()
                && defaultModelConfig.model().equals(modelConfig.model()))) {
            return;
        }
        throw new AgentException("AGENT_MODEL_SELECTION_LOCKED", "model selection is not supported yet");
    }

    private void validateToolConfig(AgentToolConfig toolConfig) {
        if (toolConfig == null || toolConfig.enabledToolNames() == null) {
            return;
        }
        Set<String> allowedToolNames = registeredToolNames();
        for (String toolName : toolConfig.enabledToolNames()) {
            if (!allowedToolNames.contains(toolName)) {
                throw new AgentException("AGENT_TOOL_NOT_REGISTERED", "agent tool is not registered: " + toolName);
            }
        }
    }

    private Set<String> registeredToolNames() {
        Set<String> toolNames = toolCallbacks.stream()
                .map(toolCallback -> toolCallback.getToolDefinition().name())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        currentMcpToolCallbacks().stream()
                .map(toolCallback -> toolCallback.getToolDefinition().name())
                .forEach(toolNames::add);
        return toolNames;
    }

    private List<ToolCallback> currentMcpToolCallbacks() {
        if (mcpAgentToolProvider == null) {
            return List.of();
        }
        ToolCallback[] callbacks = mcpAgentToolProvider.currentToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return List.of();
        }
        return Arrays.asList(callbacks);
    }

    private Set<String> defaultToolNames() {
        return registeredToolNames();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String digest(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("agent runtime digest algorithm is unavailable", e);
        }
    }

}
