# Clocktower Room, IM, And Game Refactor Design

**Date:** 2026-06-23

**Goal:** Refactor Clocktower room, IM, and game lifecycle so a room is a long-lived space, a game is one playable round inside that room, and IM is a reusable channel/group/conversation/message capability that can be used by Clocktower and future modules.

**Status:** User-approved approach A. Use a modular monolith with reusable Room/IM cores, Clocktower adapters, and Clocktower-scoped RBAC API permissions. Implementation must be planned separately before code changes.

---

## 1. Background

The current Clocktower implementation mixes several concepts:

- `clocktower_room` is both a room and a game.
- `clocktower_seat` is both a room seat draft and a running-game seat.
- Joining a room directly means taking a seat.
- There is an `allow_spectators` flag, but no room member or spectator model.
- The player page assumes the current user owns a seat, so a storyteller or `SUPER_ADMIN` can hit `/view` errors while SSE succeeds.
- Public and private chat exist only as event/action concepts, not as a full IM model with channels, groups, conversations, read state, and reusable policies.

The refactor intentionally does not preserve existing development room data. Old Clocktower room data can be discarded or left inaccessible after the new model takes over.

---

## 2. Design Principles

- Room, IM, and Clocktower game logic must be separated.
- Room and IM must be reusable outside Clocktower.
- Clocktower-specific rules should live in adapters and policies, not in generic Room or IM services.
- A room can host multiple games over time.
- A game has its own board snapshot, seat snapshot, events, replay, and audit scope.
- Chat is persistent. Game public chat and player private chat enter game replay/audit. Spectator channel messages are persistent but excluded from game replay/audit except in management audit.
- Normal room pages do not grant admin omniscience. Admin and `SUPER_ADMIN` see all data only from management audit pages.
- RBAC grants module-level and API-level access. Room, IM, and Clocktower policies enforce instance-level access for a concrete room, game, conversation, message, and replay.
- Existing Flyway migrations must not be edited. Database changes must be introduced in one new migration for this change set.

---

## 3. Architecture

### 3.1 Modules

| Module | Package | Responsibility |
|---|---|---|
| Generic Room | `top.egon.mario.room` | Long-lived rooms, members, invitations, bans, room visibility, heartbeat |
| Generic IM | `top.egon.mario.im` | Channel, group, conversation, message, read state, visibility/send policies |
| Clocktower Room Adapter | `top.egon.mario.clocktower.room` | Clocktower-specific room aggregation, board selection, seat draft, invitations |
| Clocktower Game | `top.egon.mario.clocktower.game` | Game lifecycle, board snapshot, game seat snapshot, start/end/abort |
| Clocktower Chat Adapter | `top.egon.mario.clocktower.chat` | Clocktower chat policy, game replay/audit integration |
| Clocktower View | `top.egon.mario.clocktower.view` | Storyteller, player, spectator, invited-user views |
| Clocktower Audit/Replay | `top.egon.mario.clocktower.replay` / admin audit | Game replay and management audit projections |

### 3.2 Design Patterns

| Pattern | Use | Reason |
|---|---|---|
| Facade | `RoomFacade`, `ImFacade` | Provide stable service entry points and hide member/invite/chat details from business modules |
| Strategy / Policy | `RoomAccessPolicy`, `SeatAssignmentPolicy`, `ChatSendPolicy`, `ChatVisibilityPolicy` | Allow future modules to reuse Room/IM with their own rules |
| Adapter | `ClocktowerRoomContextAdapter`, `ClocktowerImContextAdapter` | Map generic Room/IM concepts to Clocktower room, game, seat, phase, and viewer data |
| Domain Service | `ClocktowerGameLifecycleService`, `ClocktowerRoomLifecycleService` | Keep Clocktower orchestration out of generic modules |
| State Machine | Room and game status transitions | Prevent illegal transitions such as changing seats after a game starts |
| Observer / Domain Event | Room, game, invitation, and message events | Let SSE, audit, replay, and projections react without tight coupling |
| Specification | Visibility and message/history queries | Keep complex "what can this user see" queries testable and reusable |
| Factory | Game snapshots and default IM structures | Centralize creation of game seats, channel groups, and scoped conversations |
| Command, optional | Player and storyteller actions if action count grows | Use only when action branching becomes too large for direct service methods |

These patterns are justified because Room and IM are expected to be reused in other product areas. A direct implementation would hard-code Clocktower assumptions and make future reuse expensive.

### 3.3 Selected Backend Architecture

The selected backend architecture is a modular monolith:

```text
Controller
  -> Clocktower application service
      -> RoomFacade
      -> ImFacade
      -> Clocktower repositories
      -> Domain events published after commit
```

The generic Room and IM modules own reusable capability. Clocktower owns game rules and orchestration. Room and IM should not import Clocktower packages. Clocktower may depend on Room and IM through facades, policy interfaces, and context adapters.

