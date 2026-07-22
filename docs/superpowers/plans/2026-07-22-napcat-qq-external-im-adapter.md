# NapCat QQ External IM Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated NapCat OneBot 11 group/private plain-text receive and send support to CyberMario's existing External IM pipeline.

**Architecture:** Keep the current Adapter/Port strategy boundary. NapCat pushes authenticated HTTP events into the common webhook; `QqExternalChatAdapter` normalizes only group/private message events, and `QqExternalChatReplyPort` routes normalized prefixed conversation IDs to the matching OneBot send endpoint. ChatService, Guard/Graph, persistence schema, memory direction, and the built-in IM module remain untouched.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring WebFlux `WebClient`, Jackson, JUnit 5, AssertJ, Mockito, Reactor Netty, Maven.

## Global Constraints

- Implement only group and private plain-text QQ messages; all non-text modalities remain unsupported.
- Use NapCat HTTP Client push inbound and NapCat HTTP Server APIs outbound; do not add WebSocket or polling code.
- Authenticate both directions with `Authorization: Bearer <access-token>` and never place the token in URLs, bodies, or logs.
- Reuse `POST /api/external-im/webhooks/qq/{connectorId}`, durable event persistence, Memory Space binding, Chat Guard, Chat Agent, and reply retry behavior.
- Use `group:<group_id>` and `private:<user_id>` as normalized QQ conversation IDs.
- Do not modify ChatService, the Guard -> Chat Agent graph, agent tools, SoulMD, memory rules, CyberMario's built-in IM module, or Flyway migrations.
- Do not start the application; verification is test-only.

---

### Task 1: Allow authenticated adapters to drop irrelevant webhook events

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/externalim/adapter/ExternalChatInboundAdapter.java`
- Modify: `be/src/main/java/top/egon/mario/agent/externalim/adapter/telegram/TelegramExternalChatAdapter.java`
- Modify: `be/src/main/java/top/egon/mario/agent/externalim/web/ExternalChatWebhookController.java`
- Modify: `be/src/test/java/top/egon/mario/agent/externalim/adapter/telegram/TelegramExternalChatAdapterTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/externalim/web/ExternalChatWebhookControllerTests.java`

**Interfaces:**

- Produces: `Optional<ExternalChatMessage> ExternalChatInboundAdapter.verifyAndNormalize(ExternalWebhookRequest request)`.
- Consumes: existing `ExternalChatIngressService.accept(ExternalChatMessage, String)` only when the optional contains a message.

- [ ] **Step 1: Write the failing ignored-event controller test and adapt existing mock results**

Change existing controller stubs to return `Optional.of(message)`, then add:

```java
@Test
void authenticatedIgnoredEventReturnsNoContentWithoutPersistence() {
    given(registry.requireInbound(ExternalChatPlatform.QQ)).willReturn(adapter);
    given(adapter.verifyAndNormalize(any(ExternalWebhookRequest.class)))
            .willReturn(Optional.empty());

    StepVerifier.create(controller.receive("qq", "main", headers(),
                    Mono.just("{}".getBytes())))
            .assertNext(response -> assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT))
            .verifyComplete();

    verify(ingressService, never()).accept(any(), any());
}
```

Add `java.util.Optional` to the test imports. In `TelegramExternalChatAdapterTests`, unwrap the normalized result:

```java
return adapter.verifyAndNormalize(new ExternalWebhookRequest(
        "main", headers, json.getBytes(StandardCharsets.UTF_8))).orElseThrow();
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
cd be
./mvnw -Dtest=ExternalChatWebhookControllerTests,TelegramExternalChatAdapterTests test
```

Expected: test compilation fails because `verifyAndNormalize` still returns `ExternalChatMessage`.

- [ ] **Step 3: Change the inbound contract and controller**

Replace the inbound interface method with:

```java
Optional<ExternalChatMessage> verifyAndNormalize(ExternalWebhookRequest request);
```

Add `java.util.Optional` to the interface. In the controller use:

```java
var normalized = adapterRegistry.requireInbound(selected)
        .verifyAndNormalize(new ExternalWebhookRequest(connectorId, headers, bytes));
