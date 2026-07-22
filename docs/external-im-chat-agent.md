# External IM Chat Agent

This release provides a common external-IM adapter boundary and the first
production adapter for Telegram. QQ and WeCom can later implement the same
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
- QQ and WeCom require separate protocol Adapter implementations; no
  platform-specific conditional belongs in ChatService or the Graph.