`RoomFacade` and `ImFacade` are stable application entry points for other modules. They hide internal membership, invitation, ban, channel, conversation, message, and read-state services. The facades may write data, but the transaction boundary for cross-module Clocktower workflows stays in the Clocktower application service.

Policy implementations are selected by `contextType`. Use Spring bean registration backed by a policy registry or bean map:

```text
RoomAccessPolicy<CLOCKTOWER>
SeatAssignmentPolicy<CLOCKTOWER>
ChatSendPolicy<CLOCKTOWER>
ChatVisibilityPolicy<CLOCKTOWER>
```

Future modules add their own policy beans without changing generic Room or IM core services.

### 3.4 Transaction Boundaries

Room and Game operations require strong consistency. IM requires local consistency for messages and read state, then integrates with Clocktower replay/audit through after-commit events.

Transaction boundaries:

| Boundary | Owner | Rule |
|---|---|---|
| Single Room operation | `RoomApplicationService` or `RoomFacade` | Use `@Transactional` when no outer Clocktower transaction exists |
| Single IM operation | `ImApplicationService` or `ImFacade` | Use `@Transactional` for conversation/message/read-state writes |
| Clocktower room/game workflow | Clocktower application service | One outer `@Transactional` controls Room, Game, IM activation, and event append |
| Policy evaluation | Policy classes | No database writes; may load read-only context through adapters |
| Context mapping | Adapter classes | No transaction ownership; map Room/IM context to Clocktower context |
| SSE and projections | Domain event listeners | Publish only after transaction commit |

Existing `TransactionSynchronization.afterCommit()` style should be preserved for SSE and external projections. A client must not receive a room/game/message update before the database transaction commits.

### 3.5 Concurrency Model

Use pessimistic locks for user-facing state transitions that must serialize per room or per conversation. Keep the existing `@Version` optimistic lock columns as a secondary guard.

Lock order must be stable:

```text
room -> game -> room seat/invitation/member -> im channel/group/conversation -> message/event
```

Important operations:

| Scenario | Concurrency handling |
|---|---|
| Claim or release seat | Lock room, then validate and update the room seat draft |
| Accept seat invitation | Lock room, invitation, and target seat draft in one transaction |
| Switch board | Lock room, count occupied/reserved seats, then update board snapshot and seat draft |
| Start game | Lock room, validate board/seats/invitations/ready, create game and immutable game seats, activate IM conversations |
| End or abort game | Lock game and room, then perform a single legal state transition |
| Storyteller offline timeout | Scheduler locks candidate room/game, rechecks heartbeat expiry, then aborts/disbands once |
| Send message | Lock conversation or allocate message sequence through a unique database constraint |
| Mark read | Update read state idempotently; do not block message sending |

Message order must be conversation-local. `im_message` should use `message_seq` unique per `conversation_id`. If sequence allocation races, retry a small number of times like the existing Clocktower event append retry.

State changes should be idempotent where possible. Ending an already ended game, accepting an already accepted invitation by the same user, and marking read to an older sequence should return the current state or fail with a clear domain error instead of partially writing data.

### 3.6 Generic Room Internal Design

Generic Room is a reusable space capability. It does not know Clocktower board, phase, role, or game-seat rules.

```text
top.egon.mario.room
  facade/
    RoomFacade
  service/
    RoomApplicationService
    RoomMembershipService
    RoomInvitationService
    RoomBanService
    RoomHeartbeatService
  policy/
    RoomAccessPolicy
    RoomJoinPolicy
    RoomInvitationPolicy
    SeatAssignmentPolicy
  context/
    RoomContext
    RoomContextAdapter
    RoomPolicyRegistry
  po/
    RoomSpacePo
    RoomMemberPo
    RoomInvitationPo
    RoomBanPo
```

Pattern usage:

| Pattern | Room usage |
|---|---|
| Facade | `RoomFacade.enterRoom`, `invite`, `acceptInvitation`, `kick`, `ban`, `heartbeat` |
| Strategy / Policy | Context-specific access, invitation, join, and seat-assignment decisions |
| Adapter | Clocktower maps room owner, room status, member role, and seat target into `RoomContext` |
| Specification | Visible room list, acceptable invitation, active ban, and member lookup predicates |
| Domain Service | Membership, invitation, ban, and heartbeat orchestration |
| Domain Event | `RoomEnteredEvent`, `RoomInvitationAcceptedEvent`, `RoomMemberKickedEvent`, `RoomBannedEvent` |

Room invariants:

- A user has at most one active `room_member` row per room.
- A banned user cannot enter, spectate, claim a seat, or accept an invitation.
- A seat-level invitation reserves a target seat only while active.
- Room heartbeat is room-local and updates member activity for that room only.
- Room services never decide Clocktower day/night/chat rules.

### 3.7 Generic IM Internal Design

Generic IM owns channel, group, conversation, message, and read-state persistence. It does not hard-code Clocktower night chat, spectator, or storyteller visibility rules.

