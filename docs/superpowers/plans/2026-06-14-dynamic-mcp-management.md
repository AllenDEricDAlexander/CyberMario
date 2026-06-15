# Dynamic MCP Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build first-stage self-managed dynamic MCP Client administration for remote Streamable HTTP and SSE MCP
servers, with DB-backed server/tool policy, frontend controls, RBAC resources, and ReactAgent dynamic tool refresh.

**Architecture:** CyberMario remains an MCP Client only. The new `top.egon.mario.agent.mcp` package owns server
configuration, tool discovery, policy, runtime client lifecycle, tool-call logging, and RBAC resource seeding;
ReactAgent gets a refreshed tool snapshot for new chat requests when MCP configuration changes. `SUPER_ADMIN` keeps all
permissions through the existing admin bootstrap, while default registered users receive MCP server/tool management
permissions but not MCP audit log page/API permissions.

**Tech Stack:** Java 21, Spring Boot 3.5.15 WebFlux, Spring AI 1.1.2 MCP client/webflux, Model Context Protocol Java SDK
0.17.0, Spring Data JPA, Flyway, Reactor, Ant Design React, Vitest, JUnit 5, Mockito, AssertJ.

**Execution Status:** Implemented and verified on `main` without starting the application. Commit steps remain
intentionally unexecuted because no commit was requested. Final validation passed with focused backend MCP/RBAC/agent
tests, backend compile, frontend MCP/menu permission tests, frontend typecheck/build, dependency tree verification for
MCP SDK 0.17.0, and `git diff --check`. Remaining release gap: perform live smoke testing against real Streamable HTTP
and SSE MCP servers in an environment with network access.

---

## Scope Decisions

- Support only remote MCP servers in stage one: `STREAMABLE_HTTP` and `SSE`.
- Do not support local `STDIO` in stage one.
- Do not expose any MCP server endpoint from CyberMario.
- Do not build Nacos discovery in stage one.
- Do not implement interactive "confirm before tool call" in stage one. Tools with `requireConfirm=true` stay hidden
  from ReactAgent.
- Treat MCP config management as admin console functionality. Normal registered users get MCP server/tool management
  permissions through a new default role, but not MCP tool-call log page/API permissions.
- Keep tool-call logging independent from the existing RBAC audit log. Logs are for MCP tool executions and are visible
  only to roles granted MCP log permissions.

## File Structure

### Backend files to create

- `be/src/main/resources/db/migration/V14__create_agent_mcp_schema.sql`
    - Creates MCP server config, tool config, and tool-call log tables.
- `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpTransportType.java`
    - Supported remote transport values.
- `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpServerStatus.java`
    - Runtime/server connection status.
- `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolRuntimeStatus.java`
    - Derived tool availability status for UI.
- `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolRiskLevel.java`
    - Tool risk levels.
- `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolCallStatus.java`
    - Tool call log status.
- `be/src/main/java/top/egon/mario/agent/mcp/po/McpServerConfigPo.java`
    - DB entity for a configured MCP server.
- `be/src/main/java/top/egon/mario/agent/mcp/po/McpToolConfigPo.java`
    - DB entity for discovered MCP tools and policy.
- `be/src/main/java/top/egon/mario/agent/mcp/po/McpToolCallLogPo.java`
    - DB entity for MCP tool-call logs.
- `be/src/main/java/top/egon/mario/agent/mcp/repository/McpServerConfigRepository.java`
    - JPA repository for servers.
- `be/src/main/java/top/egon/mario/agent/mcp/repository/McpToolConfigRepository.java`
    - JPA repository for tools.
- `be/src/main/java/top/egon/mario/agent/mcp/repository/McpToolCallLogRepository.java`
    - JPA repository for logs.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/request/CreateMcpServerRequest.java`
    - Create server request.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/request/UpdateMcpServerRequest.java`
    - Update server request.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/request/UpdateMcpToolPolicyRequest.java`
    - Update tool policy request.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/response/McpServerResponse.java`
    - Server response with masked headers.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/response/McpToolResponse.java`
    - Tool response.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/response/McpToolCallLogResponse.java`
    - Tool-call log response.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/response/McpConnectionTestResponse.java`
    - Test connection result.
- `be/src/main/java/top/egon/mario/agent/mcp/dto/response/McpToolDiscoveryResponse.java`
    - Discovery result summary.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpServerConfigService.java`
    - Server management service.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolConfigService.java`
    - Tool policy/query service.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java`
    - Tool discovery service.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolCallLogService.java`
    - Tool-call logging/query service.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpClientFactory.java`
    - Creates initialized `McpSyncClient` instances for Streamable HTTP and SSE.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/DynamicMcpClientManager.java`
    - Owns runtime client lifecycle.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistry.java`
    - Builds currently visible MCP `ToolCallback` snapshots.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/LoggingMcpToolCallback.java`
    - Wraps Spring AI MCP callbacks to record calls.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentToolProvider.java`
    - Supplies current MCP tool callbacks to the agent factory.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java`
    - Publishes a refresh signal after MCP config changes.
- `be/src/main/java/top/egon/mario/agent/mcp/policy/McpToolPolicyService.java`
    - Decides whether a discovered tool is agent-visible.
- `be/src/main/java/top/egon/mario/agent/mcp/config/McpManagementConfiguration.java`
    - Binds module configuration and initializes the runtime manager.
- `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java`
    - Server admin API.
- `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java`
    - Tool admin API.
- `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolCallLogController.java`
    - Tool-call log admin API.
- `be/src/main/java/top/egon/mario/agent/mcp/service/resource/AgentMcpPermissionCatalog.java`
    - MCP RBAC menu/button/API/role seeds.
- `be/src/main/java/top/egon/mario/agent/mcp/service/resource/AgentMcpRbacResourceProvider.java`
    - Supplies MCP RBAC resources to the existing synchronizer.

### Backend files to modify

- `be/pom.xml`
    - Add `spring-ai-starter-mcp-client-webflux`.
- `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
    - Extract ReactAgent construction into a reusable factory bean method or provider.
- `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
    - Depend on a provider for the current ReactAgent instead of a fixed instance.
- `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`
    - Add the default registration role `AGENT_MCP_USER`.
- `be/src/test/resources/application.yaml`
    - Keep MCP auto connection disabled in tests by default if needed.

### Frontend files to create

- `fe/src/modules/agent/mcp/mcpTypes.ts`
    - MCP frontend DTO types.
- `fe/src/modules/agent/mcp/mcpService.ts`
    - MCP admin API client functions.
- `fe/src/modules/agent/mcp/mcpPermissionCodes.ts`
    - MCP button permission code constants.
- `fe/src/modules/agent/mcp/McpServerListPage.tsx`
    - Server config management page.
- `fe/src/modules/agent/mcp/McpServerEditorDrawer.tsx`
    - Server create/edit drawer.
- `fe/src/modules/agent/mcp/McpToolListPage.tsx`
    - Tool policy page.
- `fe/src/modules/agent/mcp/McpToolPolicyDrawer.tsx`
    - Tool policy edit drawer.
- `fe/src/modules/agent/mcp/McpToolCallLogListPage.tsx`
    - Tool-call log page.
- `fe/src/modules/agent/mcp/mcpService.test.ts`
    - Service request construction tests.

### Frontend files to modify

- `fe/src/app/routes.tsx`
    - Add MCP routes.
- `fe/src/layouts/AdminLayout/menu.tsx`
    - Add Agent MCP menu entries.
- `fe/src/layouts/AdminLayout/permissionImpact.ts`
    - Add MCP button permission route impact.
- `fe/src/layouts/AdminLayout/permissionImpact.test.ts`
    - Verify permission-change detection includes MCP pages.
- `fe/src/layouts/AdminLayout/menu.test.tsx`
    - Verify MCP menu filtering.

---

### Task 1: Dependency Probe and Flyway Schema

**Files:**

- Modify: `be/pom.xml`
- Create: `be/src/main/resources/db/migration/V14__create_agent_mcp_schema.sql`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/McpSchemaMigrationTests.java`