normalized.ifPresent(message -> ingressService.accept(message, TraceContext.resolve(headers)));
return ResponseEntity.noContent().<Void>build();
```

Change Telegram's signature to return `Optional<ExternalChatMessage>`. Wrap both the unsupported-update branch and the normal result with `Optional.of(...)`; keep all current validation and normalization unchanged.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same Maven command. Expected: both test classes pass with no failures.

- [ ] **Step 5: Commit the common contract change**

```bash
git add be/src/main/java/top/egon/mario/agent/externalim/adapter/ExternalChatInboundAdapter.java \
  be/src/main/java/top/egon/mario/agent/externalim/adapter/telegram/TelegramExternalChatAdapter.java \
  be/src/main/java/top/egon/mario/agent/externalim/web/ExternalChatWebhookController.java \
  be/src/test/java/top/egon/mario/agent/externalim/adapter/telegram/TelegramExternalChatAdapterTests.java \
  be/src/test/java/top/egon/mario/agent/externalim/web/ExternalChatWebhookControllerTests.java
git commit -m "refactor(agent): allow ignored external im webhooks"
```

### Task 2: Normalize NapCat group and private text events

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatProperties.java`
- Create: `be/src/main/java/top/egon/mario/agent/externalim/adapter/qq/QqOneBotEvent.java`
- Create: `be/src/main/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatAdapter.java`
- Create: `be/src/test/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatAdapterTests.java`

**Interfaces:**

- Consumes: the optional `ExternalChatInboundAdapter` contract from Task 1.
- Produces: authenticated QQ `ExternalChatMessage` values with prefixed conversation IDs and text-only classification.
- Produces: `QqExternalChatProperties.Connector` with `accessToken`, `botUserId`, and `replyWithQuote` for Task 3.

- [ ] **Step 1: Write the failing QQ inbound tests**

Create `QqExternalChatAdapterTests` with a `main` connector (`access-token`, bot user `10001`) and these cases:

```java
@Test
void normalizesGroupTextAndDetectsOnlyTheConfiguredBotMention() {
    ExternalChatMessage message = normalize("""
            {
              "time": 1784731200,
              "self_id": 10001,
              "post_type": "message",
              "message_type": "group",
              "message_id": 2002,
              "user_id": 30003,
              "group_id": 40004,
              "message": [
                {"type":"at","data":{"qq":"10001"}},
                {"type":"text","data":{"text":" review "}},
                {"type":"at","data":{"qq":"77777"}},
                {"type":"text","data":{"text":" please"}}
              ],
              "sender": {"user_id":30003,"nickname":"Alice","card":"Architect"}
            }
            "").orElseThrow();

    assertThat(message.eventId()).isEqualTo("2002");
    assertThat(message.conversationId()).isEqualTo("group:40004");
    assertThat(message.conversationType()).isEqualTo(ExternalConversationType.GROUP);
    assertThat(message.audienceKey()).isEqualTo("qq:main:group:40004");
    assertThat(message.sender()).isEqualTo(
            new ExternalSender("30003", "Architect", ExternalSenderType.HUMAN));
    assertThat(message.mentionedAgent()).isTrue();
    assertThat(message.repliedToAgentMessage()).isFalse();
    assertThat(message.text()).isEqualTo("review @77777 please");
}

@Test
void normalizesPrivateTextAsADirectConversation() {
    ExternalChatMessage message = normalize("""
            {
              "time": 1784731201,
              "self_id": 10001,
              "post_type": "message",
              "message_type": "private",
              "message_id": 2003,
              "user_id": 30003,
              "message": [{"type":"text","data":{"text":"hello"}}],
              "sender": {"user_id":30003,"nickname":"Alice"}
            }
            "").orElseThrow();

    assertThat(message.conversationId()).isEqualTo("private:30003");
    assertThat(message.conversationType()).isEqualTo(ExternalConversationType.DIRECT);
    assertThat(message.audienceKey()).isEqualTo("qq:main:private:30003");
    assertThat(message.text()).isEqualTo("hello");
}

@Test
void dropsAuthenticatedNonMessageEvents() {
    assertThat(normalize("""
            {"time":1784731202,"self_id":10001,"post_type":"meta_event",
             "meta_event_type":"heartbeat"}
            """)).isEmpty();
}

@Test
void rejectsInvalidBearerTokenAndMismatchedBotAccount() {
    HttpHeaders invalid = new HttpHeaders();
    invalid.setBearerAuth("wrong");
    assertThatThrownBy(() -> adapter.verifyAndNormalize(request(invalid, "{}")))
            .isInstanceOf(ExternalChatException.class)
            .extracting(error -> ((ExternalChatException) error).code())
            .isEqualTo("EXTERNAL_CHAT_SIGNATURE_INVALID");

    assertThatThrownBy(() -> normalize(validPrivateJsonWithSelfId(99999)))
            .isInstanceOf(ExternalChatException.class)
            .extracting(error -> ((ExternalChatException) error).code())
            .isEqualTo("QQ_BOT_ID_MISMATCH");
}

@Test
void auditsSelfMessagesAsBotAndMixedModalMessagesAsUnsupported() {
    ExternalChatMessage self = normalize(groupJson(10001,
            "[{\"type\":\"text\",\"data\":{\"text\":\"echo\"}}]"))
            .orElseThrow();
    assertThat(self.sender().type()).isEqualTo(ExternalSenderType.BOT);

    ExternalChatMessage mixed = normalize(groupJson(30003,
            "[{\"type\":\"text\",\"data\":{\"text\":\"caption\"}},"
                    + "{\"type\":\"image\",\"data\":{\"file\":\"x\"}}]"))
            .orElseThrow();
    assertThat(mixed.messageType()).isEqualTo(ExternalMessageType.UNSUPPORTED);
}
```