```text
top.egon.mario.im
  facade/
    ImFacade
  service/
    ImChannelService
    ImConversationService
    ImMessageService
    ImReadStateService
  policy/
    ChatSendPolicy
    ChatVisibilityPolicy
    ChatConversationPolicy
  context/
    ImContext
    ImContextAdapter
    ImPolicyRegistry
  factory/
    ImChannelFactory
    ImConversationFactory
  po/
    ImChannelPo
    ImGroupPo
    ImConversationPo
    ImConversationMemberPo
    ImMessagePo
    ImReadStatePo
```

Pattern usage:

| Pattern | IM usage |
|---|---|
| Facade | `ImFacade.sendMessage`, `history`, `createConversation`, `markRead` |
| Strategy / Policy | Context-specific send, visibility, and conversation-creation rules |
| Adapter | Clocktower maps room, game, seat, phase, day number, and viewer mode into `ImContext` |
| Specification | Message history, unread counts, normal visibility, and management audit queries |
| Factory | Create Clocktower default channel, groups, and game-scoped conversations |
| Domain Event | `ImMessageSentEvent`, `ImConversationCreatedEvent`, `ImReadStateUpdatedEvent` |

IM invariants:

- A channel belongs to one business context, such as one Clocktower room.
- A conversation belongs to one group and may be scoped to a room or game.
- Private one-to-one conversations are identified by a stable participant key so duplicate conversations are not created.
- Normal visibility comes from conversation membership plus `ChatVisibilityPolicy`.
- Management audit can bypass normal visibility only through admin audit APIs.
- Message sending always evaluates `ChatSendPolicy` before persisting the message.

### 3.8 Clocktower Integration Flow

Clocktower remains the owner of game lifecycle and game rules.

Start game flow:

1. `ClocktowerGameLifecycleService.startGame` locks the room.
2. It validates board snapshot, seat draft, invitations, ready state, and real users.
3. It creates `clocktower_game`.
4. It uses a factory to copy `clocktower_room_seat` into immutable `clocktower_game_seat`.
5. It changes room status from `OPEN` to `IN_GAME` and game status to `RUNNING`.
6. It calls `ImFacade.activateGameConversations` to create game public, private, spectator, and system conversations.
7. It appends `GAME_STARTED` to the game event stream.
8. It publishes room/game/IM notifications only after commit.

Clocktower-specific policies:

| Policy | Responsibility |
|---|---|
| `ClocktowerRoomAccessPolicy` | Public/private room visibility, invited-user access, banned-user rejection |
| `ClocktowerSeatAssignmentPolicy` | Open seating, approval required, invite-only, ready/start constraints |
| `ClocktowerChatSendPolicy` | Day/night chat restrictions, spectator send restrictions, private chat window |
| `ClocktowerChatVisibilityPolicy` | Player, storyteller, spectator, replay, and management-audit visibility |
| `ClocktowerGameStateMachine` | Legal room/game/phase transitions |

The state machine should be direct and explicit. Do not introduce a generic state-machine framework unless the implementation proves that hard-coded transition checks are no longer readable.

---

## 4. Data Model

Exact column details can be finalized in the implementation plan, but the model boundaries are fixed here.

### 4.1 Generic Room Tables

`room_space`

- Long-lived room.
- Stores owner user id, visibility, status, context type, context id, created/updated metadata.
- For Clocktower, `context_type = CLOCKTOWER_ROOM`.

`room_member`

- User membership in a room.
- Stores user id, room id, member role, member status, last active time in this room.
- Clocktower uses roles such as `OWNER_STORYTELLER`, `PLAYER`, `SPECTATOR`.

`room_invitation`

- Supports both room-level and seat-level invitation.
- Seat-level invitations reserve a target seat until accepted, declined, cancelled, moved, or expired.
- Users must accept and confirm seat assignment before they count as valid players.

`room_ban`

- Room-level ban.
- Prevents entering the room, spectating, claiming a seat, or accepting invitations until the ban expires or is lifted.

### 4.2 Generic IM Tables

IM is modeled like a Telegram-style hierarchy:

```text
Channel
  Group
    Conversation
      Message
```

`im_channel`

- Business chat space.
- Fields include `context_type`, `context_id`, `channel_type`, `status`.
- A Clocktower room gets one channel.

`im_group`

- Group or section inside a channel.
- Clocktower groups include `ROOM_PUBLIC`, `SPECTATOR`, `PRIVATE`, and optional `SYSTEM`.

`im_conversation`

- Concrete conversation.
- Supports `GROUP_MAIN`, `PRIVATE_1_TO_1`, and future `THREAD`.
- Can be scoped with `scope_type` and `scope_id`.
- For Clocktower, running-game conversations use `scope_type = CLOCKTOWER_GAME` and `scope_id = gameId`.

`im_conversation_member`

- Explicit members for a conversation.
- Player-to-player private conversations include only the two players. Storyteller visibility is granted by policy, not by adding the storyteller as a hidden member.

