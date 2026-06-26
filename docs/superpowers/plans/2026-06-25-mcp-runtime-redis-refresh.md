# MCP Runtime Redis Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCP server/tool policy changes refresh Agent-visible MCP tools through Redis Pub/Sub plus local refresh
fallback.

**Architecture:** Add a focused MCP runtime refresh boundary: message/properties/identity/broadcaster publish refresh
events, subscriber consumes remote events, and coordinator applies local refresh before broadcasting. MCP admin
controllers call the coordinator after DB writes commit, while `McpRuntimeRegistry`, `AgentPresetServiceImpl`, and
`DefaultAgentRuntimeFactory` keep their current callback filtering model.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring WebFlux, Spring Data Redis Pub/Sub, Spring Data JPA, Jackson, Lombok,
JUnit 5, Mockito, AssertJ, Maven.

---

## Scope Check

The approved spec covers one backend subsystem: MCP runtime refresh propagation. It does not require a database
migration, UI rewrite, MCP SDK change, or project startup. This plan keeps the work in the existing
`top.egon.mario.agent.mcp` backend package and focused tests.

Primary spec:

- `docs/superpowers/specs/2026-06-25-mcp-runtime-redis-refresh-design.md`

## File Structure

Backend files to create:

- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshMessage.java`: Redis Pub/Sub payload and event
  type.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshProperties.java`: topic and broadcast enablement
  properties.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeInstanceIdentity.java`: per-node source id used to ignore
  local loopback messages.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcaster.java`: serializes and publishes
  refresh events through Redis.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinator.java`: Facade for local runtime
  refresh plus broadcast, and remote event application.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriber.java`: Redis message listener that
  applies remote events locally.
- `be/src/main/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfiguration.java`: listener container
  registration gated by `agent.mcp.runtime.enabled`.
- `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcasterTests.java`: broadcaster behavior.
- `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinatorTests.java`: local and remote
  coordinator behavior.
- `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriberTests.java`: listener parsing,
  local-source skip, and invalid-message tolerance.
- `be/src/test/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfigurationTests.java`: listener configuration
  defaults and disabled runtime behavior.

Backend files to modify:

- `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java`: call coordinator instead of
  `DynamicMcpClientManager`.
- `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java`: call coordinator instead of
  `McpAgentRefreshService`.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolConfigService.java`: return `McpToolResponse` from
  enable/disable so controllers know the server id.
- `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java`: remove no-op refresh marker
  dependency.
- `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java`: delete after all production/test
  references are removed.
- `be/src/main/resources/application.yaml`: add explicit top-level `agent.mcp.runtime.refresh` settings.
- `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpServerControllerTests.java`: verify coordinator trigger
  reasons.
- `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpToolControllerTests.java`: add WebFlux controller coverage for
  tool policy/enable/disable triggers.
- `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolConfigServiceTests.java`: assert enable/disable return
  response with server id.
- `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryServiceTests.java`: remove
  `McpAgentRefreshService.version()` assertions and constructor dependency.
- `be/src/test/java/top/egon/mario/agent/service/impl/AgentPresetServiceTests.java`: keep existing MCP tool-name
  coverage; run it in the final verification set.
- `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`: keep existing current MCP snapshot
  coverage; run it in the final verification set.

## Shared Constants

Use these reason strings exactly:

```java
"server_update"
        "server_enable"
        "server_disable"
        "server_delete"
        "tool_policy_update"
        "tool_enable"
        "tool_disable"
        "tool_discover"
        "startup_or_manual"
```

Use this Redis topic default exactly:

```text
agent:mcp:runtime:refresh
```

---

### Task 1: Add MCP Runtime Refresh Message, Properties, Identity, And Broadcaster

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshMessage.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshProperties.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeInstanceIdentity.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcaster.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcasterTests.java`

- [ ] **Step 1: Write failing broadcaster tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcasterTests.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis Pub/Sub messages for MCP runtime refresh.
 */
class McpRuntimeRefreshBroadcasterTests {

    @Test
    void publishesServerRefreshMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeInstanceIdentity instanceIdentity = new McpRuntimeInstanceIdentity();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"), instanceIdentity);
        var payloadCaptor = forClass(String.class);

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.sourceInstanceId()).isEqualTo(instanceIdentity.sourceInstanceId());
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.SERVER_REFRESH);
        assertThat(message.serverId()).isEqualTo(9L);
        assertThat(message.reason()).isEqualTo("tool_enable");
        assertThat(message.createdAt()).isNotNull();
    }

    @Test
    void publishesServerDisableMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());
        var payloadCaptor = forClass(String.class);

        broadcaster.publishServerDisable(9L, "server_disable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.SERVER_DISABLE);
        assertThat(message.serverId()).isEqualTo(9L);
        assertThat(message.reason()).isEqualTo("server_disable");
    }

    @Test
    void publishesAllRefreshMessageWhenBroadcastIsEnabled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                objectMapper, new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());
        var payloadCaptor = forClass(String.class);

        broadcaster.publishAllRefresh("startup_or_manual");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), payloadCaptor.capture());
        McpRuntimeRefreshMessage message = objectMapper.readValue(payloadCaptor.getValue(),
                McpRuntimeRefreshMessage.class);
        assertThat(message.eventType()).isEqualTo(McpRuntimeRefreshMessage.EventType.ALL_REFRESH);
        assertThat(message.serverId()).isNull();
        assertThat(message.reason()).isEqualTo("startup_or_manual");
    }

    @Test
    void skipsPublishingWhenBroadcastIsDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new McpRuntimeRefreshProperties(false, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate, never()).convertAndSend(eq("agent:mcp:runtime:refresh"), anyString());
    }

    @Test
    void publishFailureDoesNotEscape() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(anyString(), anyString());
        McpRuntimeRefreshBroadcaster broadcaster = new McpRuntimeRefreshBroadcaster(redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new McpRuntimeRefreshProperties(true, "agent:mcp:runtime:refresh"),
                new McpRuntimeInstanceIdentity());

        broadcaster.publishServerRefresh(9L, "tool_enable");

        verify(redisTemplate).convertAndSend(eq("agent:mcp:runtime:refresh"), anyString());
    }

}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshBroadcasterTests test
```

Expected: FAIL because `McpRuntimeRefreshBroadcaster`, `McpRuntimeRefreshMessage`, `McpRuntimeRefreshProperties`, and
`McpRuntimeInstanceIdentity` do not exist.

- [ ] **Step 3: Add message, properties, and identity**

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshMessage.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import java.time.Instant;

/**
 * Redis Pub/Sub payload for cross-node MCP runtime refresh.
 */
public record McpRuntimeRefreshMessage(
        String sourceInstanceId,
        EventType eventType,
        Long serverId,
        String reason,
        Instant createdAt
) {

    public static McpRuntimeRefreshMessage serverRefresh(String sourceInstanceId, Long serverId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.SERVER_REFRESH, serverId, reason,
                Instant.now());
    }

    public static McpRuntimeRefreshMessage serverDisable(String sourceInstanceId, Long serverId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.SERVER_DISABLE, serverId, reason,
                Instant.now());
    }

    public static McpRuntimeRefreshMessage allRefresh(String sourceInstanceId, String reason) {
        return new McpRuntimeRefreshMessage(sourceInstanceId, EventType.ALL_REFRESH, null, reason, Instant.now());
    }

    public enum EventType {
        SERVER_REFRESH,
        SERVER_DISABLE,
        ALL_REFRESH
    }

}
```

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshProperties.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Pub/Sub settings for MCP runtime refresh broadcasts.
 */
