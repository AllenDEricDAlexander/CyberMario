# IM System Redesign — Design Document

Date: 2026-06-27
Status: Approved approach (Approach A), pending full-spec review
Author: design session

## 1. Background & Motivation

The repository already contains an `im` module (introduced by
`V26__create_room_im_clocktower_refactor_schema.sql` and the
`2026-06-23-clocktower-room-im-game-refactor-design.md` spec) plus a textbook
greenfield design in `docs/IM System Design Document.pdf`. Neither is
satisfactory:

- **Existing module** — the `im_channel → im_group → im_conversation` triple,
  carried by `context_type/context_id/scope_type/scope_id/participant_key`,
  is too heavy and indirect ("数据模型过重/绕"). It has no real public-channel
  semantics, no reliable delivery, no realtime that belongs to IM (realtime is
  bolted on per-feature via Clocktower SSE), and no conversation list /
  governance.
- **PDF design** — a clean ChatTarget model, but it does not match the desired
  channel/group/public-chat semantics and would require branching message logic
  on target type.

This document specifies a **new, complete IM core** that replaces the existing
`im` module's model. It is built so that **IM is a reusable core exposed only
through an in-process Java Facade**; upstream entry points (Clocktower today,
other product surfaces later, and platform REST controllers) are thin adapters
over that facade. The IM module owns **core messaging capability only** —
identity, RBAC, and product workflows live upstream.

### Goals

1. Real **channel / group / DM** surface semantics with public-readable
   channels, joinable groups, and global 1-1 DM.
2. One **unified conversation** host for every message stream, so
   sequencing / unread / delivery / outbox logic is written exactly once.
3. **Reliable delivery + IM-owned realtime** via a transactional Outbox and a
   WebSocket gateway (single-node primary, multi-node reserved).
4. **Governance**: apply/approve join, announcements, per-user mute, global
   mute, and DM block.
5. A **DTO-only in-process Facade** so any upstream caller can reuse the core
   without touching persistence or platform security.

### Non-Goals

- No DDD; classic **3-layer architecture** (controller/adapter → facade →
  service → repository).
- No per-instance RBAC permissions for channels/groups/conversations. RBAC
  stays coarse at the API level; instance access is governed by IM member
  roles/policies.
- No multi-node message routing implementation in phase 1 (interface reserved).
- No message search, reactions, threads, or media transcoding in this phase.

## 2. Domain Model & Surfaces

Three **surfaces** sit above one unified **conversation** host. A surface
decides *who can join / read / post and how it is governed*; the conversation is
the mechanical *message stream* (seq, delivery, unread).

```
CHANNEL (public, discoverable, public-read) ──owns 1── conversation(CHANNEL_MAIN)
   └─ GROUP (channel_id nullable, join-to-read+post) ──owns 1── conversation(GROUP)

GROUP (standalone, channel_id = NULL) ───────────────── conversation(GROUP)

DM_PAIR (global 1-1, ordered user pair) ─────────────── conversation(DM)
```

### Surface rules

| Surface           | Discoverable | Read       | Post              | Join model              |
|-------------------|--------------|------------|-------------------|-------------------------|
| Channel main-chat | yes (public) | **anyone** | members only      | apply → auto/approve    |
| Group             | yes (listing)| members    | members           | apply → admin approve   |
| DM                | no           | the 2 users| the 2 users†      | implicit on first message |

† DM posting is blocked when the pair is frozen by a block (see §6.4).

### Invariants

- **`channel_id` is nullable on group** — a group lives under a channel *or*
  stands alone. Groups never nest.
- **Every message stream is a `conversation`** with
  `conversation_type ∈ {CHANNEL_MAIN, GROUP, DM}`. Seq / unread / delivery /
  outbox code touches **only** `conversation` — never the surface type.
- **Channel main-chat is public-read**: anyone may read the stream; joining is
  required only to **post** and to receive unread / inbox tracking.