`im_message`

- Persistent message record.
- Includes channel, group, conversation, sender, message type, content, metadata, and audit classification.
- Includes `message_seq`, unique per conversation, for stable conversation-local ordering.

### 4.3 Clocktower Tables

`clocktower_room_profile`

- Clocktower-specific data for a generic room.
- Stores current board id/snapshot, current script, required player count, public/private setting, room status, current game id if any.

`clocktower_room_seat`

- Room-level seat draft for the next game.
- Non-running games can change this draft.
- Switching to a smaller board is blocked when occupied or reserved seat count exceeds the new board player count.

`clocktower_game`

- One playable game inside a room.
- Stores `room_id`, `game_no`, status, phase, script, player count, board snapshot, started/ended timestamps, end reason.

`clocktower_game_seat`

- Immutable seat snapshot for one game after start.
- Stores user id, seat no, display name, role, role type, alignment, life status, dead vote state.

`clocktower_game_event`

- Game fact event stream.
- Bound to `room_id + game_id`.
- Used for replay and audit. Spectator channel chat does not become a game fact event.

Existing Clocktower rule data, board config, grimoire, flow, ruling, and replay code should be reused where practical after adapting them from room id to game id.

### 4.4 Database Integrity

The migration should add database constraints that enforce concurrency assumptions:

| Table | Constraint |
|---|---|
| `room_member` | Unique active membership by `room_id + user_id` |
| `room_invitation` | Unique active seat reservation by `room_id + target_seat_no` where applicable |
| `room_ban` | Indexed active ban lookup by `room_id + user_id + expires_at` |
| `clocktower_room_profile` | Unique profile by `room_id` |
| `clocktower_room_seat` | Unique active seat draft by `room_id + seat_no` |
| `clocktower_game` | Unique game number by `room_id + game_no` |
| `clocktower_game_seat` | Unique game seat by `game_id + seat_no`; unique game user by `game_id + user_id` where user id is present |
| `im_channel` | Unique channel by `context_type + context_id + channel_type` |
| `im_group` | Unique group by `channel_id + group_type` where the group type is singleton |
| `im_conversation` | Unique scoped conversation by `group_id + scope_type + scope_id + conversation_type + participant_key` |
| `im_conversation_member` | Unique member by `conversation_id + user_id` |
| `im_message` | Unique message sequence by `conversation_id + message_seq` |
| `im_read_state` | Unique read state by `conversation_id + user_id` |

Use partial unique indexes only when the project already uses PostgreSQL-specific migration style for that area. Otherwise, model active status explicitly and enforce uniqueness in service locks plus standard constraints.

---

## 5. Room, Seat, Invitation, And Ban Rules

### 5.1 Room Lifecycle

Room status:

| Status | Meaning |
|---|---|
| `OPEN` | Enterable, can prepare the next game |
| `IN_GAME` | A game is running |
| `DISBANDED` | Not enterable; hidden from room lobby/list; only history and management audit remain |

The room owner is the storyteller and cannot be changed.

Rooms default to public visibility. Public rooms are visible to logged-in users. Private rooms are visible only to the storyteller, members, and invited users.

### 5.2 Board Selection

- A room must select a board.
- The board determines script and player count.
- When no game is running, the storyteller may switch the current board.
- Switching board is blocked if the new player count is lower than occupied/reserved seats.
- Occupied/reserved seats include accepted players and non-declined/non-cancelled seat invitations.
- Room list shows current script, required player count, and occupied/reserved seat count.

### 5.3 Entry And Default Spectator State

- Entering a room defaults the user to spectator.
- Banned users cannot enter.
- A room-level invitation lets a user enter as spectator.
- A seat-level invitation reserves a seat but does not count as an accepted player until the user accepts and confirms.

### 5.4 Seat Claiming And Ready

Room seating policy is configurable:

| Policy | Meaning |
|---|---|
| `OPEN_SEATING` | Spectators can claim open seats directly |
| `APPROVAL_REQUIRED` | Spectators request seats and storyteller approves |
| `INVITE_ONLY` | Only invitations can assign seats |

Default is `APPROVAL_REQUIRED`.

Players must be ready before a game can start. Accepting a seat is not the same as ready.

Start requirements:

- No running game in the room.
- Current board is valid.
- Seat count matches board player count.
- Every game seat is bound to a real user.
- All seat invitations are accepted.
- All players are ready.
- The storyteller cannot start a game with manually typed fake players.

### 5.5 Kick, Release, And Ban

| Operation | Result |
|---|---|
| Release/kick from seat | User remains in the room as spectator, seat is released, ready is cleared |
| Kick from room | User leaves room |
| Ban from room | User cannot enter, spectate, claim seat, or accept invitation until ban expires or is lifted |

When releasing a seat, storyteller can choose:

- Release seat only.
- Release seat and kick from room.
- Release seat, kick from room, and ban for a configured duration.