@ConfigurationProperties(prefix = "agent.mcp.runtime.refresh")
public record McpRuntimeRefreshProperties(
        Boolean broadcastEnabled,
        String broadcastTopic
) {

    public McpRuntimeRefreshProperties {
        broadcastEnabled = broadcastEnabled == null || broadcastEnabled;
        broadcastTopic = broadcastTopic == null || broadcastTopic.isBlank()
                ? "agent:mcp:runtime:refresh"
                : broadcastTopic;
    }

}
```

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeInstanceIdentity.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identifies the current application instance in MCP runtime refresh broadcasts.
 */
@Component
public class McpRuntimeInstanceIdentity {

    private final String sourceInstanceId = UUID.randomUUID().toString();

    public String sourceInstanceId() {
        return sourceInstanceId;
    }

    public boolean isLocalSource(String sourceInstanceId) {
        return this.sourceInstanceId.equals(sourceInstanceId);
    }

}
```

- [ ] **Step 4: Add broadcaster implementation**

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcaster.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

/**
 * Publishes MCP runtime refresh messages through Redis Pub/Sub.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpRuntimeRefreshBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final McpRuntimeRefreshProperties refreshProperties;
    private final McpRuntimeInstanceIdentity instanceIdentity;

    public void publishServerRefresh(Long serverId, String reason) {
        publish(McpRuntimeRefreshMessage.serverRefresh(instanceIdentity.sourceInstanceId(), serverId, reason));
    }

    public void publishServerDisable(Long serverId, String reason) {
        publish(McpRuntimeRefreshMessage.serverDisable(instanceIdentity.sourceInstanceId(), serverId, reason));
    }

    public void publishAllRefresh(String reason) {
        publish(McpRuntimeRefreshMessage.allRefresh(instanceIdentity.sourceInstanceId(), reason));
    }

    private void publish(McpRuntimeRefreshMessage message) {
        if (!Boolean.TRUE.equals(refreshProperties.broadcastEnabled())) {
            return;
        }
        try {
            redisTemplate.convertAndSend(refreshProperties.broadcastTopic(), objectMapper.writeValueAsString(message));
            LogUtil.debug(log).log("mcp runtime refresh broadcast published, eventType={}, serverId={}, reason={}",
                    message.eventType(), message.serverId(), message.reason());
        } catch (JsonProcessingException e) {
            LogUtil.warn(log).log("serialize mcp runtime refresh broadcast failed, eventType={}, serverId={}",
                    message.eventType(), message.serverId(), e);
        } catch (RuntimeException e) {
            LogUtil.warn(log).log("publish mcp runtime refresh broadcast failed, eventType={}, serverId={}",
                    message.eventType(), message.serverId(), e);
        }
    }

}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshBroadcasterTests test
```

Expected: PASS for all tests in `McpRuntimeRefreshBroadcasterTests`.

- [ ] **Step 6: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshMessage.java \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshProperties.java \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeInstanceIdentity.java \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcaster.java \
  be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshBroadcasterTests.java
git commit -m "feat: add mcp runtime refresh broadcaster"
```

### Task 2: Add Coordinator, Subscriber, And Redis Listener Configuration

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinator.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriber.java`
- Create: `be/src/main/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfiguration.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinatorTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriberTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfigurationTests.java`

- [ ] **Step 1: Write failing coordinator tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinatorTests.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies MCP runtime refresh coordination between local state and Redis broadcasts.
 */
class McpRuntimeRefreshCoordinatorTests {

    @Test
    void refreshServerAppliesLocalRefreshBeforeBroadcasting() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.refreshServer(9L, "tool_enable");

