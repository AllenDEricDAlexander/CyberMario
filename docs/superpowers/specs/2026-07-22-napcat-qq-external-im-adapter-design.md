# 2026-07-22 NapCat QQ External IM Adapter Design

## Status

Approved for implementation on 2026-07-22. The selected transport is NapCat HTTP Client event push for inbound traffic and NapCat HTTP Server OneBot APIs for outbound traffic.

## Goal

Add a production QQ adapter to the existing External IM boundary so NapCat group and private plain-text messages use the same durable ingress, Memory Space, Chat Guard, and Chat Agent flow as Telegram. The existing Web chat path and CyberMario's own IM module remain unchanged.

## Scope

In scope:

- Receive OneBot 11 `message.group` and `message.private` events through `POST /api/external-im/webhooks/qq/{connectorId}`.
- Authenticate inbound NapCat HTTP Client pushes with `Authorization: Bearer <access-token>`.
- Normalize group and private text events into `ExternalChatMessage`.
- Detect an explicit `at` segment targeting the configured bot account.
- Send group text through `/send_group_msg` and private text through `/send_private_msg`.
- Optionally quote the source message for group replies; this is enabled by default.
- Reuse the existing event persistence, binding resolution, Memory Space ordering, Guard, Chat Agent, and reply retry behavior.

Out of scope:

- Images, voice, video, files, faces, markdown, cards, and other non-text modalities.
- WebSocket connections, polling, friend requests, group requests, notices, and administrative actions.
- Resolving a OneBot `reply` segment back to its original sender. In the first version, `repliedToAgentMessage` remains false unless a future durable reply-reference resolver is introduced; QQ clients normally include an `at` segment when replying to the bot, which is still detected.
- Changes to ChatService, the Guard -> Chat Agent graph, agent tools, SoulMD, memory direction rules, or the built-in IM system.
- A post-generation privacy guard or regeneration loop.
- Database schema changes.

## Protocol Choice

NapCat supports HTTP POST event reporting and HTTP OneBot API calls. This adapter uses two independent HTTP connections:

1. NapCat runs an HTTP Client that pushes events to CyberMario.
2. NapCat runs an HTTP Server that receives CyberMario calls to `send_group_msg` and `send_private_msg`.

This preserves the current webhook contract: CyberMario verifies and normalizes the event, persists it idempotently, returns `204 No Content`, and performs Guard/Agent/reply work asynchronously. A Java-owned WebSocket lifecycle is deliberately not introduced.

## Components and Patterns

The existing Adapter and Port pattern remains the extension point:

- `QqExternalChatAdapter` implements `ExternalChatInboundAdapter` and owns OneBot authentication plus normalization.
- `QqExternalChatReplyPort` implements `ExternalChatReplyPort` and owns OneBot send API calls plus response classification.
- `QqExternalChatProperties` owns QQ/NapCat configuration and connector validation.
- `QqOneBotEvent` is the JSON DTO for the small OneBot subset used by this release.

No Factory or Chain of Responsibility is added. The existing `ExternalChatAdapterRegistry` already selects a platform strategy, while direct parsing inside the QQ adapter is clearer for the limited text-only protocol.

The inbound adapter contract changes from returning an `ExternalChatMessage` to returning `Optional<ExternalChatMessage>`. This lets the QQ adapter acknowledge OneBot heartbeat, notice, request, and `message_sent` events without writing thousands of irrelevant audit rows. Telegram continues returning a present value for its supported and audited updates. The controller returns `204` for an authenticated empty result and does not call the ingress service.

## Configuration

The Spring configuration is:

```yaml
mario:
  agent:
    external-im:
      qq:
        enabled: ${AGENT_EXTERNAL_IM_QQ_ENABLED:false}
        base-url: ${AGENT_EXTERNAL_IM_QQ_BASE_URL:http://127.0.0.1:3000}
        connectors:
          main:
            access-token: ${AGENT_EXTERNAL_IM_QQ_MAIN_ACCESS_TOKEN:}
            bot-user-id: ${AGENT_EXTERNAL_IM_QQ_MAIN_BOT_USER_ID:}
            reply-with-quote: ${AGENT_EXTERNAL_IM_QQ_MAIN_REPLY_WITH_QUOTE:true}
```

`enabled`, `base-url`, `access-token`, and `bot-user-id` are required for an active connector. Tokens are never logged, placed in response bodies, or appended to request URLs.

NapCat must configure:

- HTTP Client URL: `https://<cybermario>/api/external-im/webhooks/qq/main`
- HTTP Client token: the same connector access token
- `messagePostFormat`: `array`
- `reportSelfMessage`: `false`
- HTTP Server token: the same connector access token

Using one token for both directions keeps initial operations simple. Each connector remains an independent trust boundary and can have a different token.

## Inbound Authentication and Validation

The adapter performs validation in this order:

1. Resolve and validate the configured connector.
2. Compare the inbound Bearer token with the configured token using constant-time byte comparison.
3. Parse the JSON body.
4. Ignore authenticated events whose `post_type` is not `message`.
5. Ignore `message_sent` events.
6. Require `message_id`, `self_id`, `user_id`, and a supported `message_type`.
7. Require payload `self_id` to equal the configured bot account.
8. Require `group_id` for group messages.

Authentication failures use the existing `EXTERNAL_CHAT_SIGNATURE_INVALID` code and return HTTP 401. Malformed or misrouted payloads return HTTP 400 and are not persisted.

