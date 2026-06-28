# IM Core Contract

This document records the implemented IM core contract for developers wiring
product surfaces to `top.egon.mario.im`. Source of truth is the current backend
facade, service, policy, and realtime code.

## Scope

- Intended readers: backend and frontend developers integrating IM, plus
  maintainers reviewing Clocktower chat behavior.
- Workflow covered: create/list surfaces, join, read/post messages, compute
  unread state, deliver realtime frames, and plug product-specific policy into
  IM.
- Out of scope: data migration design, product UX copy, and future IM roadmap.

## Core Concepts

| Concept | Implemented contract |
|---|---|
| Channel | Public, discoverable surface with one `CHANNEL_MAIN` conversation. Anyone can read an active channel conversation; active channel members can post. |
| Group | Discoverable surface under a channel or standalone. Active group members can read and post its `GROUP` conversation. |
| DM | Implicit ordered user pair (`userLoId`, `userHiId`) with one `DM` conversation. The pair is created on first open/block. |
| Conversation | Unified message stream for `CHANNEL_MAIN`, `GROUP`, and `DM`; sequence, unread, outbox, inbox, and realtime dispatch operate on `conversationId`. |

### Channel Rules

- `RoomFacade.createChannel` creates or reuses an active channel by
  `(contextType, contextId, channelKey)`.
- Channel visibility is currently `PUBLIC`.
- `RoomFacade.listChannels` returns active public channels by context.
- Default visibility policy allows channel history when the conversation and
  channel surface are active, even without membership.
- Default send policy requires an authenticated active member who is not muted,
  banned, or globally muted.

### Group Rules

- `RoomFacade.createGroup` creates or reuses a group by `groupKey`.
- A group can be attached to a channel through `channelId`; without `channelId`
  it is standalone and is listed by `(contextType, contextId)`.
- Groups cannot nest. Passing a group id as `channelId` fails with
  `IM_INVALID_SURFACE_NESTING`.
- `RoomFacade.listGroups` lists active groups by channel or standalone context.
- Default visibility and send policy both require active group membership.

### Join Requests

Supported join policies are `OPEN` and `APPROVAL`.

| State | Source | Meaning |
|---|---|---|
| `PENDING` | `RoomFacade.applyJoin` on an `APPROVAL` surface | Request exists and awaits owner/admin decision. |
| `APPROVED` | `RoomFacade.approveJoin` | Requester is activated as a member and conversation member. |
| `REJECTED` | `RoomFacade.rejectJoin` | Request is closed; any non-banned requester membership/conversation member is deactivated. |
| `CANCELLED` | `RoomFacade.cancelJoin` | Requester cancels own pending request; any non-banned requester membership/conversation member is deactivated. |

`OPEN` surfaces activate membership immediately and return `ACTIVE` in
`JoinResultView`, not a join-request state.

## DM Contract

- `DmFacade.openDm` creates or reuses the ordered pair and its `DM`
  conversation.
- Both users are ensured as conversation members when the pair conversation is
  created or reused.
- `DmFacade.block` records an active directed block and freezes the pair.
- `DmFacade.unblock` inactivates that directed block; the pair is unfrozen only
  when no active block remains between the two users.
- Default DM send policy requires the caller to be a pair participant and the
  pair not to be frozen. Default DM read policy requires pair participation.

## Conversation Sequence And Unread

- `MessageService.send` locks the conversation, increments
  `conversation.messageSeq`, writes the message with the next `messageSeq`, and
  updates last-message fields.
- `clientMsgId` is idempotent per `(conversationId, senderUserId,
  clientMsgId)`: an existing message is returned instead of creating a duplicate.
- `ConversationService.listConversations` computes unread count as
  `conversation.messageSeq - conversationMember.lastReadSeq`, clamped to zero.
- `MessageService.markRead` clamps the requested sequence to the current
  conversation sequence and never moves `lastReadSeq` backward.
- `InboxService.markReadUpTo` marks inbox rows read after the member cursor is
  advanced.

## Outbox And Inbox Delivery

- Sending a message creates an `im_outbox` row with event type
  `MESSAGE_CREATED`.
- Marking read creates an `im_outbox` row with event type `READ_UPDATED` when
  the resulting sequence is greater than zero.
- `InboxService.fanOutMessage` creates per-member inbox rows for active
  conversation members. Non-DM conversations above
  `im.inbox.fanout-threshold` skip fanout and rely on cursor/history recovery.
- `OutboxDispatcher` is enabled only when
  `im.realtime.dispatcher.enabled=true`.
- Dispatch claims pending rows, sends them through `RealtimeRouter`, marks
  success as `DISPATCHED`, and retries failures up to three attempts before
  `FAILED`.
- `LocalRealtimeRouter` is single-node: it delivers only to in-memory
  connections registered in this JVM.

## WebSocket Frames

WebSocket path: `/ws/im`. The client first mints a ticket with
`POST /api/im/ws-ticket` and connects using `?ticket=...`.

All frames use:

```json
{"type":"PING","requestId":"client-id","payload":{}}
```

Client frame types:

| Type | Payload |
|---|---|
| `PING` | `{}` |
| `SUBSCRIBE` | `{ "conversationId": number, "lastSeq": number }` |
| `SEND_MESSAGE` | `SendMessageRequest` payload |
| `MARK_READ` | `{ "conversationId": number, "messageSeq": number }` |

Server frame types:

| Type | Payload |
|---|---|
| `PONG` | `{ "time": ISO-8601 string }` |
| `SEND_ACK` | `{ "message": MessageView }` |
| `READ_UPDATED` | `{ "unread": UnreadView }` or dispatched read event payload |
| `MESSAGE_PUSH` | Dispatched outbox payload with `eventType`, `conversationId`, `messageId`, `messageSeq` |
| `RESYNC` | `{ "reason": string, "conversationId"?: number, "messageSeq"?: number }` |