        var ordered = org.mockito.Mockito.inOrder(clientManager, broadcaster);
        ordered.verify(clientManager).refreshServer(9L);
        ordered.verify(broadcaster).publishServerRefresh(9L, "tool_enable");
    }

    @Test
    void disableServerAppliesLocalDisableBeforeBroadcasting() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.disableServer(9L, "server_disable");

        var ordered = org.mockito.Mockito.inOrder(clientManager, broadcaster);
        ordered.verify(clientManager).disableServer(9L);
        ordered.verify(broadcaster).publishServerDisable(9L, "server_disable");
    }

    @Test
    void refreshAllAppliesLocalReloadBeforeBroadcasting() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.refreshAll("startup_or_manual");

        var ordered = org.mockito.Mockito.inOrder(clientManager, broadcaster);
        ordered.verify(clientManager).reloadEnabledServers();
        ordered.verify(broadcaster).publishAllRefresh("startup_or_manual");
    }

    @Test
    void remoteServerRefreshOnlyAppliesLocalRefresh() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.applyRemote(McpRuntimeRefreshMessage.serverRefresh("node-1", 9L, "tool_enable"));

        verify(clientManager).refreshServer(9L);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void remoteServerDisableOnlyAppliesLocalDisable() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.applyRemote(McpRuntimeRefreshMessage.serverDisable("node-1", 9L, "server_disable"));

        verify(clientManager).disableServer(9L);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void remoteAllRefreshOnlyAppliesLocalReload() {
        DynamicMcpClientManager clientManager = mock(DynamicMcpClientManager.class);
        McpRuntimeRefreshBroadcaster broadcaster = mock(McpRuntimeRefreshBroadcaster.class);
        McpRuntimeRefreshCoordinator coordinator = new McpRuntimeRefreshCoordinator(clientManager, broadcaster);

        coordinator.applyRemote(McpRuntimeRefreshMessage.allRefresh("node-1", "startup_or_manual"));

        verify(clientManager).reloadEnabledServers();
        verifyNoInteractions(broadcaster);
    }

}
```

- [ ] **Step 2: Write failing subscriber tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriberTests.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies Redis Pub/Sub handling for MCP runtime refresh.
 */
class McpRuntimeRefreshSubscriberTests {

    @Test
    void appliesRemoteServerRefreshMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = new McpRuntimeInstanceIdentity();
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper,
                coordinator, instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.serverRefresh("node-1", 9L,
                "tool_enable");
        Message redisMessage = redisMessage(objectMapper.writeValueAsBytes(refreshMessage));

        subscriber.onMessage(redisMessage, "agent:mcp:runtime:refresh".getBytes(StandardCharsets.UTF_8));

        verify(coordinator).applyRemote(refreshMessage);
    }

    @Test
    void skipsMessagePublishedByCurrentInstance() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeInstanceIdentity instanceIdentity = new McpRuntimeInstanceIdentity();
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper,
                coordinator, instanceIdentity);
        McpRuntimeRefreshMessage refreshMessage = McpRuntimeRefreshMessage.serverRefresh(
                instanceIdentity.sourceInstanceId(), 9L, "tool_enable");
        Message redisMessage = redisMessage(objectMapper.writeValueAsBytes(refreshMessage));

        subscriber.onMessage(redisMessage, "agent:mcp:runtime:refresh".getBytes(StandardCharsets.UTF_8));

        verifyNoInteractions(coordinator);
    }

    @Test
    void skipsServerMessageWithoutServerId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(objectMapper,
                coordinator, new McpRuntimeInstanceIdentity());
        McpRuntimeRefreshMessage refreshMessage = new McpRuntimeRefreshMessage("node-1",
                McpRuntimeRefreshMessage.EventType.SERVER_REFRESH, null, "tool_enable", java.time.Instant.now());
        Message redisMessage = redisMessage(objectMapper.writeValueAsBytes(refreshMessage));

        subscriber.onMessage(redisMessage, "agent:mcp:runtime:refresh".getBytes(StandardCharsets.UTF_8));

        verifyNoInteractions(coordinator);
    }

    @Test
    void invalidJsonDoesNotEscape() {
        McpRuntimeRefreshCoordinator coordinator = mock(McpRuntimeRefreshCoordinator.class);
        McpRuntimeRefreshSubscriber subscriber = new McpRuntimeRefreshSubscriber(
                new ObjectMapper().findAndRegisterModules(), coordinator, new McpRuntimeInstanceIdentity());
        Message redisMessage = redisMessage("{not-json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(redisMessage, "agent:mcp:runtime:refresh".getBytes(StandardCharsets.UTF_8));

        verifyNoInteractions(coordinator);
    }

    private static Message redisMessage(byte[] body) {
        Message message = mock(Message.class);
        given(message.getBody()).willReturn(body);
        return message;
    }

}
```

- [ ] **Step 3: Write failing configuration tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfigurationTests.java`:

```java
package top.egon.mario.agent.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import top.egon.mario.agent.mcp.runtime.DynamicMcpClientManager;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshBroadcaster;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshConfiguration;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshProperties;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies MCP runtime refresh listener configuration.
 */
class McpRuntimeRefreshConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpRuntimeRefreshConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(DynamicMcpClientManager.class, () -> mock(DynamicMcpClientManager.class));

    @Test
    void registersRefreshBeansWhenRuntimeIsEnabled() {
        contextRunner
                .withPropertyValues("agent.mcp.runtime.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpRuntimeRefreshProperties.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshBroadcaster.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshCoordinator.class);
                    assertThat(context).hasSingleBean(McpRuntimeRefreshSubscriber.class);
                    assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
                    assertThat(context.getBean(McpRuntimeRefreshProperties.class).broadcastTopic())
                            .isEqualTo("agent:mcp:runtime:refresh");
                });
    }

    @Test
    void doesNotRegisterListenerBeansWhenRuntimeIsDisabled() {
        contextRunner
                .withPropertyValues("agent.mcp.runtime.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshBroadcaster.class);
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshCoordinator.class);
                    assertThat(context).doesNotHaveBean(McpRuntimeRefreshSubscriber.class);
                    assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                });
    }

}
```

If the import for `McpRuntimeRefreshConfiguration` points at `top.egon.mario.agent.mcp.runtime`, correct it to
`top.egon.mario.agent.mcp.config.McpRuntimeRefreshConfiguration` before running the test:

```java
import top.egon.mario.agent.mcp.config.McpRuntimeRefreshConfiguration;
```

- [ ] **Step 4: Run tests to verify they fail**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshCoordinatorTests,McpRuntimeRefreshSubscriberTests,McpRuntimeRefreshConfigurationTests test
```