- A **channel owns exactly one** `CHANNEL_MAIN` conversation, created with the
  channel. A **group owns exactly one** `GROUP` conversation. A **DM pair owns
  exactly one** `DM` conversation, created lazily on first open.

### Clocktower as a context adapter

A Clocktower room maps onto a surface of a reserved `context_type`
(`CLOCKTOWER_ROOM`). Its in-game chat is an ordinary conversation. The IM core
stays generic; Clocktower-specific visibility (e.g. storyteller-only whispers)
is supplied through the **policy registry** keyed by `context_type`, not by
special-casing the core.

## 3. Architecture & Layering

```
Upstream entry points (thin adapters)
  • ClocktowerImAdapter        (in-process)
  • ImController / RoomController (platform REST, thin)
  • ImWebSocketHandler         (realtime in/out)
        │  DTO/command in, DTO/view out
        ▼
FACADE  (top.egon.mario.im.facade)  ← the only public surface
  ImFacade · RoomFacade · DmFacade · GovFacade
  retry-on-conflict + REQUIRES_NEW tx orchestration
        │  internal package-scoped services
        ▼
SERVICE (im.service)  @Transactional business logic
  ConversationService · MessageService · MembershipService
  InboxService · OutboxService · GovernanceService · PolicyRegistry
        │  Spring Data JPA
        ▼
REPOSITORY (im.repository) + PO (im.po)  ← hidden from upstream
```

### Layering rules

- **3-layer, not DDD.** Controllers/adapters are thin; business logic lives in
  services; persistence in repositories. The facade is the orchestration +
  contract boundary.
- **DTO-only contract.** Package `im.facade.dto` holds **commands**
  (`SendMessageCommand`, `CreateChannelCommand`, `CreateGroupCommand`,
  `JoinCommand`, `ApproveCommand`, `MarkReadCommand`, `MuteUserCommand`,
  `BlockUserCommand`, `OpenDmCommand`, `HistoryQuery`, …) and **views**
  (`MessageView`, `ConversationView`, `ChannelView`, `GroupView`, `UnreadView`,
  `JoinResultView`, …). Upstream imports **only** these types.
- **No PO leakage.** PO entities and repositories stay internal
  (package-scoped). The facade maps PO → view.
- **Principal injection.** Callers pass an `ImPrincipal` (userId + caller-
  supplied roles/context). The core never depends on platform security/RBAC
  beans — that is what lets Clocktower and future callers reuse it.
- **WebFlux boundary.** Facade methods are plain blocking JPA. WebFlux upstream
  callers wrap them with the existing JPA-scheduler isolation pattern
  (`Mono.fromCallable(facade::x).subscribeOn(jpaScheduler)`). The core stays
  framework-light.

### Representative facade signatures

```java
ImFacade:
  MessageView       send(SendMessageCommand cmd);
  Page<MessageView> history(HistoryQuery q);
  UnreadView        markRead(MarkReadCommand cmd);
  List<ConversationView> listConversations(ListConversationsQuery q);

RoomFacade:
  ChannelView   createChannel(CreateChannelCommand cmd);
  GroupView     createGroup(CreateGroupCommand cmd);   // channelId nullable
  JoinResultView applyJoin(JoinCommand cmd);
  JoinResultView approveJoin(ApproveCommand cmd);
  void          leave(LeaveCommand cmd);

DmFacade:
  ConversationView openDm(OpenDmCommand cmd);
  void             block(BlockUserCommand cmd);
  void             unblock(BlockUserCommand cmd);

GovFacade:
  void mute(MuteUserCommand cmd);
  void globalMute(GlobalMuteCommand cmd);
  void announce(AnnounceCommand cmd);
  void ban(BanUserCommand cmd);
```

## 4. Data Model (new migration `V29`)

Existing migrations are immutable. The new IM model is introduced by a single
new migration `V29__create_im_core_schema.sql`. Because the legacy V26 `im_*`
tables are scaffolding-only (no production data), `V29` **drops the legacy
`im_*` tables first, then creates** the new schema below in the same migration.
This keeps table names clean (no `_v2` suffixes) and avoids any name collision.
The legacy `ImCoreService`/`ImFacade` Java built on the old tables is removed in
the same release. See §11.