- [ ] **Step 1: Verify MCP API signatures locally**

Run:

```bash
javap -classpath /Users/mario/.m2/repository/io/modelcontextprotocol/sdk/mcp/0.17.0/mcp-0.17.0.jar:/Users/mario/.m2/repository/io/modelcontextprotocol/sdk/mcp-core/0.17.0/mcp-core-0.17.0.jar:/Users/mario/.m2/repository/io/modelcontextprotocol/sdk/mcp-spring-webflux/0.17.0/mcp-spring-webflux-0.17.0.jar io.modelcontextprotocol.client.McpClient io.modelcontextprotocol.client.McpSyncClient io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport io.modelcontextprotocol.client.transport.WebFluxSseClientTransport
```

Expected: output includes `McpClient.sync(McpClientTransport)`, `McpSyncClient.initialize()`,
`McpSyncClient.listTools()`, `WebClientStreamableHttpTransport.builder(WebClient.Builder)`, and
`WebFluxSseClientTransport.builder(WebClient.Builder)`.

- [ ] **Step 2: Add MCP WebFlux starter dependency**

Modify `be/pom.xml` inside `<dependencies>` near other Spring AI dependencies:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
</dependency>
```

- [ ] **Step 3: Add Flyway migration**

Create `be/src/main/resources/db/migration/V14__create_agent_mcp_schema.sql`:

```sql
CREATE TABLE agent_mcp_server_config (
    id BIGSERIAL PRIMARY KEY,
    server_code VARCHAR(64) NOT NULL,
    server_name VARCHAR(128) NOT NULL,
    transport_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    endpoint VARCHAR(256) NOT NULL,
    headers_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    connect_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    request_timeout_ms INTEGER NOT NULL DEFAULT 30000,
    status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    last_error VARCHAR(1024),
    last_connected_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_mcp_server_code_deleted UNIQUE (server_code, deleted)
);

CREATE INDEX idx_agent_mcp_server_enabled ON agent_mcp_server_config (enabled, deleted);

CREATE TABLE agent_mcp_tool_config (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_key VARCHAR(192) NOT NULL,
    display_name VARCHAR(192) NOT NULL,
    description TEXT,
    input_schema_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    readonly BOOLEAN NOT NULL DEFAULT FALSE,
    require_confirm BOOLEAN NOT NULL DEFAULT TRUE,
    last_discovered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_agent_mcp_tool_server FOREIGN KEY (server_id) REFERENCES agent_mcp_server_config (id),
    CONSTRAINT uk_agent_mcp_tool_server_name_deleted UNIQUE (server_id, tool_name, deleted),
    CONSTRAINT uk_agent_mcp_tool_key_deleted UNIQUE (tool_key, deleted)
);

CREATE INDEX idx_agent_mcp_tool_enabled ON agent_mcp_tool_config (enabled, deleted);
CREATE INDEX idx_agent_mcp_tool_server ON agent_mcp_tool_config (server_id, deleted);

CREATE TABLE agent_mcp_tool_call_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64),
    thread_id VARCHAR(128),
    user_id BIGINT,
    server_code VARCHAR(64) NOT NULL,
    tool_key VARCHAR(192) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    request_args_summary TEXT,
    response_summary TEXT,
    status VARCHAR(32) NOT NULL,
    error_msg VARCHAR(1024),
    cost_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_mcp_tool_call_created ON agent_mcp_tool_call_log (created_at DESC);
CREATE INDEX idx_agent_mcp_tool_call_thread ON agent_mcp_tool_call_log (thread_id);
CREATE INDEX idx_agent_mcp_tool_call_tool ON agent_mcp_tool_call_log (tool_key);
```

- [ ] **Step 4: Write migration smoke test**

Create `be/src/test/java/top/egon/mario/agent/mcp/McpSchemaMigrationTests.java`:

```java
package top.egon.mario.agent.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the MCP management schema is available after Flyway migration.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class McpSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mcpTablesExist() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in (
                    'agent_mcp_server_config',
                    'agent_mcp_tool_config',
                    'agent_mcp_tool_call_log'
                )
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
    }
}
```

- [ ] **Step 5: Run migration smoke test**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=McpSchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/pom.xml be/src/main/resources/db/migration/V14__create_agent_mcp_schema.sql be/src/test/java/top/egon/mario/agent/mcp/McpSchemaMigrationTests.java
git commit -m "feat: add mcp management schema"
```

---

### Task 2: MCP Persistence Model

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpTransportType.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpServerStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolRuntimeStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolRiskLevel.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/enums/McpToolCallStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/McpServerConfigPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/McpToolConfigPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/po/McpToolCallLogPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/repository/McpServerConfigRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/repository/McpToolConfigRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/repository/McpToolCallLogRepository.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/repository/McpRepositoryTests.java`

- [ ] **Step 1: Create enum classes**

Create these enum files with the same class-level comment style used by nearby PO packages:

```java
package top.egon.mario.agent.mcp.po.enums;

/**
 * Remote transport type supported by the dynamic MCP client manager.
 */
public enum McpTransportType {
    STREAMABLE_HTTP,
    SSE
}
```

```java
package top.egon.mario.agent.mcp.po.enums;

/**
 * Connection status tracked for a configured MCP server.
 */
public enum McpServerStatus {
    DISABLED,
    CONNECTING,
    CONNECTED,
    FAILED
}
```

```java
package top.egon.mario.agent.mcp.po.enums;

/**
 * Derived runtime availability status shown in the MCP tool console.
 */
public enum McpToolRuntimeStatus {
    AVAILABLE,
    DISABLED,
    SERVER_DISABLED,
    SERVER_FAILED,
    POLICY_BLOCKED
}
```

```java
package top.egon.mario.agent.mcp.po.enums;

/**
 * Coarse risk level assigned to an MCP tool by administrators.
 */
public enum McpToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
```

```java
package top.egon.mario.agent.mcp.po.enums;

/**
 * Execution status stored for MCP tool calls.
 */
public enum McpToolCallStatus {
    SUCCESS,
    FAILED,
    BLOCKED
}
```

- [ ] **Step 2: Create server entity**

Create `McpServerConfigPo`:

```java
package top.egon.mario.agent.mcp.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Remote MCP server configuration managed from the admin console.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_server_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_mcp_server_code_deleted", columnNames = {"server_code", "deleted"})
})
public class McpServerConfigPo extends BaseAuditablePo {

    @Column(name = "server_code", nullable = false, length = 64)
    private String serverCode;