Use helpers that always set `Authorization: Bearer access-token` and construct `ExternalWebhookRequest("main", headers, UTF_8 body)`. Keep JSON helpers fully valid and assert the exact error codes above.

- [ ] **Step 2: Run the QQ adapter test and verify RED**

Run:

```bash
cd be
./mvnw -Dtest=QqExternalChatAdapterTests test
```

Expected: test compilation fails because the QQ classes do not exist.

- [ ] **Step 3: Implement QQ configuration and the OneBot DTO**

`QqExternalChatProperties` must follow the existing Telegram property style:

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "mario.agent.external-im.qq")
public class QqExternalChatProperties {
    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:3000";
    private Map<String, Connector> connectors = new LinkedHashMap<>();

    public Connector requireConnector(String connectorId) {
        Connector connector = StringUtils.hasText(connectorId)
                ? connectors.get(connectorId) : null;
        if (!StringUtils.hasText(baseUrl) || connector == null
                || !StringUtils.hasText(connector.getAccessToken())
                || connector.getBotUserId() == null) {
            throw new ExternalChatException("QQ_CONNECTOR_NOT_CONFIGURED",
                    "QQ connector is not configured");
        }
        return connector;
    }

    @Getter
    @Setter
    public static class Connector {
        private String accessToken;
        private Long botUserId;
        private boolean replyWithQuote = true;
    }
}
```

Create package-private Jackson records in `QqOneBotEvent.java`:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
record QqOneBotEvent(
        Long time,
        @JsonProperty("self_id") Long selfId,
        @JsonProperty("post_type") String postType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("group_id") Long groupId,
        List<QqMessageSegment> message,
        QqSender sender) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqMessageSegment(String type, Map<String, Object> data) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqSender(@JsonProperty("user_id") Long userId, String nickname, String card) {
}
```

- [ ] **Step 4: Implement the inbound Adapter strategy**

Create a conditional component for platform `QQ`. It must:

```java
@Override
public Optional<ExternalChatMessage> verifyAndNormalize(ExternalWebhookRequest request) {
    QqExternalChatProperties.Connector connector =
            properties.requireConnector(request.connectorId());
    verifyBearer(connector.getAccessToken(),
            request.headers().getFirst(HttpHeaders.AUTHORIZATION));
    QqOneBotEvent event = read(request.body());
    if (!"message".equals(event.postType())) {
        return Optional.empty();
    }
    requireMessageIdentity(event, connector);
    ExternalConversationType conversationType = conversationType(event.messageType());
    String targetId = conversationType == ExternalConversationType.GROUP
            ? requireGroupId(event) : String.valueOf(event.userId());
    String kind = conversationType == ExternalConversationType.GROUP ? "group" : "private";
    String conversationId = kind + ":" + targetId;
    ParsedText parsed = parseText(event.message(), event.selfId());
    ExternalMessageType messageType = parsed.supported()
            && StringUtils.hasText(parsed.text())
            ? ExternalMessageType.TEXT : ExternalMessageType.UNSUPPORTED;
    ExternalSenderType senderType = event.selfId().equals(event.userId())
            ? ExternalSenderType.BOT : ExternalSenderType.HUMAN;
    ExternalSender sender = new ExternalSender(String.valueOf(event.userId()),
            displayName(event.sender(), event.userId(), conversationType), senderType);
    Instant occurredAt = event.time() == null
            ? Instant.now() : Instant.ofEpochSecond(event.time());
    String messageId = String.valueOf(event.messageId());
    return Optional.of(new ExternalChatMessage(messageId, messageId,
            ExternalChatPlatform.QQ, request.connectorId(), conversationId,
            conversationType, "qq:" + request.connectorId() + ":" + conversationId,
            sender, messageType, parsed.text(), parsed.mentionedAgent(), false, occurredAt));
}
```

