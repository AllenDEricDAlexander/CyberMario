package top.egon.mario.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.dto.request.AgentPresetRequest;
import top.egon.mario.agent.dto.request.AgentPresetStatusRequest;
import top.egon.mario.agent.dto.response.AgentPresetResponse;
import top.egon.mario.agent.mcp.runtime.McpAgentToolProvider;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.po.AgentChatPresetPo;
import top.egon.mario.agent.repository.AgentChatPresetRepository;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.service.model.AgentModelConfig;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentPresetConfig;
import top.egon.mario.agent.service.model.AgentRuntimeDefaults;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies agent debug preset management and runtime config resolution.
 */
class AgentPresetServiceTests {

    @Test
    void createPersistsPresetForCurrentUser() {
        AgentChatPresetRepository repository = mock(AgentChatPresetRepository.class);
        given(repository.save(any(AgentChatPresetPo.class))).willAnswer(invocation -> {
            AgentChatPresetPo po = invocation.getArgument(0);
            po.setId(9L);
            po.setCreatedBy(8L);
            po.setUpdatedBy(8L);
            po.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
            po.setUpdatedAt(Instant.parse("2026-06-14T01:00:00Z"));
            return po;
        });
        AgentPresetServiceImpl service = newService(repository);
        AgentPresetRequest request = new AgentPresetRequest(
                "Research preset",
                "for research",
                new AgentPresetConfig(null,
                        new ModelOptions(new BigDecimal("0.4"), 2048, new BigDecimal("0.8"), null,
                                true, null, null, true, Map.of()),
                        "You are a research assistant.",
                        new AgentToolConfig(Set.of("searchWikipedia")),
                        new AgentOptions(true, 2, 60)),
                true
        );

        AgentPresetResponse response = service.create(request, principal(8L));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.name()).isEqualTo("Research preset");
        ArgumentCaptor<AgentChatPresetPo> captor = ArgumentCaptor.forClass(AgentChatPresetPo.class);
        verify(repository).save(captor.capture());
        AgentChatPresetPo saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Research preset");
        assertThat(saved.getDescription()).isEqualTo("for research");
        assertThat(saved.getCreatedBy()).isEqualTo(8L);
        assertThat(saved.getUpdatedBy()).isEqualTo(8L);
        assertThat(saved.getSystemPrompt()).isEqualTo("You are a research assistant.");
        assertThat(saved.getModelConfigJson()).contains("qwen3.6-max-preview");
        assertThat(saved.getModelOptionsJson()).contains("\"temperature\":0.4");
        assertThat(saved.getToolConfigJson()).contains("searchWikipedia");
        assertThat(saved.getAgentOptionsJson()).contains("\"maxParallelTools\":2");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void updateRejectsNonCreator() {
        AgentChatPresetRepository repository = mock(AgentChatPresetRepository.class);
        AgentChatPresetPo preset = preset(9L, 8L);
        given(repository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(preset));
        AgentPresetServiceImpl service = newService(repository);
        AgentPresetRequest request = new AgentPresetRequest("Mine", null, null, true);

        assertThatThrownBy(() -> service.update(9L, request, principal(11L)))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("preset can only be modified by creator");
    }

    @Test
    void statusRejectsNonCreator() {
        AgentChatPresetRepository repository = mock(AgentChatPresetRepository.class);
        AgentChatPresetPo preset = preset(9L, 8L);
        given(repository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(preset));
        AgentPresetServiceImpl service = newService(repository);

        assertThatThrownBy(() -> service.updateStatus(9L, new AgentPresetStatusRequest(false), principal(11L)))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("preset can only be modified by creator");
    }