Invalid client frames and outbound overflow are recovered with `RESYNC`. Clients
should reload history/surfaces from REST when they receive `RESYNC`.

## Facade Package Rule

Upstream modules should depend on:

- `top.egon.mario.im.facade.*`
- `top.egon.mario.im.facade.dto.command.*`
- `top.egon.mario.im.facade.dto.query.*`
- `top.egon.mario.im.facade.dto.view.*`
- `top.egon.mario.im.policy.ImPrincipal` when constructing principals

Upstream modules must not use IM repositories, PO entities, or service classes
as integration contracts. REST controllers and product adapters should stay
thin over the facade.

## RBAC Boundary

- Platform RBAC is coarse: it protects APIs and product entry points.
- IM instance access is enforced by IM membership, bans, mutes, global mutes,
  DM block state, and context-specific policy.
- IM audit history currently allows principals with `SUPER_ADMIN` or
  `CLOCKTOWER_ADMIN`.
- No per-channel, per-group, or per-conversation RBAC resource is implemented.

## Clocktower Adapter

Clocktower integrates through `top.egon.mario.clocktower.chat.ClocktowerImAdapter`.

### Context Type

- Source of truth is `ClocktowerChatConstants.CONTEXT_TYPE =
  "CLOCKTOWER_ROOM"`, and frontend `CLOCKTOWER_IM_CONTEXT_TYPE =
  "CLOCKTOWER_ROOM"`.
- Existing Clocktower IM queries and policy registration use
  `contextType = "CLOCKTOWER_ROOM"` with `contextId` set to room id or game id.

### Policy Plug-in Point

- `PolicyRegistry` selects `SendPolicy` and `VisibilityPolicy` by
  `conversation.contextType`.
- `ClocktowerSendPolicy` and `ClocktowerVisibilityPolicy` register for
  `ClocktowerChatConstants.CONTEXT_TYPE`.
- Both policies adapt IM `ImAccessContext` to `ClocktowerChatAccessContext`
  through `ClocktowerImAdapter.accessContext`, then delegate to
  `ClocktowerChatPolicy`.
- Clocktower policy decides room/public/private/spectator/system chat access
  from viewer mode, group key, conversation type, game phase, day number, and
  active conversation membership.

### IM APIs Used By Clocktower

- `RoomFacade.createChannel` and `RoomFacade.createGroup` create room/game
  channels and public/private/spectator/system groups.
- `RoomFacade.applyJoin` joins users to Clocktower groups; current adapter
  uses `OPEN` join policy for those groups.
- `RoomFacade.listChannels` and `RoomFacade.listGroups` discover Clocktower IM
  surfaces.
- `ImFacade.findConversationSurface` resolves an IM conversation back to its
  channel/group surface.
- `ImFacade.hasActiveConversationMember` checks whether a user is already a
  conversation member.
- `ImFacade.history`, `ImFacade.send`, `ImFacade.markRead`, and
  `ImFacade.auditHistory` back Clocktower chat history, send, read-state, and
  audit flows.
- The frontend Clocktower chat panel also calls generic IM REST endpoints for
  listing channels/groups/conversations/messages, sending, joining, leaving,
  marking read, and using `/ws/im` for realtime.

## Manual QA Checklist

Use this checklist after automated validation, with a disposable local database
when possible.

- Create a public channel and verify a non-member can list and read it, while
  posting is denied until membership is active.
- Create a group under that channel, join as a user, and verify only active
  group members can read, post, and accumulate unread counts.
- Exercise an `APPROVAL` surface through pending, approved, rejected, and
  cancelled join states; confirm rejected/cancelled users lose active
  membership unless they are banned.
- Open a DM from either user order, confirm the same pair conversation is reused,
  then block and unblock to verify send is frozen only while an active block
  exists.
- Send two messages with stable `clientMsgId` values and verify conversation
  sequence increments once per unique client message.
- Mark read past the current sequence and verify the cursor clamps to the
  conversation sequence without decreasing on a later lower request.
- Connect `/ws/im`, subscribe to a conversation, send a message, mark read, and
  confirm `SEND_ACK`, `MESSAGE_PUSH`, and `READ_UPDATED` behavior or REST
  resync after `RESYNC`.
- In Clocktower, start from room chat, game public chat, private chat,
  spectator chat, and system/audit views; confirm the adapter resolves
  `contextType = "CLOCKTOWER_ROOM"` and exposes private peer display as
  `displayPeerKey`.
- Confirm Clocktower access decisions still follow storyteller/player/spectator
  mode, game phase, day number, group key, conversation type, and active
  conversation membership.
- Verify no upstream Clocktower code imports IM PO entities or repositories;
  product code should stay on the facade, query/command/view DTOs, and
  `ImPrincipal`.

## Non-Goals

- No per-instance RBAC for channels, groups, conversations, or DM pairs.
- No multi-node realtime router implementation; `LocalRealtimeRouter` is
  in-memory and single-node.
- No message search, reactions, threads, or media transcoding.
- No guarantee that outbox payloads include full message bodies; clients must
  be able to reload from REST.

## Troubleshooting Notes

- `IM_CONTEXT_TYPE_REQUIRED`: create/list operations and DM open/block require
  a non-blank context type from the caller or principal.
- `IM_MEMBER_REQUIRED`: mark-read requires active conversation membership; public
  channel read does not automatically create unread tracking.
- Missing realtime updates: verify `im.realtime.dispatcher.enabled=true`, then
  reload by history after the last known sequence.
- Clocktower access mismatch: check whether the caller is using
  `CLOCKTOWER_ROOM`; current code registers policies for that context type.
