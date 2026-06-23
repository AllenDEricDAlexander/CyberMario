# Clocktower Room, IM, And Game Refactor Design

**Date:** 2026-06-23

**Goal:** Refactor Clocktower room, IM, and game lifecycle so a room is a long-lived space, a game is one playable round inside that room, and IM is a reusable channel/group/conversation/message capability that can be used by Clocktower and future modules.

**Status:** User-approved design direction. Implementation must be planned separately before code changes.

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

These patterns are justified because Room and IM are expected to be reused in other product areas. A direct implementation would hard-code Clocktower assumptions and make future reuse expensive.

---

## 4. Data Model

Exact column details can be finalized in the implementation plan, but the model boundaries are fixed here.

### 4.1 Generic Room Tables

`room_space`

- Long-lived room.
- Stores owner user id, visibility, status, context type, context id, created/updated metadata.
- For Clocktower, `context_type = CLOCKTOWER`.

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

### 11.3 Generic IM APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/im/channels/{channelId}` | Channel details |
| `GET` | `/api/im/channels/{channelId}/groups` | Groups in channel |
| `GET` | `/api/im/conversations/{conversationId}/messages` | Message history |
| `POST` | `/api/im/conversations` | Create conversation, including private one-to-one |
| `POST` | `/api/im/conversations/{conversationId}/messages` | Send message through policy |
| `POST` | `/api/im/conversations/{conversationId}/read` | Mark read |

### 11.4 Management Audit APIs

Management APIs can live under `/api/admin/clocktower/**` and `/api/admin/im/**`.

They must allow admin and `SUPER_ADMIN` to inspect all room, game, chat, spectator channel, invitation, member, kick, and ban records.

---

## 12. Frontend

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

## 13. Migration Strategy

- Do not edit existing Flyway migrations.
- Add one new Flyway migration for this refactor's schema.
- Existing development Clocktower room data does not need compatibility.
- Existing board, script, role, rule, flow, ruling, and replay code should be reused where it still fits after switching to game-centric ids.
- Old Clocktower room APIs can be replaced by new APIs for frontend usage. Compatibility shims are optional and should not drive the design.

---

## 14. Validation Plan

Backend tests:

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

## 15. Open Implementation Notes

- The implementation plan should split this into backend schema/domain, backend Clocktower integration, IM UI, room UI, game/replay UI, and audit tasks.
- Each implementation task should commit once.
- Use targeted tests first for changed modules.
- Do not start the project automatically after implementation; the user will run runtime testing.