    @Test
    void resolveRuntimeSpecMergesPresetAndOverrides() {
        AgentChatPresetRepository repository = mock(AgentChatPresetRepository.class);
        AgentChatPresetPo preset = preset(9L, 8L);
        preset.setModelOptionsJson("""
                {"temperature":0.4,"topP":0.8,"enableThinking":true,"multiModel":true,"providerOptions":{}}
                """);
        preset.setSystemPrompt("Preset prompt");
        preset.setToolConfigJson("""
                {"enabledToolNames":["searchWikipedia"]}
                """);
        preset.setAgentOptionsJson("""
                {"parallelToolExecution":true,"maxParallelTools":2,"toolExecutionTimeoutSeconds":60}
                """);
        given(repository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(preset));
        AgentPresetServiceImpl service = newService(repository);
        AgentDebugChatRequest request = new AgentDebugChatRequest(
                "hello",
                "thread-1",
                9L,
                new AgentPresetConfig(null,
                        new ModelOptions(new BigDecimal("0.2"), null, null, null, false, null, null, null, Map.of()),
                        null,
                        new AgentToolConfig(Set.of("searchWikipedia", "searchDuckDuckGoNews")),
                        null)
        );

        AgentRuntimeSpec spec = service.resolveRuntimeSpec(request);

        assertThat(spec.presetId()).isEqualTo(9L);
        assertThat(spec.modelConfig().provider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(spec.modelOptions().temperature()).isEqualByComparingTo("0.2");
        assertThat(spec.modelOptions().topP()).isEqualByComparingTo("0.8");
        assertThat(spec.modelOptions().enableThinking()).isFalse();
        assertThat(spec.systemPrompt()).isEqualTo("Preset prompt");
        assertThat(spec.toolConfig().enabledToolNames()).containsExactlyInAnyOrder(
                "searchWikipedia", "searchDuckDuckGoNews");
        assertThat(spec.agentOptions().parallelToolExecution()).isTrue();
        assertThat(spec.fingerprint()).isNotBlank();
    }

    @Test
    void resolveRuntimeSpecRejectsModelOverride() {
        AgentPresetServiceImpl service = newService(mock(AgentChatPresetRepository.class));
        AgentDebugChatRequest request = new AgentDebugChatRequest(
                "hello",
                null,
                null,
                new AgentPresetConfig(
                        new AgentModelConfig(ModelProviderType.DASHSCOPE, "other-model"),
                        null,
                        null,
                        null,
                        null)
        );

        assertThatThrownBy(() -> service.resolveRuntimeSpec(request))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("model selection is not supported yet");
    }

    @Test
    void resolveRuntimeSpecRejectsUnknownTool() {
        AgentPresetServiceImpl service = newService(mock(AgentChatPresetRepository.class));
        AgentDebugChatRequest request = new AgentDebugChatRequest(
                "hello",
                null,
                null,
                new AgentPresetConfig(null, null, null, new AgentToolConfig(Set.of("missing_tool")), null)
        );

        assertThatThrownBy(() -> service.resolveRuntimeSpec(request))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("agent tool is not registered");
    }

    @Test
    void defaultRuntimeSpecIncludesCurrentMcpToolNames() {
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        ToolCallback mcpSearch = tool("mcp_search");
        given(mcpToolProvider.currentToolCallbacks()).willReturn(new ToolCallback[]{mcpSearch});
        AgentPresetServiceImpl service = newService(mock(AgentChatPresetRepository.class), mcpToolProvider);

        AgentRuntimeSpec spec = service.defaultRuntimeSpec();

        assertThat(spec.toolConfig().enabledToolNames()).contains("searchWikipedia", "searchDuckDuckGoNews",
                "mcp_search");
    }

    @Test
    void resolveRuntimeSpecAllowsCurrentMcpToolName() {
        McpAgentToolProvider mcpToolProvider = mock(McpAgentToolProvider.class);
        ToolCallback mcpSearch = tool("mcp_search");
        given(mcpToolProvider.currentToolCallbacks()).willReturn(new ToolCallback[]{mcpSearch});
        AgentPresetServiceImpl service = newService(mock(AgentChatPresetRepository.class), mcpToolProvider);
        AgentDebugChatRequest request = new AgentDebugChatRequest(
                "hello",
                null,
                null,
                new AgentPresetConfig(null, null, null, new AgentToolConfig(Set.of("mcp_search")), null)
        );

        AgentRuntimeSpec spec = service.resolveRuntimeSpec(request);

        assertThat(spec.toolConfig().enabledToolNames()).containsExactly("mcp_search");
    }

    @Test
    void serializeRuntimeSpecWritesResolvedRuntimeConfigJson() {
        AgentPresetServiceImpl service = newService(mock(AgentChatPresetRepository.class));
        AgentRuntimeSpec spec = service.defaultRuntimeSpec();

        String json = service.serializeRuntimeSpec(spec);

        assertThat(json).contains("\"modelConfig\"");
        assertThat(json).contains("\"model\":\"qwen3.6-max-preview\"");
        assertThat(json).contains("\"systemPrompt\"");
        assertThat(json).contains("\"agentOptions\"");
        assertThat(json).doesNotContain(spec.fingerprint());
    }

    private AgentPresetServiceImpl newService(AgentChatPresetRepository repository) {
        return newService(repository, null);
    }

    private AgentPresetServiceImpl newService(AgentChatPresetRepository repository,
                                              McpAgentToolProvider mcpToolProvider) {
        ToolCallback wikipedia = tool("searchWikipedia");
        ToolCallback duckduckgo = tool("searchDuckDuckGoNews");
        return new AgentPresetServiceImpl(repository, new ObjectMapper(),
                AgentRuntimeDefaults.defaultDefaults(), List.of(wikipedia, duckduckgo), mcpToolProvider);
    }

    private ToolCallback tool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        given(tool.getToolDefinition()).willReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(name)
                .description("stub")
                .inputSchema("{}")
                .build());
        return tool;
    }

    private AgentChatPresetPo preset(Long id, Long createdBy) {
        AgentChatPresetPo preset = new AgentChatPresetPo();
        preset.setId(id);
        preset.setCreatedBy(createdBy);
        preset.setUpdatedBy(createdBy);
        preset.setName("Preset");
        preset.setSystemPrompt("Preset prompt");
        preset.setEnabled(true);
        preset.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        preset.setUpdatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        return preset;
    }

    private RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CHAT_BASIC"), Set.of(), "v1");
    }

}
