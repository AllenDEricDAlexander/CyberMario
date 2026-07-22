# External IM Chat Agent

This release provides a common external-IM adapter boundary and production
adapters for Telegram and NapCat QQ. WeCom can later implement the same
`ExternalChatInboundAdapter` and `ExternalChatReplyPort` contracts without
changing `ChatService` or the Guard → Chat Agent flow.

## Telegram configuration

Set `AGENT_EXTERNAL_IM_TELEGRAM_ENABLED=true` and provide the `main` connector
webhook secret, bot token, bot username, and bot user ID through environment
variables. Never commit their values.

Register the webhook as:

`POST /api/external-im/webhooks/telegram/main`

Configure Telegram `setWebhook.secret_token` to the same webhook secret and
limit `allowed_updates` to `["message"]`.

Before enabling traffic, create one `agent_memory_space` owned by the intended
CyberMario user and one `agent_external_chat_binding` for each Telegram chat.
The binding key is `TELEGRAM + main + Telegram chat.id`; its conversation type
must match `private` versus `group/supergroup`.

The webhook returns only after durable event persistence. Agent processing and
Telegram `sendMessage` delivery happen asynchronously. Do not enable more than
one application instance until distributed Space claiming is implemented.

## NapCat QQ configuration

Set these environment variables without committing their values:

- `AGENT_EXTERNAL_IM_QQ_ENABLED=true`
- `AGENT_EXTERNAL_IM_QQ_BASE_URL=http://127.0.0.1:3000`
- `AGENT_EXTERNAL_IM_QQ_MAIN_ACCESS_TOKEN=<strong-random-token>`
- `AGENT_EXTERNAL_IM_QQ_MAIN_BOT_USER_ID=<bot-qq-number>`
- `AGENT_EXTERNAL_IM_QQ_MAIN_REPLY_WITH_QUOTE=true`

Create a NapCat HTTP Server at `AGENT_EXTERNAL_IM_QQ_BASE_URL`, enable its
token, and use the same value as `AGENT_EXTERNAL_IM_QQ_MAIN_ACCESS_TOKEN`.
CyberMario calls `/send_group_msg` and `/send_private_msg` on that server with
`Authorization: Bearer <token>`.

Create a NapCat HTTP Client with the event URL:

`POST https://<cybermario-host>/api/external-im/webhooks/qq/main`

Set the HTTP Client token to the same access token, set `messagePostFormat` to
`array`, and set `reportSelfMessage` to `false`. CyberMario checks the Bearer
token and verifies that event `self_id` equals the configured bot user ID.
Authenticated heartbeat, notice, request, and `message_sent` events are
acknowledged without persistence.

Create bindings with type-prefixed external conversation IDs so QQ user and
group numbers cannot collide:

| QQ conversation | `external_conversation_id` | `conversation_type` | `audience_key` |
| --- | --- | --- | --- |
| Group `40004` | `group:40004` | `GROUP` | `qq:main:group:40004` |
| Private user `30003` | `private:30003` | `DIRECT` | `qq:main:private:30003` |

Only OneBot array messages made of text plus `at`/`reply` control segments are
accepted as text. Any image, voice, video, file, face, card, or other segment
makes the complete message unsupported. The adapter does not open a WebSocket,
poll history, or resolve a `reply` segment back to its original sender. An
explicit `at` segment for the configured bot is detected and removed from the
text sent to the Agent.

Group replies quote the source message by default. Set
`AGENT_EXTERNAL_IM_QQ_MAIN_REPLY_WITH_QUOTE=false` to send unquoted group text.
Private replies are always plain text. The webhook acknowledges only after the
common durable ingress accepts the event; Guard, Agent, and NapCat delivery run
asynchronously.

## Context and privacy boundary

External IM platforms share memory only through an explicitly selected Memory
Space. Web chat may read a selected external-IM Space, but Web-private memory is
not written into that Space and is therefore not exposed back to external IM.

For this release, private and group external-IM conversations bound to the same
Space can both contribute to the shared timeline. The planned post-generation
Reply Guard, which will reject and regenerate a reply that could disclose
private context into a group, is not implemented yet. Bind private and group
chats to separate Spaces unless that risk is acceptable.

External-IM execution disables business tools and SoulMD context. The current
agentic flow contains only the fail-closed Chat Guard and the existing Chat
Agent.

## Release boundaries

- Web without `memorySpaceId` behaves exactly as before.
- Web may read an owned IM Space; Web messages, USER_AGENT Memory, SoulMD and
  Web checkpoints never become visible to external IM.
- A group Chat Agent can currently read private observations from the same IM
  Space. The prompt tells it not to disclose them, but the post-generation
  Reply Guard and regeneration loop are not implemented, so this release does
  not provide a hard leak-prevention guarantee.
- The worker guarantees per-Space ordering only in one application instance.
- Telegram delivery retries only explicit 429/5xx failures before any chunk is
  confirmed sent. Ambiguous timeouts and partial multi-message delivery are
  terminal to avoid blind duplication.
- QQ delivery retries only explicit HTTP 429/5xx failures. OneBot business
  failures, invalid responses, and ambiguous timeouts are terminal to avoid
  blind duplication.
- WeCom still requires a protocol Adapter implementation; no platform-specific
  conditional belongs in ChatService or the Graph.