    @Column(name = "server_name", nullable = false, length = 128)
    private String serverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 32)
    private McpTransportType transportType;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "endpoint", nullable = false, length = 256)
    private String endpoint;

    @Column(name = "headers_json")
    private String headersJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs = 5000;

    @Column(name = "request_timeout_ms", nullable = false)
    private int requestTimeoutMs = 30000;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private McpServerStatus status = McpServerStatus.DISABLED;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;
}
```

- [ ] **Step 3: Create tool and log entities**

Create `McpToolConfigPo`:

```java
package top.egon.mario.agent.mcp.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Discovered MCP tool plus CyberMario runtime policy.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_tool_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_mcp_tool_server_name_deleted", columnNames = {"server_id", "tool_name", "deleted"}),
        @UniqueConstraint(name = "uk_agent_mcp_tool_key_deleted", columnNames = {"tool_key", "deleted"})
})
public class McpToolConfigPo extends BaseAuditablePo {

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "tool_key", nullable = false, length = 192)
    private String toolKey;

    @Column(name = "display_name", nullable = false, length = 192)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "input_schema_json")
    private String inputSchemaJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private McpToolRiskLevel riskLevel = McpToolRiskLevel.MEDIUM;

    @Column(name = "readonly", nullable = false)
    private boolean readonly;

    @Column(name = "require_confirm", nullable = false)
    private boolean requireConfirm = true;

    @Column(name = "last_discovered_at", nullable = false)
    private Instant lastDiscoveredAt;
}
```

Create `McpToolCallLogPo`:

```java
package top.egon.mario.agent.mcp.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;

import java.time.Instant;

/**
 * Persisted MCP tool execution log for admin audit.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_tool_call_log")
public class McpToolCallLogPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "server_code", nullable = false, length = 64)
    private String serverCode;

    @Column(name = "tool_key", nullable = false, length = 192)
    private String toolKey;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "request_args_summary")
    private String requestArgsSummary;

    @Column(name = "response_summary")
    private String responseSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private McpToolCallStatus status;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(name = "cost_ms", nullable = false)
    private long costMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

- [ ] **Step 4: Create repositories**

Create repository interfaces:

```java
package top.egon.mario.agent.mcp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;

import java.util.List;
import java.util.Optional;

public interface McpServerConfigRepository extends JpaRepository<McpServerConfigPo, Long> {

    Optional<McpServerConfigPo> findByIdAndDeletedFalse(Long id);

    Optional<McpServerConfigPo> findByServerCodeAndDeletedFalse(String serverCode);

    boolean existsByServerCodeAndDeletedFalse(String serverCode);

    List<McpServerConfigPo> findByDeletedFalseOrderByIdDesc();

    List<McpServerConfigPo> findByEnabledTrueAndDeletedFalseOrderByIdAsc();
}
```

```java
package top.egon.mario.agent.mcp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;

import java.util.List;
import java.util.Optional;

public interface McpToolConfigRepository extends JpaRepository<McpToolConfigPo, Long> {

    Optional<McpToolConfigPo> findByIdAndDeletedFalse(Long id);

    Optional<McpToolConfigPo> findByServerIdAndToolNameAndDeletedFalse(Long serverId, String toolName);

    boolean existsByToolKeyAndDeletedFalse(String toolKey);

    List<McpToolConfigPo> findByServerIdAndDeletedFalseOrderByIdAsc(Long serverId);

    List<McpToolConfigPo> findByDeletedFalseOrderByIdDesc();

    List<McpToolConfigPo> findByEnabledTrueAndDeletedFalseOrderByIdAsc();
}
```

```java
package top.egon.mario.agent.mcp.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpToolCallLogPo;

import java.util.Optional;

public interface McpToolCallLogRepository extends JpaRepository<McpToolCallLogPo, Long> {

    Optional<McpToolCallLogPo> findById(Long id);

    Page<McpToolCallLogPo> findAllByOrderByIdDesc(Pageable pageable);
}
```

- [ ] **Step 5: Write repository tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/repository/McpRepositoryTests.java`:

```java
package top.egon.mario.agent.mcp.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies MCP repositories persist server and tool policy records.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class McpRepositoryTests {

    @Autowired
    private McpServerConfigRepository serverRepository;
    @Autowired
    private McpToolConfigRepository toolRepository;

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
}
```

- [ ] **Step 6: Run repository tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=McpRepositoryTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/mcp/po be/src/main/java/top/egon/mario/agent/mcp/repository be/src/test/java/top/egon/mario/agent/mcp/repository/McpRepositoryTests.java
git commit -m "feat: add mcp persistence model"
```

---

### Task 3: DTOs and Management Services

**Files:**

- Create DTOs under `be/src/main/java/top/egon/mario/agent/mcp/dto`
- Create services under `be/src/main/java/top/egon/mario/agent/mcp/service`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/service/McpServerConfigServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolConfigServiceTests.java`

- [ ] **Step 1: Create request DTOs**

Create `CreateMcpServerRequest`:

```java
package top.egon.mario.agent.mcp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.util.Map;

public record CreateMcpServerRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,62}$") String serverCode,
        @NotBlank @Size(max = 128) String serverName,
        @NotNull McpTransportType transportType,
        @NotBlank @Size(max = 512) String baseUrl,
        @NotBlank @Size(max = 256) String endpoint,
        Map<String, String> headers,
        @Min(1000) @Max(60000) Integer connectTimeoutMs,
        @Min(1000) @Max(120000) Integer requestTimeoutMs
) {
}
```

Create `UpdateMcpServerRequest`:

```java
package top.egon.mario.agent.mcp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.util.Map;

public record UpdateMcpServerRequest(
        @NotBlank @Size(max = 128) String serverName,
        @NotNull McpTransportType transportType,
        @NotBlank @Size(max = 512) String baseUrl,
        @NotBlank @Size(max = 256) String endpoint,
        Map<String, String> headers,
        @Min(1000) @Max(60000) Integer connectTimeoutMs,
        @Min(1000) @Max(120000) Integer requestTimeoutMs
) {
}
```

Create `UpdateMcpToolPolicyRequest`:

```java
package top.egon.mario.agent.mcp.dto.request;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;

public record UpdateMcpToolPolicyRequest(
        @NotNull McpToolRiskLevel riskLevel,
        boolean readonly,
        boolean requireConfirm
) {
}
```

- [ ] **Step 2: Create response DTOs**

Create response records:

```java
package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;

import java.time.Instant;
import java.util.Map;

public record McpServerResponse(
        Long id,
        String serverCode,
        String serverName,
        McpTransportType transportType,
        String baseUrl,
        String endpoint,
        Map<String, String> headers,
        boolean enabled,
        int connectTimeoutMs,
        int requestTimeoutMs,
        McpServerStatus status,
        String lastError,
        Instant lastConnectedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
```

```java
package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;

import java.time.Instant;

public record McpToolResponse(
        Long id,
        Long serverId,
        String serverCode,
        String toolName,
        String toolKey,
        String displayName,
        String description,
        String inputSchemaJson,
        boolean enabled,
        McpToolRiskLevel riskLevel,
        boolean readonly,
        boolean requireConfirm,
        McpToolRuntimeStatus runtimeStatus,
        Instant lastDiscoveredAt
) {
}
```

```java
package top.egon.mario.agent.mcp.dto.response;

public record McpConnectionTestResponse(
        boolean success,
        String serverName,
        String serverVersion,
        int toolCount,
        String errorMessage
) {
}
```

```java
package top.egon.mario.agent.mcp.dto.response;

public record McpToolDiscoveryResponse(
        Long serverId,
        int discoveredCount,
        int createdCount,
        int updatedCount
) {
}
```

```java
package top.egon.mario.agent.mcp.dto.response;

import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;

import java.time.Instant;

public record McpToolCallLogResponse(
        Long id,
        String traceId,
        String threadId,
        Long userId,
        String serverCode,
        String toolKey,
        String toolName,
        String requestArgsSummary,
        String responseSummary,
        McpToolCallStatus status,
        String errorMsg,
        long costMs,
        Instant createdAt
) {
}
```

- [ ] **Step 3: Write service tests for defaults and masking**

Create `McpServerConfigServiceTests` with this core test:

```java
@Test
void createServerStoresDisabledConfigAndMasksHeadersInResponse() {
    CreateMcpServerRequest request = new CreateMcpServerRequest(
            "docs",
            "Docs MCP",
            McpTransportType.STREAMABLE_HTTP,
            "https://example.com",
            "/mcp",
            Map.of("Authorization", "Bearer secret-token"),
            null,
            null);

    McpServerResponse response = service.create(request, 7L);

    assertThat(response.enabled()).isFalse();
    assertThat(response.status()).isEqualTo(McpServerStatus.DISABLED);
    assertThat(response.connectTimeoutMs()).isEqualTo(5000);
    assertThat(response.requestTimeoutMs()).isEqualTo(30000);
    assertThat(response.headers()).containsEntry("Authorization", "Bearer ********");
    McpServerConfigPo saved = serverRepository.findByServerCodeAndDeletedFalse("docs").orElseThrow();
    assertThat(saved.getHeadersJson()).contains("secret-token");
}
```

Create `McpToolConfigServiceTests` with this core test:

```java
@Test
void enableToolRejectsToolThatRequiresConfirmation() {
    McpServerConfigPo server = serverRepository.save(server("docs", true, McpServerStatus.CONNECTED));
    McpToolConfigPo tool = toolRepository.save(tool(server.getId(), "docs_search", true));

    assertThatThrownBy(() -> service.enable(tool.getId(), 7L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires confirmation");
}
```

- [ ] **Step 4: Implement `McpServerConfigService`**

Implement public methods:

```java
public List<McpServerResponse> list();
public McpServerResponse get(Long id);
public McpServerResponse create(CreateMcpServerRequest request, Long actorId);
public McpServerResponse update(Long id, UpdateMcpServerRequest request, Long actorId);
public void enable(Long id, Long actorId);
public void disable(Long id, Long actorId);
public void delete(Long id, Long actorId);
public McpServerConfigPo requireServer(Long id);
```

Important implementation rules:

```java
private String normalizeServerCode(String serverCode) {
    return serverCode.trim().toLowerCase(Locale.ROOT);
}

private int timeoutOrDefault(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
}

private Map<String, String> maskHeaders(String headersJson) {
    Map<String, String> headers = readHeaders(headersJson);
    return headers.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> maskHeaderValue(entry.getValue())));
}
```

Mask any nonblank header value longer than 8 characters as prefix plus `********`. Return `********` for shorter
nonblank values.

- [ ] **Step 5: Implement `McpToolConfigService`**

Implement public methods:

```java
public List<McpToolResponse> list(Long serverId);
public McpToolResponse get(Long id);
public McpToolResponse updatePolicy(Long id, UpdateMcpToolPolicyRequest request, Long actorId);
public void enable(Long id, Long actorId);
public void disable(Long id, Long actorId);
```

Tool visibility rule:

```java
if (tool.isRequireConfirm()) {
    throw new IllegalStateException("tool requires confirmation and cannot be enabled for stage one");
}
```

Derived runtime status rule:

```java
if (!tool.isEnabled()) return McpToolRuntimeStatus.DISABLED;
if (!server.isEnabled()) return McpToolRuntimeStatus.SERVER_DISABLED;
if (server.getStatus() == McpServerStatus.FAILED) return McpToolRuntimeStatus.SERVER_FAILED;
if (tool.isRequireConfirm()) return McpToolRuntimeStatus.POLICY_BLOCKED;
return McpToolRuntimeStatus.AVAILABLE;
```

- [ ] **Step 6: Run service tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=McpServerConfigServiceTests,McpToolConfigServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/mcp/dto be/src/main/java/top/egon/mario/agent/mcp/service be/src/test/java/top/egon/mario/agent/mcp/service
git commit -m "feat: add mcp management services"
```

---

### Task 4: Runtime MCP Client, Discovery, Registry, and Logging

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpClientFactory.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/DynamicMcpClientManager.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistry.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/LoggingMcpToolCallback.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentToolProvider.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/policy/McpToolPolicyService.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolCallLogService.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/config/McpManagementConfiguration.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpToolPolicyServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistryTests.java`

- [ ] **Step 1: Write policy tests**

Create `McpToolPolicyServiceTests`:

```java
@Test
void visibleOnlyWhenServerAndToolAreEnabledAndConfirmationIsNotRequired() {
    McpToolPolicyService service = new McpToolPolicyService();

    assertThat(service.isAgentVisible(server(true, McpServerStatus.CONNECTED), tool(true, false))).isTrue();
    assertThat(service.isAgentVisible(server(false, McpServerStatus.DISABLED), tool(true, false))).isFalse();
    assertThat(service.isAgentVisible(server(true, McpServerStatus.CONNECTED), tool(false, false))).isFalse();
    assertThat(service.isAgentVisible(server(true, McpServerStatus.CONNECTED), tool(true, true))).isFalse();
    assertThat(service.isAgentVisible(server(true, McpServerStatus.FAILED), tool(true, false))).isFalse();
}
```

- [ ] **Step 2: Implement policy service**

Create:

```java
package top.egon.mario.agent.mcp.policy;

import org.springframework.stereotype.Service;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;

/**
 * Applies stage-one MCP tool visibility policy before tools reach ReactAgent.
 */