During a running game, game seats are locked. Kicking a user affects future access and chat, but the game seat snapshot remains.

---

## 6. Game Lifecycle

### 6.1 Status

| Status | Meaning |
|---|---|
| `DRAFT` | Prepared game draft |
| `RUNNING` | Game in progress |
| `ENDED` | Normal end |
| `ABORTED` | Mid-game abort |
| `ARCHIVED` | Read-only archive |

### 6.2 Starting A Game

1. Storyteller confirms current board and room seat draft.
2. Backend validates board, seats, invitations, and ready states.
3. Backend creates `clocktower_game`.
4. Backend copies room seats to `clocktower_game_seat`.
5. Backend snapshots board and role assignment into the game.
6. Backend marks game `RUNNING` and room `IN_GAME`.
7. Backend writes `GAME_STARTED` event.
8. Backend activates game-scoped IM conversations.

Each new game defaults to the previous game seat draft, but the storyteller can change board, invitations, seats, and roles before starting.

### 6.3 Ending Or Aborting

Normal end:

- Game becomes `ENDED`.
- Room returns to `OPEN`.
- Replay remains available to this game's players and storyteller.

Storyteller offline timeout:

- Applies only while a game is running.
- If the storyteller is inactive in this room for more than five minutes, the room is automatically disbanded.
- Game becomes `ABORTED`.
- Room becomes `DISBANDED`.
- End reason is `STORYTELLER_OFFLINE_TIMEOUT`.
- Room disappears from normal room list/lobby and is visible only via historical games and management audit.

Manual disband:

- Same visibility result as timeout, with distinct reason such as `STORYTELLER_DISBANDED`.

---

## 7. IM Rules

### 7.1 Clocktower Channel Structure

One Clocktower room owns one IM channel:

| Group | Purpose |
|---|---|
| `ROOM_PUBLIC` | Room public chat |
| `PRIVATE` | Container for one-to-one private conversations |
| `SPECTATOR` | Spectator channel after a game starts |
| `SYSTEM` | Optional system notifications |

### 7.2 Before Game Start

- Only room public chat exists.
- It binds to `roomId`, not `gameId`.
- It persists in room chat history.
- It enters room-level audit.
- It does not enter any game replay.

### 7.3 During Game

| Chat | Binding | Visibility | Replay/Audit |
|---|---|---|---|
| Player public chat | `roomId + gameId` | Storyteller, players, spectators read-only | Player/storyteller replay and management audit |
| Player one-to-one private chat | `roomId + gameId` | Two players plus storyteller by policy | Participant replay, storyteller replay, management audit |
| Storyteller-player private chat | `roomId + gameId` | Storyteller and target player | Player/storyteller replay and management audit |
| Spectator channel | `roomId + gameId` | Spectators only | Persistent, excluded from game replay; visible in management audit |

Private chat is one-to-one only in this design. It is room-local and game-local, not a system-wide private messaging feature.

Each game gets separate private conversations. Previous game private chats become read-only history.

### 7.4 Chat Permission Rules

- Player public chat is not allowed at night.
- Storyteller public announcements are not limited by night.
- Player private chat is not allowed at night.
- Player private chat is allowed only during day-like phases such as `DAY`, `NOMINATION`, and `EXECUTION`.
- Player private chat defaults to the first two days, configurable by room/game rule.
- Storyteller can adjust player private chat rules during a game, but each adjustment must write a public system event.
- Player public chat rules are locked after game start.
- Dead players can still use public chat and private chat during allowed phases.
- Spectators can read player public chat after game start but can only send to the spectator channel.
- Spectators cannot private chat with players or storyteller.
- Storyteller cannot read or send spectator channel messages in normal room pages.
- Admin and `SUPER_ADMIN` can view all chat only in management audit.

---

## 8. Viewer Matrix

### 8.1 Room Lobby

| Content | Storyteller | Player | Spectator / Invited User |
|---|---|---|---|
| Room basics | Yes | Yes | Yes |
| Current board/script/player count | Yes | Yes | Yes |
| Seat occupancy | Yes | Yes | Yes |
| Reserved seat | Yes | Yes, limited | Yes, limited |
| Invitation list | All | Own only | Own only |
| Ban/kick management | Yes | No | No |
| Ready state | Yes | Yes | Yes |
| Room public chat | Yes | Yes | Yes |
| Board switch/invite/kick/start | Yes | No | No |

### 8.2 Running Game

Storyteller sees:

- All seats, roles, alignments, life states, grimoire, markers.
- All player public chat.
- All player private chat by visibility policy.
- Storyteller-player private chat.
- Flow controls, night tasks, rulings, member governance.
- No spectator channel in normal room pages.

Player sees:

- Own role, alignment, status, dead vote.
- Public seats and public life states.
- Public events.
- Player public chat.
- Private conversations they participate in.
- Available game actions.
- No other player roles, grimoire, storyteller tasks, spectator channel.

Spectator sees:

- Room and current game basics.
- Public seats and public life states.
- Public events.
- Player public chat read-only.
- Spectator channel read/write.
- No roles, private chat, grimoire, or player actions.

Admin in normal room pages follows normal resolved room identity. Admin omniscience applies only in management audit.

---

## 9. Replay And Audit

There is no public replay for arbitrary users.

| Viewer | Access |
|---|---|
| This game's player | Own visible replay only |
| Storyteller | Full game replay |
| Management audit | Full room/game/chat/spectator/governance audit |
| Spectator or unrelated user | No game replay access |

Replay is game-scoped and queried by `gameId`. Management audit can query by room, game, channel, group, conversation, message, invitation, member, or ban.

Disbanded rooms are hidden from normal room list. Their games remain available through history if the user has replay access.

---

## 10. Online And Auto-Disband

Online state is room-local, not global user activity.

- Lobby, player view, storyteller view, and spectator view send room heartbeat.
- Room member last active time is updated per room.
- Player and spectator inactivity only marks them offline.
- Storyteller inactivity only disbands the room while a game is running.
- If storyteller has no heartbeat in the room for more than five minutes during a running game:
  - Game becomes `ABORTED`.
  - Room becomes `DISBANDED`.
  - Reason is `STORYTELLER_OFFLINE_TIMEOUT`.
  - Room cannot be entered again.
  - Historical game and management audit remain available.

---

## 11. API Surface

Exact request/response DTOs will be finalized in the implementation plan, but the service surface should follow this shape.

### 11.1 Clocktower Room APIs

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/clocktower/rooms` | Create room with current board |
| `GET` | `/api/clocktower/rooms` | List visible non-disbanded rooms |
| `GET` | `/api/clocktower/rooms/{roomId}` | Room lobby aggregate view |
| `PATCH` | `/api/clocktower/rooms/{roomId}/board` | Switch board when no game is running |
| `POST` | `/api/clocktower/rooms/{roomId}/invitations` | Create room-level or seat-level invitation |
| `POST` | `/api/clocktower/rooms/{roomId}/invitations/{id}/accept` | Accept invitation |
| `POST` | `/api/clocktower/rooms/{roomId}/invitations/{id}/decline` | Decline invitation |
| `POST` | `/api/clocktower/rooms/{roomId}/seats/{seatNo}/claim` | Claim or request seat |
| `POST` | `/api/clocktower/rooms/{roomId}/seats/{seatNo}/release` | Release/kick seat |
| `POST` | `/api/clocktower/rooms/{roomId}/members/{userId}/kick` | Kick from room, optionally ban |
| `POST` | `/api/clocktower/rooms/{roomId}/heartbeat` | Room-local heartbeat |

### 11.2 Clocktower Game APIs

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/clocktower/rooms/{roomId}/games/start` | Start current draft game |
| `POST` | `/api/clocktower/games/{gameId}/end` | End game normally |
| `GET` | `/api/clocktower/games/{gameId}/view` | Current viewer game view |
| `GET` | `/api/clocktower/games/{gameId}/events/stream` | Game event SSE |
| `GET` | `/api/clocktower/games/{gameId}/replay` | Player/storyteller replay |
| `GET` | `/api/clocktower/games/history` | Historical games available to current user |

### 11.3 Clocktower Chat APIs