## Normalized Message Mapping

| External field | Group mapping | Private mapping |
| --- | --- | --- |
| `eventId` | decimal `message_id` | decimal `message_id` |
| `messageId` | decimal `message_id` | decimal `message_id` |
| `platform` | `QQ` | `QQ` |
| `connectorId` | webhook path connector | webhook path connector |
| `conversationId` | `group:<group_id>` | `private:<user_id>` |
| `conversationType` | `GROUP` | `DIRECT` |
| `audienceKey` | `qq:<connectorId>:group:<group_id>` | `qq:<connectorId>:private:<user_id>` |
| sender id | `user_id` | `user_id` |
| sender display name | `sender.card`, then `sender.nickname`, then user id | `sender.nickname`, then user id |
| occurred time | `Instant.ofEpochSecond(time)` | `Instant.ofEpochSecond(time)` |

The type prefix in `conversationId` prevents a QQ group number and QQ user number with the same digits from colliding with the existing unique binding key `(platform, connector_id, external_conversation_id)`. Operators must therefore create bindings with the prefixed values.

## Text and Addressing Rules

The adapter consumes the configured OneBot array message format in segment order:

- `text`: concatenate its `data.text` into the normalized text.
- `at`: set `mentionedAgent=true` when `data.qq` equals `self_id`; omit that bot mention from normalized text. Preserve other mentions as `@<qq>` so Guard can see that the message addresses somebody else.
- `reply`: treat it as control metadata and omit it from text. The first version does not claim that it replies to the agent because the standard segment only carries a message id.
- any other segment: classify the whole event as `UNSUPPORTED`, even when a text segment is also present. This avoids answering a partial interpretation of a multimodal message.

At least one nonblank text fragment is required for `TEXT`; otherwise the event is `UNSUPPORTED`. Unsupported message events are durably audited and ignored by the existing ingress rules.

Events sent by the configured bot account are normalized with sender type `BOT`, providing a second loop-prevention check even when NapCat is misconfigured to report self messages. QQ does not expose a reliable general-purpose flag for other bot accounts, so other accounts are treated as human in this version.

## Outbound Routing

`QqExternalChatReplyPort` decodes the normalized conversation id:

- `group:<id>` -> `POST {baseUrl}/send_group_msg`
- `private:<id>` -> `POST {baseUrl}/send_private_msg`

Private body:

```json
{"user_id":"123456789","message":"plain text"}
```

Group body without a quote:

```json
{"group_id":"987654321","message":"plain text"}
```

Group body with the default quote behavior:

```json
{
  "group_id":"987654321",
  "message":[
    {"type":"reply","data":{"id":"2002"}},
    {"type":"text","data":{"text":"plain text"}}
  ]
}
```

All outbound calls use `Content-Type: application/json` and `Authorization: Bearer <access-token>`.

## Delivery Result Semantics

The port accepts a response only when all conditions hold:

- HTTP status is 2xx.
- JSON `status` equals `ok`.
- JSON `retcode` equals `0`.
- `data.message_id` is present.

The returned message id becomes the existing durable `platform_message_id`.

- HTTP 429 and 5xx responses are retryable because the request was explicitly rejected before a successful OneBot result.
- Other HTTP errors, OneBot business failures, invalid responses, and empty responses are terminal.
- Connection errors and timeouts are terminal with an ambiguous-delivery error to avoid blind duplicate QQ messages.
- Empty reply text and malformed prefixed conversation ids are terminal before any network call.

The existing worker reuses its persisted assistant candidate on an explicit retry and does not run the Chat Agent twice.

## Error Handling and Observability

Errors use QQ-specific stable codes such as `QQ_CONNECTOR_NOT_CONFIGURED`, `QQ_PAYLOAD_INVALID`, `QQ_MESSAGE_ID_REQUIRED`, `QQ_BOT_ID_MISMATCH`, `QQ_CONVERSATION_INVALID`, `QQ_SEND_FAILED`, `QQ_RESPONSE_INVALID`, and `QQ_DELIVERY_AMBIGUOUS`. Error messages do not contain access tokens or raw bodies.

No additional database or logging layer is introduced. Existing external event state, reply status, trace id, and worker error columns remain the source of operational visibility.

## Test Strategy

TDD coverage includes:

- Common webhook controller acknowledges an authenticated ignored event without persistence.
- Telegram remains compatible with the optional inbound result.
- QQ Bearer token verification and bot account validation.
- Group text normalization, mention removal, typed conversation id, and sender name selection.
- Private text normalization and direct conversation routing.
- Self-message loop prevention.
- Non-message drop and non-text/mixed-message rejection.
- Group and private outbound endpoint/body selection.
- Optional group quote composition.
- Bearer header use without token leakage into URI or JSON.
- OneBot success parsing and HTTP/business/ambiguous failure classification.
- Existing External IM regression suite.

The disposable PostgreSQL contract test remains unchanged and requires `EXTERNAL_IM_POSTGRES_TEST_URL`, `EXTERNAL_IM_POSTGRES_TEST_USERNAME`, and `EXTERNAL_IM_POSTGRES_TEST_PASSWORD` when run.

## Operational Boundary

No application process is started as part of implementation. Runtime verification is left to the operator after configuring NapCat, connector secrets, and `agent_external_chat_binding` rows for `group:<group_id>` and `private:<user_id>`.