@Service
public class McpToolPolicyService {

    public boolean isAgentVisible(McpServerConfigPo server, McpToolConfigPo tool) {
        return server != null
                && tool != null
                && server.isEnabled()
                && server.getStatus() == McpServerStatus.CONNECTED
                && tool.isEnabled()
                && !tool.isRequireConfirm();
    }
}
```

- [ ] **Step 3: Implement MCP client factory**

Create `McpClientFactory` using SDK classes verified in Task 1:

```java
McpClientTransport transport = switch (server.getTransportType()) {
    case STREAMABLE_HTTP -> WebClientStreamableHttpTransport.builder(webClientBuilder(server))
            .endpoint(server.getEndpoint())
            .build();
    case SSE -> WebFluxSseClientTransport.builder(webClientBuilder(server))
            .sseEndpoint(server.getEndpoint())
            .build();
};
McpSyncClient client = McpClient.sync(transport)
        .clientInfo(new McpSchema.Implementation("CyberMario", "0.0.1"))
        .requestTimeout(Duration.ofMillis(server.getRequestTimeoutMs()))
        .initializationTimeout(Duration.ofMillis(server.getConnectTimeoutMs()))
        .build();
client.initialize();
return client;
```

The `webClientBuilder` must set `baseUrl(server.getBaseUrl())` and apply headers parsed from `headersJson`.

- [ ] **Step 4: Implement runtime manager**

`DynamicMcpClientManager` maintains:

```java
private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();
```

Required methods:

```java
public synchronized void reloadEnabledServers();
public synchronized void refreshServer(Long serverId);
public synchronized void disableServer(Long serverId);
public Optional<McpSyncClient> client(Long serverId);
public List<McpSyncClient> clients();
```

Rules:

- Close and replace an existing client on refresh.
- Update server status to `CONNECTED` and `lastConnectedAt` after successful initialize.
- Update server status to `FAILED` and `lastError` after failure.
- Set status `DISABLED` when disabling.

- [ ] **Step 5: Implement tool discovery**

`McpToolDiscoveryService.discover(Long serverId, Long actorId)`:

```java
McpSyncClient client = clientFactory.create(server);
McpSchema.ListToolsResult result = client.listTools();
for (McpSchema.Tool tool : result.tools()) {
    upsertTool(server, tool);
}
client.closeGracefully();
```

Tool key generation:

```java
String toolKey = normalize(server.getServerCode()) + "_" + normalize(tool.name());
```

New tool defaults:

```java
enabled = false;
riskLevel = McpToolRiskLevel.MEDIUM;
readonly = false;
requireConfirm = true;
displayName = toolKey;
```

Existing tool policy must be preserved while description/schema/lastDiscoveredAt are updated.

- [ ] **Step 6: Implement runtime registry**

`McpRuntimeRegistry.currentToolCallbacks()`:

```java
List<ToolCallback> callbacks = new ArrayList<>();
for (McpToolConfigPo tool : toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()) {
    McpServerConfigPo server = serverRepository.findByIdAndDeletedFalse(tool.getServerId()).orElse(null);
    if (!policyService.isAgentVisible(server, tool)) {
        continue;
    }
    McpSyncClient client = clientManager.client(tool.getServerId()).orElse(null);
    if (client == null) {
        continue;
    }
    McpSchema.Tool schemaTool = findRuntimeTool(client, tool.getToolName());
    ToolCallback callback = SyncMcpToolCallback.builder()
            .mcpClient(client)
            .tool(schemaTool)
            .prefixedToolName(tool.getToolKey())
            .build();
    callbacks.add(new LoggingMcpToolCallback(server, tool, callback, logService));
}
return callbacks.toArray(new ToolCallback[0]);
```

- [ ] **Step 7: Implement logging wrapper**

`LoggingMcpToolCallback` delegates:

```java
@Override
public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
}