All tables carry the standard audit columns used across the project:
`metadata_json JSONB`, `created_at`, `updated_at`, `created_by`, `updated_by`,
`version BIGINT`, `deleted BOOLEAN`.

### 4.1 Surfaces

**`im_channel`** — public, discoverable channel.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| context_type | VARCHAR(64) | e.g. `PLATFORM`, `CLOCKTOWER_ROOM` |
| context_id | BIGINT | nullable for platform-global channels |
| channel_key | VARCHAR(128) | stable slug within (context_type, context_id) |
| name | VARCHAR(128) | |
| owner_user_id | BIGINT | |
| visibility | VARCHAR(32) | `PUBLIC` (phase 1) |
| join_policy | VARCHAR(32) | `OPEN` / `APPROVAL` |
| status | VARCHAR(32) | `ACTIVE` / `ARCHIVED` |
| announcement | TEXT | nullable |
| main_conversation_id | BIGINT | the owned CHANNEL_MAIN conversation |
| member_count | INTEGER | denormalized |
| last_active_at | TIMESTAMPTZ | |

Unique: `(context_type, context_id, channel_key)`.

**`im_group`** — sub-area of a channel *or* standalone.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| channel_id | BIGINT NULL | NULL = standalone group |
| context_type | VARCHAR(64) | inherited/explicit |
| context_id | BIGINT | nullable |
| group_key | VARCHAR(128) | |
| name | VARCHAR(128) | |
| owner_user_id | BIGINT | |
| join_policy | VARCHAR(32) | `OPEN` / `APPROVAL` |
| status | VARCHAR(32) | |
| announcement | TEXT | nullable |
| conversation_id | BIGINT | the owned GROUP conversation |
| member_count | INTEGER | |
| last_active_at | TIMESTAMPTZ | |

Unique: `(channel_id, group_key)` when channel_id NOT NULL; a partial unique on
`(context_type, context_id, group_key)` for standalone groups.

**`im_dm_pair`** — global ordered 1-1 pair.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| user_lo_id | BIGINT | `min(a,b)` |
| user_hi_id | BIGINT | `max(a,b)` |
| conversation_id | BIGINT | the owned DM conversation |
| frozen | BOOLEAN | true while any active block exists |

Unique: `(user_lo_id, user_hi_id)`.

### 4.2 Membership

**`im_membership`** — one row per (surface, user). Surface identified by
`surface_type ∈ {CHANNEL, GROUP}` + `surface_id`. (DM has no membership row; the
two users are implicit.)

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| surface_type | VARCHAR(32) | CHANNEL / GROUP |
| surface_id | BIGINT | |
| user_id | BIGINT | |
| member_role | VARCHAR(32) | `OWNER` / `ADMIN` / `MEMBER` |
| status | VARCHAR(32) | `ACTIVE` / `PENDING` / `LEFT` / `BANNED` |
| muted_until | TIMESTAMPTZ | per-user mute; NULL = not muted |
| joined_at | TIMESTAMPTZ | |

Unique: `(surface_type, surface_id, user_id)`.
Index: `(user_id, status)` for "my channels/groups".

**`im_join_request`** — apply/approve workflow for `APPROVAL` surfaces.

| Column | Notes |
|---|---|
| surface_type, surface_id, user_id | target |
| status | `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` |
| decided_by, decided_at | |

### 4.3 Conversation (unified host)

**`im_conversation`** — the single message host.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| conversation_type | VARCHAR(32) | CHANNEL_MAIN / GROUP / DM |
| owner_surface_type | VARCHAR(32) | CHANNEL / GROUP / DM_PAIR |
| owner_surface_id | BIGINT | back-reference to the surface |
| context_type | VARCHAR(64) | for policy resolution |
| context_id | BIGINT | nullable |
| message_seq | BIGINT | monotonic per-conversation, incremented under lock |
| last_message_id | BIGINT | |
| last_message_at | TIMESTAMPTZ | |
| last_active_at | TIMESTAMPTZ | |
| status | VARCHAR(32) | ACTIVE / ARCHIVED |