Expected: FAIL because coordinator, subscriber, and configuration classes do not exist.

- [ ] **Step 5: Add coordinator implementation**

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinator.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Coordinates local MCP runtime refresh and cross-node refresh broadcasts.
 */
@Component
@RequiredArgsConstructor
public class McpRuntimeRefreshCoordinator {

    private final DynamicMcpClientManager clientManager;
    private final McpRuntimeRefreshBroadcaster broadcaster;

    public void refreshServer(Long serverId, String reason) {
        clientManager.refreshServer(serverId);
        broadcaster.publishServerRefresh(serverId, reason);
    }

    public void disableServer(Long serverId, String reason) {
        clientManager.disableServer(serverId);
        broadcaster.publishServerDisable(serverId, reason);
    }

    public void refreshAll(String reason) {
        clientManager.reloadEnabledServers();
        broadcaster.publishAllRefresh(reason);
    }

    public void applyRemote(McpRuntimeRefreshMessage message) {
        if (message.eventType() == McpRuntimeRefreshMessage.EventType.SERVER_REFRESH) {
            clientManager.refreshServer(message.serverId());
        } else if (message.eventType() == McpRuntimeRefreshMessage.EventType.SERVER_DISABLE) {
            clientManager.disableServer(message.serverId());
        } else if (message.eventType() == McpRuntimeRefreshMessage.EventType.ALL_REFRESH) {
            clientManager.reloadEnabledServers();
        }
    }

}
```

- [ ] **Step 6: Add subscriber implementation**

Create `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriber.java`:

```java
package top.egon.mario.agent.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.io.IOException;

/**
 * Handles Redis Pub/Sub messages by refreshing only local MCP runtime clients.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpRuntimeRefreshSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final McpRuntimeRefreshCoordinator coordinator;
    private final McpRuntimeInstanceIdentity instanceIdentity;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            McpRuntimeRefreshMessage refreshMessage = objectMapper.readValue(message.getBody(),
                    McpRuntimeRefreshMessage.class);
            if (instanceIdentity.isLocalSource(refreshMessage.sourceInstanceId())) {
                LogUtil.debug(log).log("mcp runtime refresh broadcast skipped, reason=local_source, eventType={}, serverId={}",
                        refreshMessage.eventType(), refreshMessage.serverId());
                return;
            }
            if (isInvalid(refreshMessage)) {
                LogUtil.warn(log).log("mcp runtime refresh broadcast skipped, reason=invalid_message, eventType={}, serverId={}",
                        refreshMessage.eventType(), refreshMessage.serverId());
                return;
            }
            coordinator.applyRemote(refreshMessage);
            LogUtil.info(log).log("mcp runtime refresh broadcast received, eventType={}, serverId={}, reason={}",
                    refreshMessage.eventType(), refreshMessage.serverId(), refreshMessage.reason());
        } catch (IOException | RuntimeException e) {
            LogUtil.warn(log).log("handle mcp runtime refresh broadcast failed", e);
        }
    }

    private boolean isInvalid(McpRuntimeRefreshMessage message) {
        if (message.eventType() == null) {
            return true;
        }
        return message.eventType() != McpRuntimeRefreshMessage.EventType.ALL_REFRESH && message.serverId() == null;
    }

}
```

- [ ] **Step 7: Add Redis listener configuration**

Create `be/src/main/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfiguration.java`:

```java
package top.egon.mario.agent.mcp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshProperties;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshSubscriber;

/**
 * Registers Redis Pub/Sub listener for cross-node MCP runtime refresh.
 */
@Configuration
@EnableConfigurationProperties(McpRuntimeRefreshProperties.class)
@ConditionalOnProperty(prefix = "agent.mcp.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpRuntimeRefreshConfiguration {

    @Bean
    public RedisMessageListenerContainer mcpRuntimeRefreshMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            McpRuntimeRefreshProperties refreshProperties,
            McpRuntimeRefreshSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        if (Boolean.TRUE.equals(refreshProperties.broadcastEnabled())) {
            container.addMessageListener(subscriber, new ChannelTopic(refreshProperties.broadcastTopic()));
        }
        return container;
    }

}
```

- [ ] **Step 8: Run tests to verify they pass**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshCoordinatorTests,McpRuntimeRefreshSubscriberTests,McpRuntimeRefreshConfigurationTests test
```

Expected: PASS for all coordinator, subscriber, and configuration tests.

- [ ] **Step 9: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinator.java \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriber.java \
  be/src/main/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfiguration.java \
  be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshCoordinatorTests.java \
  be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRefreshSubscriberTests.java \
  be/src/test/java/top/egon/mario/agent/mcp/config/McpRuntimeRefreshConfigurationTests.java
git commit -m "feat: add mcp runtime refresh listener"
```

### Task 3: Wire Server Admin Endpoints To The Refresh Coordinator

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpServerControllerTests.java`

- [ ] **Step 1: Update failing controller tests**

Modify `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpServerControllerTests.java`:

```java
package top.egon.mario.agent.mcp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpServerConfigService;
import top.egon.mario.agent.mcp.service.McpToolDiscoveryService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Verifies admin MCP server management endpoints.
 */
@WebFluxTest(controllers = AdminMcpServerController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminMcpServerControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private McpServerConfigService serverConfigService;

    @MockitoBean
    private McpToolDiscoveryService toolDiscoveryService;

    @MockitoBean
    private McpRuntimeRefreshCoordinator refreshCoordinator;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void updateRefreshesRuntimeServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();
        given(serverConfigService.update(eq(9L), any(), any())).willReturn(response());

        webTestClient.put()
                .uri("/api/admin/agent/mcp/servers/9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "serverName":"Search MCP",
                          "transportType":"SSE",
                          "baseUrl":"https://mcp.example.com",
                          "endpoint":"/sse",
                          "connectTimeoutMs":3000,
                          "requestTimeoutMs":10000
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(9)
                .jsonPath("$.data.serverName").isEqualTo("Search MCP");

        verify(refreshCoordinator).refreshServer(9L, "server_update");
    }

    @Test
    void enableRefreshesRuntimeServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/enable")
                .exchange()
                .expectStatus().isOk();

        verify(serverConfigService).enable(eq(9L), any());
        verify(refreshCoordinator).refreshServer(9L, "server_enable");
    }

    @Test
    void disableRemovesRuntimeServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/disable")
                .exchange()
                .expectStatus().isOk();

        verify(serverConfigService).disable(eq(9L), any());
        verify(refreshCoordinator).disableServer(9L, "server_disable");
    }

    @Test
    void deleteRemovesRuntimeServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();

        webTestClient.delete()
                .uri("/api/admin/agent/mcp/servers/9")
                .exchange()
                .expectStatus().isOk();

        verify(serverConfigService).delete(eq(9L), any());
        verify(refreshCoordinator).disableServer(9L, "server_delete");
    }

    @Test
    void discoverToolsRefreshesRuntimeServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();
        given(toolDiscoveryService.discover(eq(9L), any()))
                .willReturn(new McpToolDiscoveryResponse(9L, 3, 2, 1));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/discover-tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.serverId").isEqualTo(9)
                .jsonPath("$.data.discoveredCount").isEqualTo(3);

        verify(refreshCoordinator).refreshServer(9L, "tool_discover");
    }

    private void configureImmediateScheduler() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
    }

    private McpServerResponse response() {
        return new McpServerResponse(9L, "search", "Search MCP", McpTransportType.SSE,
                "https://mcp.example.com", "/sse", Map.of(), true, 3000, 10000,
                McpServerStatus.CONNECTED, null, Instant.parse("2026-06-14T01:00:00Z"),
                Instant.parse("2026-06-14T00:00:00Z"), Instant.parse("2026-06-14T01:00:00Z"));
    }

}
```

- [ ] **Step 2: Run server controller tests to verify they fail**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=AdminMcpServerControllerTests test
```

Expected: FAIL because `AdminMcpServerController` still injects `DynamicMcpClientManager`.

- [ ] **Step 3: Update server controller implementation**

Modify `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java` imports and field:

```java
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
```

```java
private final McpRuntimeRefreshCoordinator refreshCoordinator;
```

Replace the write endpoints with:

```java
@PutMapping("/{id}")
public Mono<ApiResponse<McpServerResponse>> update(@PathVariable @Min(1) Long id,
                                                   @Valid @RequestBody UpdateMcpServerRequest request,
                                                   @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> {
        McpServerResponse response = serverConfigService.update(id, request, actorId(principal));
        refreshCoordinator.refreshServer(id, "server_update");
        return response;
    });
}

@PostMapping("/{id}/enable")
public Mono<ApiResponse<Void>> enable(@PathVariable @Min(1) Long id,
                                      @AuthenticationPrincipal RbacPrincipal principal) {
    return blockingVoid(() -> {
        serverConfigService.enable(id, actorId(principal));
        refreshCoordinator.refreshServer(id, "server_enable");
    });
}

@PostMapping("/{id}/disable")
public Mono<ApiResponse<Void>> disable(@PathVariable @Min(1) Long id,
                                       @AuthenticationPrincipal RbacPrincipal principal) {
    return blockingVoid(() -> {
        serverConfigService.disable(id, actorId(principal));
        refreshCoordinator.disableServer(id, "server_disable");
    });
}

@PostMapping("/{id}/discover-tools")
public Mono<ApiResponse<McpToolDiscoveryResponse>> discoverTools(@PathVariable @Min(1) Long id,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> {
        McpToolDiscoveryResponse response = toolDiscoveryService.discover(id, actorId(principal));
        refreshCoordinator.refreshServer(id, "tool_discover");
        return response;
    });
}

@DeleteMapping("/{id}")
public Mono<ApiResponse<Void>> delete(@PathVariable @Min(1) Long id,
                                      @AuthenticationPrincipal RbacPrincipal principal) {
    return blockingVoid(() -> {
        serverConfigService.delete(id, actorId(principal));
        refreshCoordinator.disableServer(id, "server_delete");
    });
}
```

Remove the old import:

```java
import top.egon.mario.agent.mcp.runtime.DynamicMcpClientManager;
```

- [ ] **Step 4: Run server controller tests to verify they pass**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=AdminMcpServerControllerTests test
```

Expected: PASS for all tests in `AdminMcpServerControllerTests`.

- [ ] **Step 5: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpServerController.java \
  be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpServerControllerTests.java
git commit -m "feat: refresh mcp runtime from server admin actions"
```

### Task 4: Wire Tool Policy Endpoints To The Refresh Coordinator

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolConfigService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolConfigServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpToolControllerTests.java`

- [ ] **Step 1: Update service tests so enable/disable return the changed tool response**

Modify the `enableAndDisableToolUpdateFieldsAndActor` test in
`be/src/test/java/top/egon/mario/agent/mcp/service/McpToolConfigServiceTests.java`:

```java
@Test
void enableAndDisableToolUpdateFieldsAndActor() {
    McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
    McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
    McpServerConfigPo server = server("docs", true, McpServerStatus.CONNECTED);
    McpToolConfigPo tool = tool(server.getId(), "docs_search", false);
    given(toolRepository.findByIdAndDeletedFalse(10L)).willReturn(Optional.of(tool));
    given(toolRepository.save(any(McpToolConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(serverRepository.findByIdAndDeletedFalse(server.getId())).willReturn(Optional.of(server));
    McpToolConfigService service = new McpToolConfigService(toolRepository, serverRepository);

    McpToolResponse enabled = service.enable(10L, 7L);

    assertThat(enabled.serverId()).isEqualTo(9L);
    assertThat(enabled.enabled()).isTrue();
    assertThat(enabled.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.AVAILABLE);
    assertThat(tool.isEnabled()).isTrue();
    assertThat(tool.getUpdatedBy()).isEqualTo(7L);

    McpToolResponse disabled = service.disable(10L, 8L);

    assertThat(disabled.serverId()).isEqualTo(9L);
    assertThat(disabled.enabled()).isFalse();
    assertThat(disabled.runtimeStatus()).isEqualTo(McpToolRuntimeStatus.DISABLED);
    assertThat(tool.isEnabled()).isFalse();
    assertThat(tool.getUpdatedBy()).isEqualTo(8L);
}
```

- [ ] **Step 2: Create failing tool controller tests**

Create `be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpToolControllerTests.java`:

```java
package top.egon.mario.agent.mcp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.mcp.dto.response.McpToolResponse;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpToolConfigService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Verifies admin MCP tool policy endpoints.
 */
@WebFluxTest(controllers = AdminMcpToolController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminMcpToolControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private McpToolConfigService toolConfigService;

    @MockitoBean
    private McpRuntimeRefreshCoordinator refreshCoordinator;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void updatePolicyRefreshesParentServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();
        given(toolConfigService.updatePolicy(eq(10L), any(), any())).willReturn(toolResponse(true));

        webTestClient.put()
                .uri("/api/admin/agent/mcp/tools/10/policy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "riskLevel":"LOW",
                          "readonly":true,
                          "requireConfirm":false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(10)
                .jsonPath("$.data.serverId").isEqualTo(9);

        verify(refreshCoordinator).refreshServer(9L, "tool_policy_update");
    }

    @Test
    void enableRefreshesParentServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();
        given(toolConfigService.enable(eq(10L), any())).willReturn(toolResponse(true));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/tools/10/enable")
                .exchange()
                .expectStatus().isOk();

        verify(refreshCoordinator).refreshServer(9L, "tool_enable");
    }

    @Test
    void disableRefreshesParentServerAfterPersistenceSucceeds() {
        configureImmediateScheduler();
        given(toolConfigService.disable(eq(10L), any())).willReturn(toolResponse(false));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/tools/10/disable")
                .exchange()
                .expectStatus().isOk();

        verify(refreshCoordinator).refreshServer(9L, "tool_disable");
    }

    private void configureImmediateScheduler() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
    }

    private McpToolResponse toolResponse(boolean enabled) {
        return new McpToolResponse(10L, 9L, "docs", "search", "docs_search", "docs_search",
                "Search docs", "{\"type\":\"object\"}", enabled, McpToolRiskLevel.LOW, true,
                false, enabled ? McpToolRuntimeStatus.AVAILABLE : McpToolRuntimeStatus.DISABLED,
                Instant.parse("2026-06-14T01:00:00Z"));
    }

}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpToolConfigServiceTests,AdminMcpToolControllerTests test
```

Expected: FAIL because `McpToolConfigService#enable/disable` return `void`, and `AdminMcpToolController` still injects
`McpAgentRefreshService`.

- [ ] **Step 4: Update tool config service enable/disable signatures**

Modify `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolConfigService.java`:

```java
@Transactional
public McpToolResponse enable(Long id, Long actorId) {
    McpToolConfigPo tool = requireTool(id);
    if (tool.isRequireConfirm()) {
        throw new AgentException("AGENT_MCP_TOOL_POLICY_BLOCKED",
                "tool requires confirmation and cannot be enabled for stage one");
    }
    tool.setEnabled(true);
    tool.setUpdatedBy(actorId);
    tool = toolRepository.save(tool);
    return toResponse(tool, requireServer(tool.getServerId()));
}

@Transactional
public McpToolResponse disable(Long id, Long actorId) {
    McpToolConfigPo tool = requireTool(id);
    tool.setEnabled(false);
    tool.setUpdatedBy(actorId);
    tool = toolRepository.save(tool);
    return toResponse(tool, requireServer(tool.getServerId()));
}
```

- [ ] **Step 5: Update tool controller implementation**

Modify `be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java` imports:

```java
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
```

Remove:

```java
import top.egon.mario.agent.mcp.runtime.McpAgentRefreshService;
```

Replace the field:

```java
private final McpRuntimeRefreshCoordinator refreshCoordinator;
```

Replace write endpoints:

```java
@PutMapping("/{id}/policy")
public Mono<ApiResponse<McpToolResponse>> updatePolicy(@PathVariable @Min(1) Long id,
                                                       @Valid @RequestBody UpdateMcpToolPolicyRequest request,
                                                       @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> {
        McpToolResponse response = toolConfigService.updatePolicy(id, request, actorId(principal));
        refreshCoordinator.refreshServer(response.serverId(), "tool_policy_update");
        return response;
    });
}

@PostMapping("/{id}/enable")
public Mono<ApiResponse<Void>> enable(@PathVariable @Min(1) Long id,
                                      @AuthenticationPrincipal RbacPrincipal principal) {
    return blockingVoid(() -> {
        McpToolResponse response = toolConfigService.enable(id, actorId(principal));
        refreshCoordinator.refreshServer(response.serverId(), "tool_enable");
    });
}

@PostMapping("/{id}/disable")
public Mono<ApiResponse<Void>> disable(@PathVariable @Min(1) Long id,
                                       @AuthenticationPrincipal RbacPrincipal principal) {
    return blockingVoid(() -> {
        McpToolResponse response = toolConfigService.disable(id, actorId(principal));
        refreshCoordinator.refreshServer(response.serverId(), "tool_disable");
    });
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpToolConfigServiceTests,AdminMcpToolControllerTests test
```