@Override
public String call(String toolInput) {
    return call(toolInput, null);
}

@Override
public String call(String toolInput, ToolContext toolContext) {
    long start = System.currentTimeMillis();
    try {
        String response = delegate.call(toolInput, toolContext);
        logService.recordSuccess(server, tool, toolInput, response, System.currentTimeMillis() - start, toolContext);
        return response;
    } catch (Exception e) {
        logService.recordFailure(server, tool, toolInput, e, System.currentTimeMillis() - start, toolContext);
        throw e;
    }
}
```

Summaries must be bounded to 4000 chars for args and response, and 1024 chars for error.

- [ ] **Step 8: Run runtime tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=McpToolPolicyServiceTests,McpRuntimeRegistryTests test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/mcp/runtime be/src/main/java/top/egon/mario/agent/mcp/policy be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java be/src/main/java/top/egon/mario/agent/mcp/service/McpToolCallLogService.java be/src/main/java/top/egon/mario/agent/mcp/config/McpManagementConfiguration.java be/src/test/java/top/egon/mario/agent/mcp/runtime
git commit -m "feat: add dynamic mcp runtime registry"
```

---

### Task 5: ReactAgent Dynamic Refresh

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/ReactAgentProvider.java`
- Test: `be/src/test/java/top/egon/mario/agent/ReactAgentDynamicRefreshTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/config/AgentConfigurationTests.java`

- [ ] **Step 1: Create provider interface**

Create:

```java
package top.egon.mario.agent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;

/**
 * Supplies the active ReactAgent snapshot for each chat request.
 */
public interface ReactAgentProvider {

    ReactAgent current();
}
```

- [ ] **Step 2: Write dynamic refresh test**

Core assertion:

```java
@Test
void chatServiceUsesCurrentAgentForEachRequest() throws Exception {
    ReactAgent first = mock(ReactAgent.class);
    ReactAgent second = mock(ReactAgent.class);
    AtomicReference<ReactAgent> current = new AtomicReference<>(first);
    ReactAgentProvider provider = current::get;
    given(first.stream(eq("one"), any(RunnableConfig.class))).willReturn(Flux.empty());
    given(second.stream(eq("two"), any(RunnableConfig.class))).willReturn(Flux.empty());

    ReactAgentChatService service = new ReactAgentChatService(provider, Schedulers.immediate(), new ArxivToolUserContext());

    StepVerifier.create(service.chat("one", "thread-1", null)).verifyComplete();
    current.set(second);
    StepVerifier.create(service.chat("two", "thread-1", null)).verifyComplete();

    then(first).should().stream(eq("one"), any(RunnableConfig.class));
    then(second).should().stream(eq("two"), any(RunnableConfig.class));
}
```

- [ ] **Step 3: Refactor `ReactAgentChatService` constructor**

Change field and constructor:

```java
private final ReactAgentProvider agentProvider;

public ReactAgentChatService(ReactAgentProvider agentProvider, Scheduler blockingScheduler,
                             ArxivToolUserContext arxivToolUserContext) {
    this.agentProvider = agentProvider;
    this.blockingScheduler = blockingScheduler;
    this.arxivToolUserContext = arxivToolUserContext;
}
```

Change stream invocation:

```java
return agentProvider.current().stream(message, cfg);
```

- [ ] **Step 4: Build active ReactAgent provider**

In `AgentConfiguration`, create a small nested or package-visible provider class that holds:

```java
private final AtomicReference<ReactAgent> current = new AtomicReference<>();
```

It must:

- Build a ReactAgent with local tool callbacks plus `mcpAgentToolProvider.currentToolCallbacks()`.
- Refresh on `McpAgentRefreshService` signal.
- Keep the previous agent if rebuild fails.

Constructor dependencies:

```java
MarioModelFactory marioModelFactory,
List<ToolCallback> toolCallbacks,
McpAgentToolProvider mcpAgentToolProvider
```

Filter out MCP logging callbacks from the base `List<ToolCallback>` if they are exposed as beans; the recommended
implementation is for MCP runtime callbacks not to be Spring beans, so `List<ToolCallback>` remains local tools only.

- [ ] **Step 5: Update chat service bean**

Change:

```java
public ChatAgentService chatAgentService(ReactAgentProvider reactAgentProvider, Scheduler blockingScheduler,
                                         ArxivToolUserContext arxivToolUserContext) {
    return new ReactAgentChatService(reactAgentProvider, blockingScheduler, arxivToolUserContext);
}
```

- [ ] **Step 6: Run agent tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=ReactAgentChatServiceTests,ReactAgentDynamicRefreshTests,AgentConfigurationTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java be/src/main/java/top/egon/mario/agent/service/ReactAgentProvider.java be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java be/src/test/java/top/egon/mario/agent be/src/test/java/top/egon/mario/agent/config/AgentConfigurationTests.java
git commit -m "feat: refresh react agent tool snapshots"
```

---