Unique: `(owner_surface_type, owner_surface_id, conversation_type)`.

**`im_conversation_member`** — mechanical read/delivery row (derived from
surface membership for CHANNEL_MAIN/GROUP; the 2 users for DM). Drives unread.

| Column | Type | Notes |
|---|---|---|
| conversation_id | BIGINT | |
| user_id | BIGINT | |
| last_read_seq | BIGINT | cursor; **unread = conversation.message_seq − last_read_seq** |
| delivery_mode | VARCHAR(16) | `INBOX` (write-fan-out) / `CURSOR` (read-fan-out) |
| muted | BOOLEAN | per-conversation notification mute |
| status | VARCHAR(32) | ACTIVE / LEFT |

Unique: `(conversation_id, user_id)`.

### 4.4 Messages

**`im_message`**

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| conversation_id | BIGINT | |
| sender_user_id | BIGINT | |
| message_seq | BIGINT | per-conversation, gap-free |
| client_msg_id | VARCHAR(64) | idempotency key from sender |
| message_type | VARCHAR(32) | TEXT / SYSTEM / … |
| content | TEXT | |
| payload_json | JSONB | |
| status | VARCHAR(32) | SENT / EDITED / DELETED |
| sent_at, edited_at, deleted_at | TIMESTAMPTZ | |

Unique: `(conversation_id, message_seq)` and
`(conversation_id, sender_user_id, client_msg_id)` (idempotency).

### 4.5 Reliable delivery & inbox

**`im_outbox`** — transactional outbox written in the same tx as the message.

| Column | Notes |
|---|---|
| id | PK |
| conversation_id, message_id, message_seq | |
| event_type | `MESSAGE_CREATED` / `READ_UPDATED` / `SYSTEM` |
| status | `PENDING` / `DISPATCHED` / `FAILED` |
| available_at, attempts, last_error | for retry/backoff |

Dispatcher selects `PENDING` rows with `FOR UPDATE SKIP LOCKED`, fans out to the
WebSocket gateway, then marks `DISPATCHED`.

**`im_inbox`** — write-fan-out rows for DM + small groups (`delivery_mode=INBOX`).

| Column | Notes |
|---|---|
| user_id, conversation_id, message_id, message_seq | |
| read | boolean (or derived from cursor) |

Large channels/groups (`delivery_mode=CURSOR`) skip inbox rows; unread is
computed from `message_seq − last_read_seq`. The INBOX/CURSOR threshold is a
configurable member count.

### 4.6 Governance

- **Per-user mute**: `im_membership.muted_until`.
- **Global mute**: `im_global_mute (user_id, scope, expires_at)` — `scope` =
  `PLATFORM` or a specific context; checked by send policy.
- **Ban**: `im_membership.status = BANNED` (+ optional `im_ban` audit row with
  reason/expiry).
- **DM block**: `im_dm_block (blocker_user_id, blocked_user_id, status)`.
  Any active block sets `im_dm_pair.frozen = true` (two-way freeze).
- **Announcement**: `announcement` column on channel/group.

### 4.7 Indexing (summary)

- `im_conversation (last_active_at)`, `(owner_surface_type, owner_surface_id)`.
- `im_message (conversation_id, message_seq)` unique covers history paging.
- `im_conversation_member (user_id, status)` for conversation list + unread.
- `im_outbox (status, available_at)` for the dispatcher scan.
- `im_inbox (user_id, message_seq)` for write-fan-out fetch.
- `im_membership (user_id, status)`, `(surface_type, surface_id, status)`.

## 5. Message Flow

### 5.1 Send (single transaction)

