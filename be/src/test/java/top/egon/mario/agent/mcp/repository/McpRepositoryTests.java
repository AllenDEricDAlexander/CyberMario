package top.egon.mario.agent.mcp.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolCallLogPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.service.AgentRuntimeFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies MCP repositories persist server and tool policy records.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:mcp_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.pool-name=McpRepositoryTestHikariPool",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class McpRepositoryTests {

    @Autowired
    private McpServerConfigRepository serverRepository;
    @Autowired
    private McpToolConfigRepository toolRepository;
    @Autowired
    private McpToolCallLogRepository toolCallLogRepository;
    @MockitoBean
    private AgentRuntimeFactory agentRuntimeFactory;

    @Test
    void serverAndToolPolicyCanBeSavedAndRead() {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setServerCode("docs");
        server.setServerName("Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setEnabled(true);
        server.setStatus(McpServerStatus.CONNECTED);
        server = serverRepository.save(server);

        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setServerId(server.getId());
        tool.setToolName("search");
        tool.setToolKey("docs_search");
        tool.setDisplayName("docs_search");
        tool.setDescription("Search docs");
        tool.setInputSchemaJson("{\"type\":\"object\"}");
        tool.setEnabled(true);
        tool.setReadonly(true);
        tool.setRequireConfirm(false);
        tool.setRiskLevel(McpToolRiskLevel.LOW);
        tool.setLastDiscoveredAt(Instant.now());
        toolRepository.save(tool);

        assertThat(serverRepository.findByServerCodeAndDeletedFalse("docs")).isPresent();
        assertThat(toolRepository.findByServerIdAndToolNameAndDeletedFalse(server.getId(), "search"))
                .isPresent()
                .get()
                .extracting(McpToolConfigPo::getToolKey)
                .isEqualTo("docs_search");
    }

    @Test
    void toolCallLogsCanBeSavedAndReadByDescendingId() {
        McpToolCallLogPo first = new McpToolCallLogPo();
        first.setTraceId("trace-1");
        first.setThreadId("thread-1");
        first.setUserId(1001L);
        first.setServerCode("docs");
        first.setToolKey("docs_search");
        first.setToolName("search");
        first.setRequestArgsSummary("{\"query\":\"alpha\"}");
        first.setResponseSummary("alpha result");
        first.setStatus(McpToolCallStatus.SUCCESS);
        first.setCostMs(12L);
        first.setCreatedAt(Instant.parse("2026-06-14T01:00:00Z"));
        first = toolCallLogRepository.save(first);

        McpToolCallLogPo second = new McpToolCallLogPo();
        second.setTraceId("trace-2");
        second.setThreadId("thread-2");
        second.setUserId(1002L);
        second.setServerCode("docs");
        second.setToolKey("docs_search");
        second.setToolName("search");
        second.setRequestArgsSummary("{\"query\":\"beta\"}");
        second.setResponseSummary("beta failure");
        second.setStatus(McpToolCallStatus.FAILED);
        second.setErrorMsg("timeout");
        second.setCostMs(34L);
        second.setCreatedAt(Instant.parse("2026-06-14T02:00:00Z"));
        second = toolCallLogRepository.save(second);

        var logs = toolCallLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10)).getContent();

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(McpToolCallLogPo::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(logs.get(0).getStatus()).isEqualTo(McpToolCallStatus.FAILED);
        assertThat(logs.get(0).getRequestArgsSummary()).isEqualTo("{\"query\":\"beta\"}");
        assertThat(logs.get(0).getResponseSummary()).isEqualTo("beta failure");
        assertThat(logs.get(1).getStatus()).isEqualTo(McpToolCallStatus.SUCCESS);
        assertThat(logs.get(1).getRequestArgsSummary()).isEqualTo("{\"query\":\"alpha\"}");
        assertThat(logs.get(1).getResponseSummary()).isEqualTo("alpha result");
    }

    @Test
    void serverAndToolCanBeSoftDeletedRecreatedAndSoftDeletedAgain() {
        McpServerConfigPo firstServer = newServer("repeat-docs");
        firstServer = serverRepository.save(firstServer);
        firstServer.setDeleted(true);
        serverRepository.save(firstServer);

        McpServerConfigPo secondServer = newServer("repeat-docs");
        secondServer = serverRepository.save(secondServer);

        McpToolConfigPo firstTool = newTool(secondServer.getId(), "repeat_docs_search");
        firstTool = toolRepository.save(firstTool);
        firstTool.setDeleted(true);
        toolRepository.save(firstTool);

        McpToolConfigPo secondTool = newTool(secondServer.getId(), "repeat_docs_search");
        secondTool = toolRepository.save(secondTool);
        secondTool.setDeleted(true);
        toolRepository.save(secondTool);

        secondServer.setDeleted(true);
        serverRepository.save(secondServer);

        assertThat(serverRepository.findByServerCodeAndDeletedFalse("repeat-docs")).isEmpty();
        assertThat(toolRepository.findByServerIdAndToolNameAndDeletedFalse(secondServer.getId(), "search")).isEmpty();
    }

    private McpServerConfigPo newServer(String serverCode) {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setServerCode(serverCode);
        server.setServerName("Repeat Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setStatus(McpServerStatus.DISABLED);
        return server;
    }

    private McpToolConfigPo newTool(Long serverId, String toolKey) {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setServerId(serverId);
        tool.setToolName("search");
        tool.setToolKey(toolKey);
        tool.setDisplayName(toolKey);
        tool.setDescription("Search docs");
        tool.setInputSchemaJson("{\"type\":\"object\"}");
        tool.setRiskLevel(McpToolRiskLevel.LOW);
        tool.setLastDiscoveredAt(Instant.now());
        return tool;
    }
}