Expected: PASS for service and tool controller tests.

- [ ] **Step 7: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/agent/mcp/service/McpToolConfigService.java \
  be/src/main/java/top/egon/mario/agent/mcp/web/AdminMcpToolController.java \
  be/src/test/java/top/egon/mario/agent/mcp/service/McpToolConfigServiceTests.java \
  be/src/test/java/top/egon/mario/agent/mcp/web/AdminMcpToolControllerTests.java
git commit -m "feat: refresh mcp runtime from tool policy actions"
```

### Task 5: Remove The No-Op Refresh Marker From Discovery

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java`
- Delete: `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java`
- Modify: `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryServiceTests.java`

- [ ] **Step 1: Update discovery tests to remove version assertions**

Modify `be/src/test/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryServiceTests.java`:

```java
package top.egon.mario.agent.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.mcp.runtime.McpClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies MCP tool discovery handles runtime client edge cases.
 */
class McpToolDiscoveryServiceTests {

    @Test
    void nullListToolsResultIsTreatedAsZeroDiscoveredTools() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willReturn(null);

        McpToolDiscoveryResponse response = support.service.discover(9L, 7L);

        assertThat(response.discoveredCount()).isZero();
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        verifyNoInteractions(support.toolRepository);
    }

    @Test
    void nullToolsListIsTreatedAsZeroDiscoveredTools() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willReturn(new McpSchema.ListToolsResult(null, null));

        McpToolDiscoveryResponse response = support.service.discover(9L, 7L);

        assertThat(response.discoveredCount()).isZero();
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        verifyNoInteractions(support.toolRepository);
    }

    @Test
    void closeFailureDoesNotMaskListToolsFailure() {
        TestSupport support = new TestSupport();
        McpSyncClient client = support.client();
        RuntimeException listFailure = new RuntimeException("list failed");
        given(support.clientFactory.create(support.server)).willReturn(client);
        given(client.listTools()).willThrow(listFailure);
        given(client.closeGracefully()).willThrow(new RuntimeException("close failed"));

        assertThatThrownBy(() -> support.service.discover(9L, 7L))
                .isSameAs(listFailure);
    }

    private static class TestSupport {
        private final McpServerConfigPo server = server();
        private final McpServerConfigService serverConfigService = mock(McpServerConfigService.class);
        private final McpToolConfigRepository toolRepository = mock(McpToolConfigRepository.class);
        private final McpClientFactory clientFactory = mock(McpClientFactory.class);
        private final McpToolDiscoveryService service = new McpToolDiscoveryService(
                serverConfigService,
                toolRepository,
                clientFactory,
                new ObjectMapper());

        TestSupport() {
            given(serverConfigService.requireServer(9L)).willReturn(server);
        }

        private McpSyncClient client() {
            return mock(McpSyncClient.class);
        }

        private static McpServerConfigPo server() {
            McpServerConfigPo server = new McpServerConfigPo();
            server.setId(9L);
            server.setServerCode("docs");
            server.setServerName("Docs MCP");
            server.setTransportType(McpTransportType.STREAMABLE_HTTP);
            server.setBaseUrl("https://example.com");
            server.setEndpoint("/mcp");
            server.setEnabled(true);
            server.setStatus(McpServerStatus.CONNECTED);
            return server;
        }
    }

}
```

- [ ] **Step 2: Run discovery tests to verify they fail**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpToolDiscoveryServiceTests test
```

Expected: FAIL because `McpToolDiscoveryService` still requires `McpAgentRefreshService` in its constructor.

- [ ] **Step 3: Remove refresh marker from discovery service**

Modify `be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java`:

Remove import:

```java
import top.egon.mario.agent.mcp.runtime.McpAgentRefreshService;
```

Remove field:

```java
private final McpAgentRefreshService refreshService;
```

Remove this line from `discover`:

```java
refreshService.refresh();
```

The constructor will be generated by Lombok with the remaining fields:

```java
private final McpServerConfigService serverConfigService;
private final McpToolConfigRepository toolRepository;
private final McpClientFactory clientFactory;
private final ObjectMapper objectMapper;
```

- [ ] **Step 4: Delete the unused refresh marker**

Delete `be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java`.

Then run:

```bash
rg -n "McpAgentRefreshService|refreshService\\.refresh|refreshService\\.version" be/src/main/java be/src/test/java
```

Expected: no output.

- [ ] **Step 5: Run discovery tests to verify they pass**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpToolDiscoveryServiceTests test
```

Expected: PASS for all tests in `McpToolDiscoveryServiceTests`.

- [ ] **Step 6: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryService.java \
  be/src/main/java/top/egon/mario/agent/mcp/runtime/McpAgentRefreshService.java \
  be/src/test/java/top/egon/mario/agent/mcp/service/McpToolDiscoveryServiceTests.java