1. Facade `send(cmd)` → `MessageService.send`.
2. Lock the conversation row (`SELECT … FOR UPDATE` via
   `findLockedByIdAndDeletedFalse`).
3. Resolve principal → surface membership → **send policy**
   (`PolicyRegistry.resolveSendPolicy(contextType)`): channel/group requires
   ACTIVE membership and not muted/banned/globally-muted; DM requires pair not
   frozen.
4. Idempotency: if `(conversation_id, sender_user_id, client_msg_id)` exists,
   return the existing `MessageView` (no new row).
5. `next_seq = conversation.message_seq + 1`; insert `im_message`; update
   conversation `message_seq / last_message_id / last_message_at`.
6. Insert `im_outbox(MESSAGE_CREATED, PENDING)`.
7. For `INBOX` members, insert `im_inbox` rows (write-fan-out) **in the same tx**
   for small groups/DM; `CURSOR` members are served by read-fan-out.
8. Commit. The dispatcher (separate tx) picks up the outbox row and pushes over
   WebSocket.

### 5.2 History / read-fan-out

`history(q)` resolves visibility policy
(`PolicyRegistry.resolveVisibilityPolicy(contextType)`): channel main-chat is
public-read (no membership needed); group/DM require membership/pair. Returns a
page ordered by `message_seq`.

### 5.3 Mark read & unread

`markRead(cmd)` advances `im_conversation_member.last_read_seq` (monotonic).
Unread is always `conversation.message_seq − last_read_seq` (exact, cheap).
For INBOX members the inbox `read` flag is updated for the affected range.

### 5.4 Conversation list

`listConversations(q)` returns the user's ACTIVE conversation members joined to
conversation summary (last message, last_active_at) + computed unread, sorted by
`last_active_at`. This is the "会话列表" the existing module lacked.

## 6. Realtime — WebSocket Gateway

### 6.1 Transport

- Single bidirectional **WebSocket** endpoint replaces the per-feature SSE.
- Auth: short-lived `ws_ticket` minted by an authenticated REST call, presented
  on WS connect; the handler resolves it to an `ImPrincipal`.
- Per-connection **sink** with a **bounded outbound queue**. On overflow
  (slow consumer): drop buffered frames and send a `RESYNC` hint so the client
  re-fetches history from its last seq. Protects the Reactor event loop.

### 6.2 Frames (client ↔ server)

- Client→Server: `SEND_MESSAGE`, `MARK_READ`, `SUBSCRIBE`, `PING`.
- Server→Client: `SEND_ACK`, `MESSAGE_PUSH`, `READ_UPDATED`, `RESYNC`, `PONG`.

`SEND_MESSAGE` carries `client_msg_id` so the `SEND_ACK` (and any duplicate
`MESSAGE_PUSH`) is idempotent on the client.

### 6.3 Single-node primary, multi-node reserved

Phase 1 keeps connection→sink registry **in-memory** (single node). The
dispatcher calls a `RealtimeRouter` interface to deliver to local sinks. A
multi-node implementation (Redis Pub/Sub fan-out by user/conversation) is left
as a second `RealtimeRouter` binding — **interface reserved, not implemented**.

### 6.4 DM block (two-way freeze)

When A blocks B, `im_dm_block` row is created and `im_dm_pair.frozen=true`.
While frozen, **neither** side can post to the DM conversation (send policy
rejects with `IM_DM_FROZEN`); reads of prior history remain allowed. Unblock
clears the row; the pair unfreezes only when **no** active block remains in
either direction.

## 7. Policy Registry (instance-level access)

Access *within* IM is decided by policies, not RBAC. `PolicyRegistry` resolves
`SendPolicy` / `VisibilityPolicy` by `context_type`:

- `DefaultSendPolicy` / `DefaultVisibilityPolicy` — public-read channel,
  member-gated group/DM, mute/ban/freeze checks.
- `ClocktowerSendPolicy` / `ClocktowerVisibilityPolicy` — game-phase and
  storyteller/whisper visibility, supplied by the Clocktower adapter package and
  registered for `context_type = CLOCKTOWER_ROOM`.