### Task 6: Admin APIs and RBAC Resources

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolCallLogController.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/service/resource/AgentMcpPermissionCatalog.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/service/resource/AgentMcpRbacResourceProvider.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/service/resource/AgentMcpRbacResourceProviderTests.java`
- Test: `be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java`

- [ ] **Step 1: Create RBAC catalog**

`AgentMcpPermissionCatalog` must return these resources:

Menus:

```java
new MenuPermissionSeed("menu:agent:mcp-servers", "MCP 服务配置", "menu:agent", "/agent/mcp/servers", "agent-mcp-servers", 11)
new MenuPermissionSeed("menu:agent:mcp-tools", "MCP 工具策略", "menu:agent", "/agent/mcp/tools", "agent-mcp-tools", 12)
new MenuPermissionSeed("menu:agent:mcp-logs", "MCP 调用日志", "menu:agent", "/agent/mcp/logs", "agent-mcp-logs", 13)
```

Buttons:

```java
btn:agent:mcp-server:add       -> api:agent:mcp-server:*
btn:agent:mcp-server:edit      -> api:agent:mcp-server:*
btn:agent:mcp-server:delete    -> api:agent:mcp-server:*
btn:agent:mcp-server:test      -> api:agent:mcp-server:*
btn:agent:mcp-server:discover  -> api:agent:mcp-server:*
btn:agent:mcp-server:toggle    -> api:agent:mcp-server:*
btn:agent:mcp-tool:edit-policy -> api:agent:mcp-tool:*
btn:agent:mcp-tool:toggle      -> api:agent:mcp-tool:*
btn:agent:mcp-log:view         -> api:agent:mcp-log:collection
```

APIs:

```java
api:agent:mcp-server:collection GET /api/admin/agent/mcp/servers EXACT HIGH
api:agent:mcp-server:*          ANY /api/admin/agent/mcp/servers/** ANT HIGH
api:agent:mcp-tool:collection   GET /api/admin/agent/mcp/tools EXACT HIGH
api:agent:mcp-tool:*            ANY /api/admin/agent/mcp/tools/** ANT HIGH
api:agent:mcp-log:collection    GET /api/admin/agent/mcp/tool-calls EXACT HIGH
api:agent:mcp-log:*             ANY /api/admin/agent/mcp/tool-calls/** ANT HIGH
```

Role presets:

```java
AGENT_MCP_USER:
menu:agent
menu:agent:mcp-servers
menu:agent:mcp-tools
btn:agent:mcp-server:add
btn:agent:mcp-server:edit
btn:agent:mcp-server:delete
btn:agent:mcp-server:test
btn:agent:mcp-server:discover
btn:agent:mcp-server:toggle
btn:agent:mcp-tool:edit-policy
btn:agent:mcp-tool:toggle
api:agent:mcp-server:collection
api:agent:mcp-server:*
api:agent:mcp-tool:collection
api:agent:mcp-tool:*
api:rbac:auth:self
api:rbac:me:self
```

```java
AGENT_MCP_ADMIN:
all AGENT_MCP_USER permissions
menu:agent:mcp-logs
btn:agent:mcp-log:view
api:agent:mcp-log:collection
api:agent:mcp-log:*
```

- [ ] **Step 2: Write RBAC tests**

Create `AgentMcpRbacResourceProviderTests`:

```java
@Test
void defaultMcpUserCanManageServersAndToolsButCannotViewLogs() {
    AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

    RbacRolePresetSeed user = provider.rolePresets().stream()
            .filter(seed -> seed.roleCode().equals("AGENT_MCP_USER"))
            .findFirst()
            .orElseThrow();

    assertThat(user.permissionCodes())
            .contains("menu:agent:mcp-servers",
                    "menu:agent:mcp-tools",
                    "api:agent:mcp-server:*",
                    "api:agent:mcp-tool:*",
                    "btn:agent:mcp-server:add",
                    "btn:agent:mcp-tool:toggle")
            .doesNotContain("menu:agent:mcp-logs",
                    "api:agent:mcp-log:collection",
                    "api:agent:mcp-log:*",
                    "btn:agent:mcp-log:view");
}

@Test
void mcpAdminCanViewToolCallLogs() {
    AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

    RbacRolePresetSeed admin = provider.rolePresets().stream()
            .filter(seed -> seed.roleCode().equals("AGENT_MCP_ADMIN"))
            .findFirst()
            .orElseThrow();

    assertThat(admin.permissionCodes())
            .contains("menu:agent:mcp-logs",
                    "api:agent:mcp-log:collection",
                    "api:agent:mcp-log:*",
                    "btn:agent:mcp-log:view");
}
```

- [ ] **Step 3: Modify default registration roles**

In `RbacAuthApplication`, change:

```java
private static final List<String> DEFAULT_REGISTER_ROLE_CODES = List.of(
        "CHAT_BASIC",
        "RAG_USER",
        "AGENT_DASHBOARD_USER",
        "AGENT_MCP_USER");
```

- [ ] **Step 4: Update registration test**

In `RbacAuthApplicationTests.registerCreatesEnabledUserWithDefaultBusinessRolesAndNoRbacAdminPermission`, create and
grant `AGENT_MCP_USER`:

```java
RolePo mcpRole = roleRepository.save(role("AGENT_MCP_USER"));
grant(mcpRole, menuPermission("menu:agent:mcp-servers", "agent-mcp-servers", "/agent/mcp/servers"));
grant(mcpRole, menuPermission("menu:agent:mcp-tools", "agent-mcp-tools", "/agent/mcp/tools"));
grant(mcpRole, permission("api:agent:mcp-server:*", PermissionType.API));
grant(mcpRole, permission("api:agent:mcp-tool:*", PermissionType.API));
permission("menu:agent:mcp-logs", PermissionType.MENU);
permission("api:agent:mcp-log:collection", PermissionType.API);
permission("api:agent:mcp-log:*", PermissionType.API);
```

Expected role assertion:

```java
assertThat(response.roleCodes()).containsExactlyInAnyOrder(
        "CHAT_BASIC",
        "RAG_USER",
        "AGENT_DASHBOARD_USER",
        "AGENT_MCP_USER");
```

Expected permission assertion:

```java
assertThat(response.permissionCodes())
        .contains("api:chat:stream",
                "api:rag:document:*",
                "api:agent:model-audit:dashboard:self",
                "api:agent:mcp-server:*",
                "api:agent:mcp-tool:*")
        .doesNotContain("api:rbac:admin:*",
                "api:agent:model-audit:dashboard:global",
                "api:agent:model-audit:dashboard:user-options",
                "api:agent:mcp-log:collection",
                "api:agent:mcp-log:*");
```

- [ ] **Step 5: Implement controllers**

Controller paths and methods:

```java
@RequestMapping("/api/admin/agent/mcp/servers")
GET "" -> page/list servers
POST "" -> create
GET "/{id}" -> detail
PUT "/{id}" -> update
POST "/{id}/enable" -> enable
POST "/{id}/disable" -> disable
POST "/{id}/test" -> test connection
POST "/{id}/discover-tools" -> discover tools
DELETE "/{id}" -> soft delete
```

```java
@RequestMapping("/api/admin/agent/mcp/tools")
GET "" -> list tools, optional serverId
GET "/{id}" -> detail
PUT "/{id}/policy" -> update policy
POST "/{id}/enable" -> enable
POST "/{id}/disable" -> disable
```

```java
@RequestMapping("/api/admin/agent/mcp/tool-calls")
GET "" -> page logs
GET "/{id}" -> detail
```

Use `@RbacApi` or provider resources consistently. The provider resources are authoritative for menu/button/API grants;
annotations can remain on controller methods for scanner compatibility.

- [ ] **Step 6: Run RBAC and registration tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=AgentMcpRbacResourceProviderTests,RbacAuthApplicationTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/mcp/web be/src/main/java/top/egon/mario/agent/mcp/service/resource be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java be/src/test/java/top/egon/mario/agent/mcp/service/resource/AgentMcpRbacResourceProviderTests.java be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java
git commit -m "feat: add mcp admin api and rbac resources"
```

---

### Task 7: Frontend API Client, Permissions, Routes, and Menu

**Files:**

- Create: `fe/src/modules/agent/mcp/mcpTypes.ts`
- Create: `fe/src/modules/agent/mcp/mcpService.ts`
- Create: `fe/src/modules/agent/mcp/mcpPermissionCodes.ts`
- Create: `fe/src/modules/agent/mcp/mcpService.test.ts`
- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.ts`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.test.ts`
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`

- [ ] **Step 1: Create frontend types**

Create `mcpTypes.ts`:

```ts
export type McpTransportType = 'STREAMABLE_HTTP' | 'SSE'
export type McpServerStatus = 'DISABLED' | 'CONNECTING' | 'CONNECTED' | 'FAILED'
export type McpToolRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type McpToolRuntimeStatus = 'AVAILABLE' | 'DISABLED' | 'SERVER_DISABLED' | 'SERVER_FAILED' | 'POLICY_BLOCKED'
export type McpToolCallStatus = 'SUCCESS' | 'FAILED' | 'BLOCKED'

export type McpServerResponse = {
    id: number
    serverCode: string
    serverName: string
    transportType: McpTransportType
    baseUrl: string
    endpoint: string
    headers?: Record<string, string>
    enabled: boolean
    connectTimeoutMs: number
    requestTimeoutMs: number
    status: McpServerStatus
    lastError?: string
    lastConnectedAt?: string
    createdAt?: string
    updatedAt?: string
}

export type McpToolResponse = {
    id: number
    serverId: number
    serverCode: string
    toolName: string
    toolKey: string
    displayName: string
    description?: string
    inputSchemaJson?: string
    enabled: boolean
    riskLevel: McpToolRiskLevel
    readonly: boolean
    requireConfirm: boolean
    runtimeStatus: McpToolRuntimeStatus
    lastDiscoveredAt?: string
}

export type CreateMcpServerRequest = {
    serverCode: string
    serverName: string
    transportType: McpTransportType
    baseUrl: string
    endpoint: string
    headers?: Record<string, string>
    connectTimeoutMs?: number
    requestTimeoutMs?: number
}

export type UpdateMcpServerRequest = Omit<CreateMcpServerRequest, 'serverCode'>

export type UpdateMcpToolPolicyRequest = {
    riskLevel: McpToolRiskLevel
    readonly: boolean
    requireConfirm: boolean
}

export type McpConnectionTestResponse = {
    success: boolean
    serverName?: string
    serverVersion?: string
    toolCount: number
    errorMessage?: string
}

export type McpToolDiscoveryResponse = {
    serverId: number
    discoveredCount: number
    createdCount: number
    updatedCount: number
}

export type McpToolCallLogResponse = {
    id: number
    traceId?: string
    threadId?: string
    userId?: number
    serverCode: string
    toolKey: string
    toolName: string
    requestArgsSummary?: string
    responseSummary?: string
    status: McpToolCallStatus
    errorMsg?: string
    costMs: number
    createdAt: string
}
```

- [ ] **Step 2: Create permission constants**

Create `mcpPermissionCodes.ts`:

```ts
export const mcpButtonCodes = {
    server: {
        create: 'btn:agent:mcp-server:add',
        edit: 'btn:agent:mcp-server:edit',
        delete: 'btn:agent:mcp-server:delete',
        test: 'btn:agent:mcp-server:test',
        discover: 'btn:agent:mcp-server:discover',
        toggle: 'btn:agent:mcp-server:toggle',
    },
    tool: {
        editPolicy: 'btn:agent:mcp-tool:edit-policy',
        toggle: 'btn:agent:mcp-tool:toggle',
    },
    log: {
        view: 'btn:agent:mcp-log:view',
    },
}
```

- [ ] **Step 3: Create API service**

Create `mcpService.ts`:

```ts
import {requestJson} from '../../../services/request'
import type {PageResult} from '../../../types/api'
import type {
    CreateMcpServerRequest,
    McpConnectionTestResponse,
    McpServerResponse,
    McpToolCallLogResponse,
    McpToolDiscoveryResponse,
    McpToolResponse,
    UpdateMcpServerRequest,
    UpdateMcpToolPolicyRequest,
} from './mcpTypes'

export function getMcpServers() {
    return requestJson<McpServerResponse[]>('/api/admin/agent/mcp/servers')
}

export function createMcpServer(request: CreateMcpServerRequest) {
    return requestJson<McpServerResponse>('/api/admin/agent/mcp/servers', {
        method: 'POST',
        body: JSON.stringify(request),
    })
}

export function updateMcpServer(id: number, request: UpdateMcpServerRequest) {
    return requestJson<McpServerResponse>(`/api/admin/agent/mcp/servers/${id}`, {
        method: 'PUT',
        body: JSON.stringify(request),
    })
}

export function deleteMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}`, {method: 'DELETE'})
}

export function enableMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}/enable`, {method: 'POST'})
}

export function disableMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}/disable`, {method: 'POST'})
}

export function testMcpServer(id: number) {
    return requestJson<McpConnectionTestResponse>(`/api/admin/agent/mcp/servers/${id}/test`, {method: 'POST'})
}

export function discoverMcpTools(id: number) {
    return requestJson<McpToolDiscoveryResponse>(`/api/admin/agent/mcp/servers/${id}/discover-tools`, {method: 'POST'})
}

export function getMcpTools(serverId?: number) {
    const query = serverId ? `?serverId=${serverId}` : ''
    return requestJson<McpToolResponse[]>(`/api/admin/agent/mcp/tools${query}`)
}

export function updateMcpToolPolicy(id: number, request: UpdateMcpToolPolicyRequest) {
    return requestJson<McpToolResponse>(`/api/admin/agent/mcp/tools/${id}/policy`, {
        method: 'PUT',
        body: JSON.stringify(request),
    })
}

export function enableMcpTool(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/tools/${id}/enable`, {method: 'POST'})
}

export function disableMcpTool(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/tools/${id}/disable`, {method: 'POST'})
}

export function getMcpToolCallLogs(params: { page: number; size: number }) {
    return requestJson<PageResult<McpToolCallLogResponse>>(
        `/api/admin/agent/mcp/tool-calls?page=${params.page}&size=${params.size}`,
    )
}
```

- [ ] **Step 4: Add routes**

Modify `fe/src/app/routes.tsx` inside admin layout children:

```tsx
{path: 'agent/mcp/servers', lazy: () => import('../modules/agent/mcp/McpServerListPage')},
{path: 'agent/mcp/tools', lazy: () => import('../modules/agent/mcp/McpToolListPage')},
{path: 'agent/mcp/logs', lazy: () => import('../modules/agent/mcp/McpToolCallLogListPage')},
```

- [ ] **Step 5: Add menu entries**

Modify `fe/src/layouts/AdminLayout/menu.tsx`.

Add imports:

```tsx
import {CloudServerOutlined, ToolOutlined} from '@ant-design/icons'
```

Add path mappings:

```ts
'/agent/mcp/servers': '/agent/mcp/servers',
'/agent/mcp/tools': '/agent/mcp/tools',
'/agent/mcp/logs': '/agent/mcp/logs',
```

Add menu children under the existing Agent/dashboard area. If `menu:agent` is represented as `/dashboard` today, create
a parent item:

```tsx
{
    key: 'agent',
    icon: <DashboardOutlined/>,
    label: 'Agent 管理',
    children: [
        {key: '/dashboard', icon: <DashboardOutlined/>, label: '首页控制台'},
        {key: '/agent/mcp/servers', icon: <CloudServerOutlined/>, label: 'MCP 服务配置'},
        {key: '/agent/mcp/tools', icon: <ToolOutlined/>, label: 'MCP 工具策略'},
        {key: '/agent/mcp/logs', icon: <FileSearchOutlined/>, label: 'MCP 调用日志'},
    ],
}
```

Keep `/dashboard` available for users with existing `menu:agent`.

- [ ] **Step 6: Add permission impact mapping**

Modify `permissionImpact.ts`:

```ts
import {mcpButtonCodes} from '../../modules/agent/mcp/mcpPermissionCodes'
```

Add:

```ts
{path: '/agent/mcp/servers', buttonCodes: Object.values(mcpButtonCodes.server)},
{path: '/agent/mcp/tools', buttonCodes: Object.values(mcpButtonCodes.tool)},
{path: '/agent/mcp/logs', buttonCodes: Object.values(mcpButtonCodes.log)},
```

- [ ] **Step 7: Run frontend route/menu tests**

Run:

```bash
npm test -- --run src/layouts/AdminLayout/menu.test.tsx src/layouts/AdminLayout/permissionImpact.test.ts src/modules/agent/mcp/mcpService.test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add fe/src/modules/agent/mcp/mcpTypes.ts fe/src/modules/agent/mcp/mcpService.ts fe/src/modules/agent/mcp/mcpPermissionCodes.ts fe/src/modules/agent/mcp/mcpService.test.ts fe/src/app/routes.tsx fe/src/layouts/AdminLayout/menu.tsx fe/src/layouts/AdminLayout/permissionImpact.ts fe/src/layouts/AdminLayout/permissionImpact.test.ts fe/src/layouts/AdminLayout/menu.test.tsx
git commit -m "feat: add mcp frontend routes and api client"
```

---

### Task 8: Frontend MCP Management Pages

**Files:**

- Create: `fe/src/modules/agent/mcp/McpServerListPage.tsx`
- Create: `fe/src/modules/agent/mcp/McpServerEditorDrawer.tsx`
- Create: `fe/src/modules/agent/mcp/McpToolListPage.tsx`
- Create: `fe/src/modules/agent/mcp/McpToolPolicyDrawer.tsx`
- Create: `fe/src/modules/agent/mcp/McpToolCallLogListPage.tsx`

- [ ] **Step 1: Build server editor drawer**

`McpServerEditorDrawer` props:

```ts
type McpServerEditorDrawerProps = {
    open: boolean
    server?: McpServerResponse | null
    onClose: () => void
    onSubmit: (request: CreateMcpServerRequest | UpdateMcpServerRequest) => Promise<void>
}
```

Form fields:

```tsx
serverCode
serverName
transportType
baseUrl
endpoint
headersText
connectTimeoutMs
requestTimeoutMs
```

Headers text format:

```text
Authorization: Bearer xxx
X-Api-Key: xxx
```

Parse into `Record<string, string>` by splitting nonblank lines at the first colon. Reject lines without a colon through
`Form.Item` validation.

- [ ] **Step 2: Build server list page**

`McpServerListPage` UI:

- `PageToolbar` title `MCP 服务配置`.
- Primary add button guarded by `canUseRbacButton(auth, mcpButtonCodes.server.create)`.
- Table columns: code, name, transport, base URL, endpoint, status, enabled, last connected, actions.
- Action buttons with icons: edit, test, discover, enable/disable, delete.
- Use `Switch` for enable/disable.
- After test/discover/toggle/delete, reload list.

Button guards:

```ts
const canCreate = canUseRbacButton(auth, mcpButtonCodes.server.create)
const canEdit = canUseRbacButton(auth, mcpButtonCodes.server.edit)
const canDelete = canUseRbacButton(auth, mcpButtonCodes.server.delete)
const canTest = canUseRbacButton(auth, mcpButtonCodes.server.test)
const canDiscover = canUseRbacButton(auth, mcpButtonCodes.server.discover)
const canToggle = canUseRbacButton(auth, mcpButtonCodes.server.toggle)
```

- [ ] **Step 3: Build tool policy drawer**

`McpToolPolicyDrawer` fields:

```tsx
riskLevel: Select LOW/MEDIUM/HIGH
readonly: Switch
requireConfirm: Switch
```

Show a warning text when `requireConfirm=true`:

```text
需要确认的工具在第一阶段不会暴露给 ReactAgent。
```

- [ ] **Step 4: Build tool list page**

`McpToolListPage` UI:

- `PageToolbar` title `MCP 工具策略`.
- Optional server filter using server list.
- Table columns: toolKey, serverCode, toolName, risk, readonly, requireConfirm, runtimeStatus, enabled,
  lastDiscoveredAt, actions.
- Expand row to show `description` and formatted `inputSchemaJson`.
- Toggle tool only if `canUseRbacButton(auth, mcpButtonCodes.tool.toggle)`.
- Edit policy only if `canUseRbacButton(auth, mcpButtonCodes.tool.editPolicy)`.

- [ ] **Step 5: Build tool-call log page**

`McpToolCallLogListPage` UI:

- `PageToolbar` title `MCP 调用日志`.
- Table columns: createdAt, status, serverCode, toolKey, userId, threadId, costMs, errorMsg.
- Expand row for request and response summaries.
- No mutating buttons.

- [ ] **Step 6: Run frontend tests and typecheck**

Run:

```bash
npm test -- --run src/layouts/AdminLayout/menu.test.tsx src/layouts/AdminLayout/permissionImpact.test.ts src/modules/agent/mcp/mcpService.test.ts
npm run build
```

Expected: tests PASS and build succeeds.

- [ ] **Step 7: Commit**

```bash
git add fe/src/modules/agent/mcp/McpServerListPage.tsx fe/src/modules/agent/mcp/McpServerEditorDrawer.tsx fe/src/modules/agent/mcp/McpToolListPage.tsx fe/src/modules/agent/mcp/McpToolPolicyDrawer.tsx fe/src/modules/agent/mcp/McpToolCallLogListPage.tsx
git commit -m "feat: add mcp management pages"
```

---

### Task 9: End-to-End Verification and Documentation Notes

**Files:**

- Modify: `README.md`
- Test: all focused tests from previous tasks

- [ ] **Step 1: Add README configuration note**

Add a short backend section:

```markdown
### Dynamic MCP Client Management

CyberMario can manage remote MCP servers from the admin console. Stage one supports Streamable HTTP and SSE transports. New MCP servers and tools are disabled by default; tools that require confirmation are not exposed to ReactAgent until a later confirmation workflow is added.
```

- [ ] **Step 2: Run backend focused verification**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=McpSchemaMigrationTests,McpRepositoryTests,McpServerConfigServiceTests,McpToolConfigServiceTests,McpToolPolicyServiceTests,McpRuntimeRegistryTests,ReactAgentChatServiceTests,ReactAgentDynamicRefreshTests,AgentConfigurationTests,AgentMcpRbacResourceProviderTests,RbacAuthApplicationTests test
```

Expected: PASS.

- [ ] **Step 3: Run frontend focused verification**

Run:

```bash
npm test -- --run src/layouts/AdminLayout/menu.test.tsx src/layouts/AdminLayout/permissionImpact.test.ts src/modules/agent/mcp/mcpService.test.ts
npm run build
```

Expected: PASS.

- [ ] **Step 4: Run backend compile**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document dynamic mcp management"
```

---

## Self-Review

### Spec coverage

- Frontend writes MCP config: covered by Tasks 7 and 8.
- DB stores server/tool/policy: covered by Tasks 1, 2, and 3.
- Supports remote Streamable HTTP and SSE: covered by Task 4.
- Supports enable/disable: covered by Tasks 3, 6, and 8.
- Supports tool discovery: covered by Tasks 4, 6, and 8.
- ReactAgent dynamic integration: covered by Task 5.
- Existing RBAC framework integration: covered by Task 6 and frontend permission tasks.
- `SUPER_ADMIN` all permissions: preserved by existing `RbacAdminBootstrap.grantAllPermissions`; providers do not
  declare `SUPER_ADMIN`, matching synchronizer constraints.
- New users get MCP permissions but not audit log page permission: covered by Task 6 registration and provider tests.

### Placeholder scan

The plan contains no `TBD`, no deferred implementation slots, and every task has exact files, commands, and expected
results.

### Type consistency

Backend enum names, DTO field names, frontend types, service names, API paths, RBAC permission codes, and route paths
are consistent across tasks.