git commit -m "refactor: remove unused mcp refresh marker"
```

### Task 6: Add Runtime Visibility Regression Coverage

**Files:**

- Modify: `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistryTests.java`
- Verify existing: `be/src/test/java/top/egon/mario/agent/service/impl/AgentPresetServiceTests.java`
- Verify existing: `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`

- [ ] **Step 1: Add a registry regression for tool visibility after client refresh**

Add this test to `be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistryTests.java`:

```java
@Test
void registryReflectsCurrentClientManagerSnapshotAcrossCalls() {
    TestSupport support = new TestSupport();
    McpServerConfigPo server = server(9L, true, McpServerStatus.CONNECTED);
    McpToolConfigPo tool = tool(9L, "search", "docs_search", true, false);
    McpSyncClient firstClient = support.clientWithTools(runtimeTool("other"));
    McpSyncClient refreshedClient = support.clientWithTools(runtimeTool("search"));
    given(support.toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()).willReturn(List.of(tool));
    given(support.serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
    given(support.clientManager.client(9L)).willReturn(Optional.of(firstClient), Optional.of(refreshedClient));

    ToolCallback[] beforeRefresh = support.registry.currentToolCallbacks();
    ToolCallback[] afterRefresh = support.registry.currentToolCallbacks();

    assertThat(beforeRefresh).isEmpty();
    assertThat(afterRefresh).hasSize(1);
    assertThat(afterRefresh[0].getToolDefinition().name()).isEqualTo("docs_search");
}
```

- [ ] **Step 2: Run focused runtime tests**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRegistryTests,AgentPresetServiceTests,AgentRuntimeFactoryTests test
```

Expected: PASS. These tests prove:

- `McpRuntimeRegistry` reads the current client manager snapshot on each call.
- `AgentPresetServiceTests#defaultRuntimeSpecIncludesCurrentMcpToolNames` keeps default `/chat` tool names current.
- `AgentRuntimeFactoryTests#getIncludesCurrentMcpToolSnapshotForEachCall` keeps runtime construction tied to the current
  MCP provider snapshot.

- [ ] **Step 3: Commit**

```bash
git add be/src/test/java/top/egon/mario/agent/mcp/runtime/McpRuntimeRegistryTests.java
git commit -m "test: cover refreshed mcp runtime visibility"
```

### Task 7: Add Application Configuration Defaults

**Files:**

- Modify: `be/src/main/resources/application.yaml`
- Verify existing: `be/src/test/resources/application.yaml`

- [ ] **Step 1: Add top-level MCP runtime refresh config**

Modify `be/src/main/resources/application.yaml` by adding this top-level block before the existing `mario:` block:

```yaml
agent:
  mcp:
    runtime:
      enabled: ${AGENT_MCP_RUNTIME_ENABLED:true}
      refresh:
        broadcast-enabled: ${AGENT_MCP_RUNTIME_REFRESH_BROADCAST_ENABLED:true}
        broadcast-topic: ${AGENT_MCP_RUNTIME_REFRESH_BROADCAST_TOPIC:agent:mcp:runtime:refresh}
```

Do not move the existing `mario:` block. Do not edit Redis credentials or unrelated values.

- [ ] **Step 2: Keep test runtime disabled**

Inspect `be/src/test/resources/application.yaml` and keep this existing block unchanged:

```yaml
agent:
  mcp:
    runtime:
      enabled: false
```

This preserves fast test contexts by preventing automatic Redis listener startup in general tests.

- [ ] **Step 3: Run configuration and YAML syntax checks**

Run:

```bash
rg -n "^agent:|mcp:|runtime:|refresh:" be/src/main/resources/application.yaml be/src/test/resources/application.yaml
git diff --check -- be/src/main/resources/application.yaml be/src/test/resources/application.yaml
```

Expected:

- `be/src/main/resources/application.yaml` shows the new `agent.mcp.runtime.refresh` values.
- `be/src/test/resources/application.yaml` still shows `agent.mcp.runtime.enabled: false`.
- `git diff --check` exits 0.

- [ ] **Step 4: Commit**

```bash
git add be/src/main/resources/application.yaml
git commit -m "config: add mcp runtime refresh settings"
```

### Task 8: Final Focused Verification

**Files:**

- Verify only; no planned file changes.

- [ ] **Step 1: Run all focused MCP refresh tests**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshBroadcasterTests,McpRuntimeRefreshCoordinatorTests,McpRuntimeRefreshSubscriberTests,McpRuntimeRefreshConfigurationTests,AdminMcpServerControllerTests,AdminMcpToolControllerTests,McpToolConfigServiceTests,McpToolDiscoveryServiceTests,McpRuntimeRegistryTests,AgentPresetServiceTests,AgentRuntimeFactoryTests \
  test
```

Expected: PASS.

- [ ] **Step 2: Run backend compile**

Run:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Run repository hygiene checks**

Run:

```bash
git diff --check
git status --short
```

Expected:

- `git diff --check` exits 0.
- `git status --short` only shows files intentionally modified after the last task commit. If every task was committed,
  it shows no output.

- [ ] **Step 4: Record final validation results**

In the final implementation response, include:

```text
Validation:
- be focused Maven tests: PASS
- be compile: PASS
- git diff --check: PASS
```

If a command fails because of unrelated existing workspace drift, include the command, the failing class or error line,
and why it is outside this MCP refresh scope.

---

## Self-Review Notes

- Spec coverage: Tasks 1-2 implement Redis Pub/Sub message, broadcaster, subscriber, coordinator, and listener
  configuration. Tasks 3-5 wire all MCP server/tool/discovery trigger points and remove the no-op refresh marker. Task 6
  covers `/chat` visibility through existing runtime seams. Task 7 adds explicit config defaults. Task 8 verifies the
  focused surface.
- Scope: No database migration, UI rewrite, MCP SDK change, or runtime startup is included.
- Type consistency: The plan consistently uses `McpRuntimeRefreshMessage.EventType`,
  `McpRuntimeRefreshCoordinator#refreshServer`, `#disableServer`, `#refreshAll`, and `#applyRemote`.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-25-mcp-runtime-redis-refresh.md`. Execute with one of these
approaches:

1. **Subagent-Driven (recommended)** - use `superpowers:subagent-driven-development`, dispatch a fresh subagent per
   task, review between tasks, and commit each task separately.
2. **Inline Execution** - use `superpowers:executing-plans`, execute the tasks in this session, and use the checkboxes
   as checkpoints.

Do not start the project during implementation; validation should use the Maven commands listed in Task 8.