The core never hard-codes Clocktower rules.

## 8. RBAC Boundary

- RBAC stays **coarse, API-level**: `api:im:read`, `api:im:write`,
  `api:im:admin` (and a Clocktower-scoped equivalent upstream). These gate the
  REST controllers only.
- **No per-instance RBAC permissions** are created for channels/groups/
  conversations/messages. Instance access = IM membership + policy.
- The RBAC resource-sync declares the coarse API/menu/button resources from
  code, consistent with project conventions.

## 9. Facade Contract Details

- `im.facade.dto.command.*` and `im.facade.dto.view.*` are the only
  upstream-visible types.
- The facade performs retry-on-unique-conflict (3 attempts) and
  `REQUIRES_NEW` orchestration where ensure-style idempotent creation is needed
  (mirrors the current `ImFacade` retry pattern).
- Mapping PO→view happens in the facade (or a dedicated `im.facade.mapper`),
  never exposing PO.
- `ImPrincipal` is the sole identity input; no Spring Security types cross the
  facade boundary.

## 10. Frontend Surface (high-level)

Backend-first; the frontend reuses the existing
`fe/src/components/chat-workspace/` (@ant-design/x) shell. Phase-1 frontend
scope:

- Conversation list panel (channels / groups / DMs with unread badges).
- Message stream + composer wired to the WebSocket frames.
- Join/apply UI for `APPROVAL` surfaces; announcement banner; block/mute
  controls surfaced minimally.

Detailed frontend component spec is deferred to the implementation plan; this
document fixes only the backend contract and the high-level FE surface.

## 11. Migration & Transition

- **`V29__create_im_core_schema.sql`** is one migration that **(a) drops the
  legacy V26 `im_*` tables** (`im_channel`, `im_group`, `im_conversation`,
  `im_conversation_member`, `im_message`, `im_read_state`), then **(b) creates
  all new tables in §4**. The legacy IM data is scaffolding only, so no backfill
  is required.
- The legacy `ImCoreService` / `ImFacade` and their PO/repository classes built
  on the old tables are **removed** in the same release; the Clocktower chat
  adapter is repointed at the new facade.
- The `room_*` and `clocktower_*` tables from V26 are **kept**; Clocktower rooms
  map onto IM surfaces via the adapter rather than being rebuilt.

## 12. Testing Strategy

- **Service tests** (real Postgres via the project's test DB): send/idempotency,
  seq monotonicity under concurrency, mark-read/unread math, join/approve, mute/
  ban/global-mute, DM block freeze/unfreeze, public-read vs member-gated reads.
- **Outbox/dispatcher tests**: PENDING→DISPATCHED, `SKIP LOCKED` concurrency,
  retry/backoff on failure.
- **Policy tests**: default vs Clocktower send/visibility resolution.
- **Facade contract tests**: DTO in/out, no PO leakage, retry-on-conflict.
- **WebSocket tests**: ticket auth, frame round-trip, slow-consumer
  drop-and-`RESYNC`.

## 13. Phased Delivery

1. Schema `V29` + PO/repository layer.
2. Conversation/Message/Membership services + send/history/mark-read + unread.
3. Surfaces (channel/group/DM) + join/approve + governance.
4. Outbox + dispatcher + inbox write/read fan-out.
5. WebSocket gateway (single-node) + `RealtimeRouter` interface.
6. Facade DTO contract + platform REST controllers.
7. Clocktower adapter migration (repoint chat adapter at the new facade).
8. Frontend high-level surface.

## 14. Open Questions (resolved)

- Channel read: **public read, join to post.**
- DM block: **two-way conversation freeze.**
- Unread: **exact via seq cursor.**
- Facade exposure: **in-process Java facade, DTO-only, 3-layer (not DDD).**
- Realtime: **WebSocket, single-node primary, multi-node reserved.**
- New IM primary; Clocktower is a context adapter.