The generic IM module is an internal reusable capability in this change set. It should not expose platform-level `/api/im/**` APIs or `api:im:*` permissions yet. Clocktower owns the current chat API surface and maps these endpoints to `ImFacade` internally.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/clocktower/rooms/{roomId}/chat/conversations` | Room/game chat conversations visible to the current viewer |
| `GET` | `/api/clocktower/chat/conversations/{conversationId}/messages` | Message history through Clocktower visibility policy |
| `POST` | `/api/clocktower/chat/conversations` | Create or resolve a Clocktower-scoped private conversation |
| `POST` | `/api/clocktower/chat/conversations/{conversationId}/messages` | Send message through Clocktower chat send policy |
| `POST` | `/api/clocktower/chat/conversations/{conversationId}/read` | Mark read for a Clocktower chat conversation |

### 11.4 Management Audit APIs

Management APIs live under `/api/admin/clocktower/**`, including Clocktower chat/IM audit endpoints such as `/api/admin/clocktower/chat/**`.

They must allow admin and `SUPER_ADMIN` to inspect all room, game, chat, spectator channel, invitation, member, kick, and ban records.

---

## 12. RBAC Integration

### 12.1 Authorization Boundary

Clocktower uses the existing RBAC flow:

```text
ClocktowerRbacResourceProvider
  -> RbacResourceSynchronizer
      -> sys_permission / sys_api / role presets
          -> DynamicAuthorizationManager
              -> Controller
                  -> Room/IM/Clocktower policy checks
```

RBAC answers "may this user call this category of API?" Business policies answer "may this user access this exact room, game, conversation, or replay?" Do not create RBAC permissions for individual rooms, games, conversations, or messages.

This keeps RBAC stable and prevents business-instance data from polluting `sys_permission`. The concrete access rules stay in `RoomAccessPolicy`, `SeatAssignmentPolicy`, `ClocktowerChatSendPolicy`, `ClocktowerChatVisibilityPolicy`, and replay access policies.

### 12.2 Resource Provider

Update the existing `ClocktowerRbacResourceProvider` to declare the new room/game/chat/admin API resources. Do not use annotation scanning as the primary source for this module because Clocktower needs menus, buttons, API permissions, and role presets to stay aligned.

All current chat HTTP APIs are Clocktower APIs:

- No platform-level `/api/im/**` routes in this refactor.
- No `api:im:*` permissions in this refactor.
- Generic IM remains reusable internally through `ImFacade`.
- Future modules that reuse IM will expose their own module-specific APIs and permissions.

Accepted implementation deviation: this refactor adds `V27__retire_old_clocktower_rbac_resources.sql` as a one-time RBAC data migration to retire legacy Clocktower permissions. The current `RbacResourceSynchronizer` appends and updates managed provider resources, but it does not retire provider-managed resources that disappear from a provider or remove stale role grants. This migration is therefore accepted as a transitional cleanup; future stale-resource retirement should be moved into RBAC synchronization before similar migrations are added.

### 12.3 API Permission Codes

Use capability-level permissions rather than one permission per endpoint:

| Permission | Covers |
|---|---|
| `api:clocktower:script:read` | Script, role, term, and jinx-rule read APIs |
| `api:clocktower:board:*` | Board generate, validate, save, list, and delete APIs |
| `api:clocktower:room:read` | Visible room list and room lobby aggregate |
| `api:clocktower:room:create` | Create a Clocktower room |
| `api:clocktower:room:membership` | Enter, heartbeat, accept/decline invitation, leave |
| `api:clocktower:room:seat` | Claim, request, release, approve, or move a seat |
| `api:clocktower:room:governance` | Kick, ban, invite, switch board, and room governance operations |
| `api:clocktower:game:read` | Current viewer game view and public game facts |
| `api:clocktower:game:lifecycle` | Start, end, abort, and disband game operations |
| `api:clocktower:game:action` | Player actions during a game |
| `api:clocktower:game:storyteller` | Storyteller flow, grimoire, night tasks, nominations, execution, and rulings |
| `api:clocktower:game:event-stream` | Game SSE stream |
| `api:clocktower:game:replay` | Player/storyteller game replay and history |
| `api:clocktower:chat:read` | Clocktower chat conversation list and message history |
| `api:clocktower:chat:send` | Send room/game chat messages |
| `api:clocktower:chat:conversation` | Create or resolve Clocktower private conversations |
| `api:clocktower:chat:read-state` | Mark Clocktower chat read state |
| `api:admin:clocktower:audit` | Management audit for rooms, games, chat, invitations, members, bans, and replay |
| `api:admin:clocktower:rule-data` | Clocktower rule data administration if rule maintenance remains in scope |

Specific URL patterns should use the existing `ANT` matcher style and avoid a broad `api:clocktower:*` grant except for `SUPER_ADMIN`.

### 12.4 Preset Roles

Preset roles are managed by RBAC resource synchronization and append missing grants only for managed preset roles.

| Role | Meaning | Default grants |
|---|---|---|
| `CLOCKTOWER_PLAYER` | Ordinary Clocktower participant. The same role can enter as spectator, claim/request a seat, become a player, chat, act, and view eligible replay. | Room read/membership/seat, game read/action/event-stream/replay, Clocktower chat read/send/conversation/read-state, script read |
| `CLOCKTOWER_STORYTELLER` | Room owner and storyteller. | Player grants plus board, room create/governance, game lifecycle, storyteller game operations, full eligible replay |
| `CLOCKTOWER_ADMIN` | Clocktower management operator. | Admin audit and rule-data operations. Normal room pages still use resolved room identity; omniscience is only for management audit pages |
| `SUPER_ADMIN` | Existing platform super admin. | Keeps existing platform-wide behavior and can receive all Clocktower managed permissions through existing super-admin bootstrap/sync behavior |

Do not add a separate `CLOCKTOWER_SPECTATOR` role in this design. A `CLOCKTOWER_PLAYER` can be only a spectator in a specific room until they claim or are assigned a seat. Spectator/player/storyteller identity is business state, not a global RBAC role.

### 12.5 Runtime Checks

Controller access follows two layers:

1. `DynamicAuthorizationManager` matches the request path and method to an enabled API permission rule.
2. The application service resolves the concrete context and runs business policy checks.

Examples:

| API | RBAC check | Business policy check |
|---|---|---|
| `GET /api/clocktower/rooms` | `api:clocktower:room:read` | Room visibility filters public/private/invited/banned rooms |
| `POST /api/clocktower/rooms/{roomId}/seats/{seatNo}/claim` | `api:clocktower:room:seat` | `SeatAssignmentPolicy` checks seating mode, ban, reservation, and room status |
| `POST /api/clocktower/rooms/{roomId}/games/start` | `api:clocktower:game:lifecycle` | User must be this room's storyteller and start conditions must pass |
| `GET /api/clocktower/chat/conversations/{conversationId}/messages` | `api:clocktower:chat:read` | `ClocktowerChatVisibilityPolicy` checks room/game/conversation visibility |
| `POST /api/clocktower/chat/conversations/{conversationId}/messages` | `api:clocktower:chat:send` | `ClocktowerChatSendPolicy` checks phase, viewer role, spectator status, and private-chat window |
| `GET /api/admin/clocktower/chat/**` | `api:admin:clocktower:audit` | Admin audit policy confirms admin or `SUPER_ADMIN`; normal room visibility does not apply |

This split must be reflected in tests: RBAC provider tests verify resources and role grants; service tests verify instance-level denial and visibility.

---

## 13. Frontend

| Page | Changes |
|---|---|
| Room list | Show visible rooms only; show current script, required players, occupied/reserved seats |
| Room lobby | Default spectator entry; show seat draft, invitations, ready, room public chat |
| Storyteller room controls | Board switch, invitations, release seat, kick, ban, start game |
| Player game page | Own identity, public seats, player public chat, private chat, actions, events |
| Storyteller grimoire | Grimoire, flow, rulings, private chat monitor, member governance |
| Spectator page | Public game view, read-only player public chat, spectator channel |
| Historical games | Player/storyteller accessible games only; disbanded game status visible |
| Management audit | Full room/game/chat/governance audit |

The `/clocktower/rooms/{roomId}/play` route should no longer assume every viewer has a seat. Entry routing should resolve viewer identity and show lobby/player/storyteller/spectator surfaces accordingly.

---

## 14. Migration Strategy

- Do not edit existing Flyway migrations.
- Add one new Flyway migration for this refactor's schema.
- Existing development Clocktower room data does not need compatibility.
- Existing board, script, role, rule, flow, ruling, and replay code should be reused where it still fits after switching to game-centric ids.
- Old Clocktower room APIs can be replaced by new APIs for frontend usage. Compatibility shims are optional and should not drive the design.
- RBAC resource changes should be implemented through `ClocktowerRbacResourceProvider` and existing RBAC resource synchronization by default.
- Accepted deviation: `V27__retire_old_clocktower_rbac_resources.sql` performs one-time retirement of old Clocktower RBAC rows because current synchronization does not retire stale provider resources or stale role grants. Do not use this as the default pattern for future RBAC resource evolution.

---

## 15. Validation Plan

Backend tests:

- Concurrent seat claim returns one success and one domain conflict.
- Concurrent invitation accept and board switch cannot produce an invalid occupied/reserved seat count.
- Concurrent start game requests create exactly one running game.
- Concurrent storyteller timeout and manual end/abort produce one final state.
- Concurrent message sends preserve unique per-conversation message sequences.
- Room visibility: public/private/member/invited/banned.
- Invitation: room-level, seat-level reservation, accept, decline, cancel, move, expire.
- Seat policies: open, approval required, invite only.
- Board switching blocked by occupied/reserved seat count.
- Start game blocked until all real users are accepted and ready.
- Game start creates immutable game seat snapshot.
- Running game blocks seat changes.
- Storyteller heartbeat timeout aborts game and disbands room only while running.
- Chat send policy by phase, day number, role, death state, and spectator status.
- Chat visibility policy for player, storyteller, spectator, and management audit.
- Replay access limited to game players and storyteller.
- `ClocktowerRbacResourceProvider` declares new room, game, chat, and admin API permissions without `api:im:*`.
- `CLOCKTOWER_PLAYER` role includes spectator-capable room/chat/game read grants but excludes storyteller/admin grants.
- Dynamic authorization matches representative Clocktower API paths to the intended permission codes.
- Service-level policy denies unauthorized access even when the caller has the coarse RBAC API permission.

Frontend tests:

- Room list counters and visibility.
- Lobby spectator default.
- Invitation accept/decline and reserved seats.
- Ready/start disabled states.
- Player, storyteller, spectator view rendering.
- Chat composer availability across phase and viewer mode.
- Historical game access for player/storyteller only.

Manual validation:

- Create room with board.
- Invite users by room and by seat.
- Accept invitations, ready up, start game.
- Verify public chat, private chat, spectator channel.
- Verify night chat restrictions.
- Abort by storyteller offline timeout.
- Confirm room disappears from lobby and game appears in history as mid-game disbanded.

---

## 16. Open Implementation Notes

- The implementation plan should split this into backend schema/domain, backend Clocktower integration, IM UI, room UI, game/replay UI, and audit tasks.
- Each implementation task should commit once.
- Use targeted tests first for changed modules.
- Do not start the project automatically after implementation; the user will run runtime testing.