Use constant-time comparison after stripping a case-insensitive `Bearer ` prefix. `read` must convert Jackson failures to `QQ_PAYLOAD_INVALID`. Identity validation must throw `QQ_MESSAGE_ID_REQUIRED`, `QQ_SENDER_ID_REQUIRED`, or `QQ_BOT_ID_MISMATCH` without including payload data. Only `group` and `private` are supported; other message types throw `QQ_MESSAGE_TYPE_UNSUPPORTED`.

`parseText` must process segments in order. Concatenate text, remove the configured bot `at`, render other `at` targets as `@<qq>` with single separating spaces, ignore `reply`, and set `supported=false` for every other segment type. Finish with `trim()` and return an immutable private `ParsedText` record.

- [ ] **Step 5: Run QQ inbound tests and verify GREEN**

Run the same Maven command. Expected: all `QqExternalChatAdapterTests` pass.

- [ ] **Step 6: Commit QQ inbound support**

```bash
git add be/src/main/java/top/egon/mario/agent/externalim/adapter/qq \
  be/src/test/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatAdapterTests.java
git commit -m "feat(agent): receive napcat qq text events"
```

### Task 3: Send group and private replies through NapCat

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatReplyPort.java`
- Create: `be/src/test/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatReplyPortTests.java`

**Interfaces:**

- Consumes: existing `ExternalReplyCommand` and the QQ connector properties from Task 2.
- Produces: `ExternalReplyResult` with the OneBot `data.message_id` or a stable terminal/retryable QQ error.

- [ ] **Step 1: Write failing HTTP contract tests**

Use a Reactor Netty server on an ephemeral local port, following `TelegramExternalChatReplyPortTests`. Capture URI, Authorization header, and body. Add tests that prove:

```java
@Test
void sendsQuotedGroupTextWithBearerAuthentication() throws Exception {
    ExternalReplyResult result = port().send(command(
            "group:40004", "2002", "answer"));

    assertThat(result.sent()).isTrue();
    assertThat(result.platformMessageId()).isEqualTo("9009");
    assertThat(requests).singleElement().satisfies(request -> {
        assertThat(request.uri()).isEqualTo("/send_group_msg");
        assertThat(request.authorization()).isEqualTo("Bearer access-token");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("group_id").asText()).isEqualTo("40004");
        assertThat(body.get("message").get(0).get("type").asText()).isEqualTo("reply");
        assertThat(body.get("message").get(0).get("data").get("id").asText())
                .isEqualTo("2002");
        assertThat(body.get("message").get(1).get("data").get("text").asText())
                .isEqualTo("answer");
        assertThat(request.uri() + request.body()).doesNotContain("access-token");
    });
}

@Test
void sendsPrivateTextWithoutAReplySegment() throws Exception {
    ExternalReplyResult result = port().send(command(
            "private:30003", "2003", "answer"));

    assertThat(result.sent()).isTrue();
    JsonNode body = objectMapper.readTree(requests.getFirst().body());
    assertThat(requests.getFirst().uri()).isEqualTo("/send_private_msg");
    assertThat(body.get("user_id").asText()).isEqualTo("30003");
    assertThat(body.get("message").asText()).isEqualTo("answer");
}

@Test
void sendsUnquotedGroupTextWhenTheConnectorDisablesQuoting() throws Exception {
    QqExternalChatProperties properties = properties();
    properties.getConnectors().get("main").setReplyWithQuote(false);

    ExternalReplyResult result = port(properties).send(command(
            "group:40004", "2002", "answer"));

    assertThat(result.sent()).isTrue();
    JsonNode body = objectMapper.readTree(requests.getFirst().body());
    assertThat(body.get("message").asText()).isEqualTo("answer");
}

@Test
void classifiesExplicitAndAmbiguousFailuresWithoutBlindDuplicates() {
    responseStatus.set(HttpResponseStatus.SERVICE_UNAVAILABLE);
    assertThat(port().send(command("group:40004", "2002", "answer")).retryable())
            .isTrue();

    responseStatus.set(HttpResponseStatus.OK);
    responseBody.set("{\"status\":\"failed\",\"retcode\":1200,\"data\":null}");
    ExternalReplyResult rejected = port().send(command("group:40004", "2002", "answer"));
    assertThat(rejected.retryable()).isFalse();
    assertThat(rejected.errorCode()).isEqualTo("QQ_SEND_REJECTED");

    ExternalReplyResult ambiguous = throwingPort().send(
            command("private:30003", "2003", "answer"));
    assertThat(ambiguous.retryable()).isFalse();
    assertThat(ambiguous.errorCode()).isEqualTo("QQ_DELIVERY_AMBIGUOUS");
}

@Test
void rejectsEmptyTextAndMalformedConversationBeforeNetworkUse() {
    assertThat(port().send(command("group:40004", "2002", " ")).errorCode())
            .isEqualTo("QQ_REPLY_EMPTY");
    assertThat(port().send(command("40004", "2002", "answer")).errorCode())
            .isEqualTo("QQ_CONVERSATION_INVALID");
    assertThat(requests).isEmpty();
}
```

The test server success body is exactly:

```json
{"status":"ok","retcode":0,"data":{"message_id":9009},"message":"","wording":""}
```

- [ ] **Step 2: Run the reply-port test and verify RED**

Run:

```bash
cd be
./mvnw -Dtest=QqExternalChatReplyPortTests test
```

Expected: test compilation fails because `QqExternalChatReplyPort` does not exist.

- [ ] **Step 3: Implement target decoding and payload construction**

Create a conditional `ExternalChatReplyPort` for `QQ`. Decode only nonblank `group:<id>` and `private:<id>` values into this private record:

```java
private record QqTarget(String endpoint, String idField, String id, boolean group) {
}
```

Build private and unquoted group payloads with string `message`. When `target.group()`, connector `replyWithQuote`, and `sourceMessageId` are all present, build:

```java
List.of(
        Map.of("type", "reply", "data", Map.of("id", command.sourceMessageId())),
        Map.of("type", "text", "data", Map.of("text", command.text()))
)
```

Return `QQ_REPLY_EMPTY` or `QQ_CONVERSATION_INVALID` before resolving any HTTP response.

- [ ] **Step 4: Implement the WebClient call and response mapping**

Use one POST to `properties.getBaseUrl()` with a trailing slash removed plus the decoded endpoint. Set JSON content type and Bearer auth. Map this DTO:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
private record QqApiResponse(String status, Integer retcode, QqSentMessage data,
                             String message, String wording) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
private record QqSentMessage(@JsonProperty("message_id") Long messageId) {
}
```

For 2xx responses, require `status=ok`, `retcode=0`, and a non-null message id. Use `QQ_SEND_REJECTED` for explicit business failures and `QQ_RESPONSE_INVALID` for missing/invalid success data. For HTTP 429/5xx return retryable `QQ_SEND_FAILED`; other HTTP statuses return terminal `QQ_SEND_FAILED`. An empty body returns `QQ_RESPONSE_EMPTY`. Catch `RuntimeException` as terminal `QQ_DELIVERY_AMBIGUOUS`.

- [ ] **Step 5: Run QQ reply tests and verify GREEN**

Run the same Maven command. Expected: all `QqExternalChatReplyPortTests` pass.

- [ ] **Step 6: Commit QQ outbound support**

```bash
git add be/src/main/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatReplyPort.java \
  be/src/test/java/top/egon/mario/agent/externalim/adapter/qq/QqExternalChatReplyPortTests.java
git commit -m "feat(agent): send napcat qq text replies"
```

### Task 4: Register, configure, document, and regress the QQ adapter

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
- Modify: `be/src/main/resources/application.yaml`
- Modify: `be/src/test/resources/application.yaml`
- Modify: `docs/external-im-chat-agent.md`

**Interfaces:**

- Consumes: `QqExternalChatProperties` and conditional QQ components from Tasks 2-3.
- Produces: deployable environment-variable configuration and operator instructions; no new runtime API or database contract.

- [ ] **Step 1: Register QQ configuration properties**

Import `QqExternalChatProperties` and add it to the existing annotation:

```java
@EnableConfigurationProperties({AgentSoulProperties.class, ExternalImMemoryProperties.class,
        ChatGuardProperties.class, ExternalChatWorkerProperties.class,
        TelegramExternalChatProperties.class, QqExternalChatProperties.class})
```

- [ ] **Step 2: Add production and test YAML defaults**

Under `mario.agent.external-im`, add:

```yaml
qq:
  enabled: ${AGENT_EXTERNAL_IM_QQ_ENABLED:false}
  base-url: ${AGENT_EXTERNAL_IM_QQ_BASE_URL:http://127.0.0.1:3000}
  connectors:
    main:
      access-token: ${AGENT_EXTERNAL_IM_QQ_MAIN_ACCESS_TOKEN:}
      bot-user-id: ${AGENT_EXTERNAL_IM_QQ_MAIN_BOT_USER_ID:}
      reply-with-quote: ${AGENT_EXTERNAL_IM_QQ_MAIN_REPLY_WITH_QUOTE:true}
```

Under the test `mario.agent.external-im`, add:

```yaml
qq:
  enabled: false
```

- [ ] **Step 3: Document NapCat setup and binding keys**

Update `docs/external-im-chat-agent.md` to:

- State that Telegram and QQ are production adapters while WeCom remains SPI-only.
- Add a `NapCat QQ configuration` section listing all five QQ environment variables.
- Instruct operators to configure a NapCat HTTP Client URL ending in `/api/external-im/webhooks/qq/main`, array message format, self-message reporting disabled, and matching token.
- Instruct operators to configure the NapCat HTTP Server at `AGENT_EXTERNAL_IM_QQ_BASE_URL` for `send_group_msg` and `send_private_msg`.
- Document binding IDs `group:<group_id>` and `private:<user_id>` plus matching audience keys.
- State the text-only, no-WebSocket, no-reply-origin-resolution boundary.
- Preserve all current privacy, retry, and single-instance worker warnings.

- [ ] **Step 4: Run QQ and External IM focused regression tests**

Run:

```bash
cd be
./mvnw -Dtest='*ExternalChat*,*ExternalImMemory*,ExternalImSchemaMigrationTests,ExternalImPersistenceMappingTests,*Telegram*,*Qq*,*ChatAgentFlow*,*ChatInvocationPolicy*,*DefaultChatGuardModel*,*ChatGuardService*' test
```

Expected: all selected tests pass. `ExternalImPostgresContractIT` is intentionally not selected because it requires a disposable external PostgreSQL database.

- [ ] **Step 5: Run the full normal Maven test suite**

Run:

```bash
cd be
./mvnw test
```

Expected: build success with zero test failures. This normal Surefire suite does not select `*IT` PostgreSQL contracts.

- [ ] **Step 6: Check scope and formatting**

Run:

```bash
git diff --check
git status --short
git diff --stat 96c3b8e7..HEAD
rg -n "AGENT_EXTERNAL_IM_QQ|group:<group_id>|private:<user_id>" \
  be/src/main/resources/application.yaml docs/external-im-chat-agent.md
```

Expected: no whitespace errors; only the planned External IM QQ, configuration, test, spec, plan, and documentation files appear.

- [ ] **Step 7: Commit registration and operations documentation**

```bash
git add be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java \
  be/src/main/resources/application.yaml be/src/test/resources/application.yaml \
  docs/external-im-chat-agent.md
git commit -m "docs(agent): configure napcat qq adapter"
```

### Task 5: Final review and completion evidence

**Files:**

- Review only: all files changed since `96c3b8e7`.

**Interfaces:**

- Consumes: all committed tasks and the approved design spec.
- Produces: independent code-review findings, applied fixes if required, and fresh final verification evidence.

- [ ] **Step 1: Compare implementation with the approved spec**

Read `docs/superpowers/specs/2026-07-22-napcat-qq-external-im-adapter-design.md`, inspect `git diff 96c3b8e7..HEAD`, and verify every in-scope item is implemented and every out-of-scope boundary is preserved.

- [ ] **Step 2: Request an independent code review**

Use the `superpowers:requesting-code-review` workflow with:

```text
DESCRIPTION: NapCat QQ HTTP adapter for authenticated group/private text event normalization and replies
PLAN_OR_REQUIREMENTS: docs/superpowers/specs/2026-07-22-napcat-qq-external-im-adapter-design.md and docs/superpowers/plans/2026-07-22-napcat-qq-external-im-adapter.md
BASE_SHA: 96c3b8e7
HEAD_SHA: current branch HEAD
```

Fix all valid Critical and Important findings using a fresh failing regression test before production changes, rerun the focused test, and commit with a scope-specific message.

- [ ] **Step 3: Run fresh final verification**

Run:

```bash
cd be
./mvnw test
cd ..
git diff --check
git status --short --branch
```

Expected: Maven build success, zero failures, no whitespace errors, and a clean named feature branch.

- [ ] **Step 4: Do not start the application**

Record that live NapCat/CyberMario runtime testing is intentionally left to the user because tokens, QQ accounts, HTTP client/server networking, and binding rows are external operational state.
