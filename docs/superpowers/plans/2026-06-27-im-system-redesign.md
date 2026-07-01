# IM System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy IM scaffolding with a reusable channel/group/DM IM core, DTO-only Java facades, reliable
outbox delivery, IM-owned WebSocket realtime, Clocktower adapter integration, and a frontend conversation experience.

**Architecture:** This is a direct replacement, not a compatibility refactor. The `im` package becomes a classic 3-layer
module: thin controllers/adapters call DTO-only facades, facades orchestrate service calls and mapping, services own
business rules and transactions, repositories own persistence. The core uses a Facade boundary, Strategy policies for
send/read rules, an Adapter for Clocktower context behavior, and a reserved `RealtimeRouter` abstraction for future
multi-node routing.

**Tech Stack:** Java 21, Spring Boot 3.5, WebFlux, Spring Data JPA, PostgreSQL/Flyway, Reactor WebSocket, JUnit 5,
AssertJ, Mockito, H2 PostgreSQL mode for fast local smoke tests, explicit real-PostgreSQL integration validation for
SQL/locking semantics, React 19, TypeScript 6, Ant Design 6, Ant Design X, Bun, Vite, Vitest.

---

## Direct Rewrite Decision

The old `im` implementation is treated as disposable scaffolding. Do not preserve the old `ImFacade` method shape, old
PO leakage, old `im_channel -> im_group -> im_conversation` identity model, old `im_read_state` table, old
participant-key branching, or old Clocktower chat service call flow. The new implementation must still live in the
current repository paths so the work can be executed and reviewed, but the implementation should follow the approved
redesign document instead of adapting around the current IM code.

The plan contains no Java, SQL, TypeScript, or shell implementation blocks. Any fenced blocks are pseudocode only and
must be translated into project code during execution.

## Scope Check

The specification covers backend schema, backend IM core, governance, reliable delivery, realtime, REST/RBAC, Clocktower
adaptation, and frontend chat surfaces. These pieces are tightly coupled by one reusable IM core and should remain one
implementation plan. Each task below is independently committable and leaves a testable slice behind.

## Design Pattern Decision

Use these patterns because they match explicit variation points in the redesign:

- Facade: `ImFacade`, `RoomFacade`, `DmFacade`, and `GovFacade` are the only upstream Java contracts and prevent
  PO/repository leakage.
- Strategy: `SendPolicy` and `VisibilityPolicy` vary by `context_type`, with default and Clocktower strategies.
- Adapter: Clocktower maps room/game chat into generic IM commands and supplies Clocktower policy rules without IM
  importing Clocktower packages.
- Repository: Spring Data JPA repositories stay internal to IM services.
- Mapper: facade-level mappers convert PO state to DTO views.

Do not introduce DDD aggregates, event-sourcing, Abstract Factory, deep command-handler chains, or a new framework
layer. The core logic is complex enough for Facade, Strategy, Adapter, and Mapper; direct services are clearer for
everything else.

## File Structure

### Backend Files To Create

- `be/src/main/resources/db/migration/V29__create_im_core_schema.sql` — one migration that drops legacy IM tables and
  creates the new IM core schema.
- `be/src/main/java/top/egon/mario/im/po/ImChannelPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImGroupPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImDmPairPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImMembershipPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImJoinRequestPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImConversationPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImConversationMemberPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImMessagePo.java`
- `be/src/main/java/top/egon/mario/im/po/ImOutboxPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImInboxPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImGlobalMutePo.java`
- `be/src/main/java/top/egon/mario/im/po/ImDmBlockPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImBanPo.java`
- `be/src/main/java/top/egon/mario/im/po/ImWsTicketPo.java`
- `be/src/main/java/top/egon/mario/im/po/enums/*` — enum types for surfaces, conversations, membership, messages,
  outbox, delivery, governance, and tickets.
- `be/src/main/java/top/egon/mario/im/repository/*Repository.java` — one repository per PO, package-internal use only.
- `be/src/main/java/top/egon/mario/im/facade/ImFacade.java`
- `be/src/main/java/top/egon/mario/im/facade/RoomFacade.java`
- `be/src/main/java/top/egon/mario/im/facade/DmFacade.java`
- `be/src/main/java/top/egon/mario/im/facade/GovFacade.java`
- `be/src/main/java/top/egon/mario/im/facade/dto/command/*`
- `be/src/main/java/top/egon/mario/im/facade/dto/query/*`
- `be/src/main/java/top/egon/mario/im/facade/dto/view/*`
- `be/src/main/java/top/egon/mario/im/facade/mapper/ImFacadeMapper.java`
- `be/src/main/java/top/egon/mario/im/service/ConversationService.java`
- `be/src/main/java/top/egon/mario/im/service/MessageService.java`
- `be/src/main/java/top/egon/mario/im/service/MembershipService.java`
- `be/src/main/java/top/egon/mario/im/service/InboxService.java`
- `be/src/main/java/top/egon/mario/im/service/OutboxService.java`
- `be/src/main/java/top/egon/mario/im/service/GovernanceService.java`
- `be/src/main/java/top/egon/mario/im/service/DmService.java`
- `be/src/main/java/top/egon/mario/im/service/ImTicketService.java`
- `be/src/main/java/top/egon/mario/im/service/ImException.java`
- `be/src/main/java/top/egon/mario/im/policy/ImPrincipal.java`
- `be/src/main/java/top/egon/mario/im/policy/ImAccessContext.java`
- `be/src/main/java/top/egon/mario/im/policy/SendPolicy.java`
- `be/src/main/java/top/egon/mario/im/policy/VisibilityPolicy.java`
- `be/src/main/java/top/egon/mario/im/policy/PolicyRegistry.java`
- `be/src/main/java/top/egon/mario/im/policy/DefaultSendPolicy.java`
- `be/src/main/java/top/egon/mario/im/policy/DefaultVisibilityPolicy.java`
- `be/src/main/java/top/egon/mario/im/realtime/ImFrame.java`
- `be/src/main/java/top/egon/mario/im/realtime/RealtimeRouter.java`
- `be/src/main/java/top/egon/mario/im/realtime/LocalRealtimeRouter.java`
- `be/src/main/java/top/egon/mario/im/realtime/ImConnectionRegistry.java`
- `be/src/main/java/top/egon/mario/im/realtime/OutboxDispatcher.java`
- `be/src/main/java/top/egon/mario/im/web/ImController.java`
- `be/src/main/java/top/egon/mario/im/web/ImWebSocketHandler.java`
- `be/src/main/java/top/egon/mario/im/web/ImWebSocketConfiguration.java`
- `be/src/main/java/top/egon/mario/im/web/ReactiveImSupport.java`
- `be/src/main/java/top/egon/mario/im/resource/ImRbacResourceProvider.java`
- `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerImAdapter.java`
- `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerSendPolicy.java`
- `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerVisibilityPolicy.java`
- `be/src/test/java/top/egon/mario/im/*Tests.java`
- `be/src/test/java/top/egon/mario/im/ImPostgresContractIT.java` — explicit real-PostgreSQL validation for V29 columns,
  partial indexes, JSONB, locking, SKIP LOCKED, and sequence concurrency.
- `be/src/test/java/top/egon/mario/im/ImPostgresBehaviorIT.java` — explicit real-PostgreSQL service validation for
  send/idempotency, seq concurrency, mark-read/unread, membership governance, DM freeze, and visibility rules.
- `be/src/test/java/top/egon/mario/clocktower/chat/*Tests.java`

### Backend Files To Replace Or Remove

- Replace every old `be/src/main/java/top/egon/mario/im/**` file with the new file set above.
- Replace old `be/src/test/java/top/egon/mario/im/ImFacadeTests.java` with DTO-only contract and behavior tests.
- Replace old Clocktower chat service internals in `be/src/main/java/top/egon/mario/clocktower/chat/**` so Clocktower
  calls the new facades through `ClocktowerImAdapter`.
- Keep `room_*` and `clocktower_*` schema and services unless a Clocktower chat adapter needs to read current room/game
  state.
- Do not edit `be/src/main/resources/db/migration/V26__create_room_im_clocktower_refactor_schema.sql`.
- Do not edit any existing migration file.

### Frontend Files To Create

- `fe/src/modules/im/imTypes.ts` — generic IM DTO and frame types.
- `fe/src/modules/im/imService.ts` — generic IM REST helpers and WebSocket ticket helper.
- `fe/src/modules/im/useImSocket.ts` — WebSocket lifecycle, frame dispatch, reconnect, and resync hook.
- `fe/src/modules/im/imMappers.ts` — generic IM view to chat workspace mapper.
- `fe/src/modules/im/components/ImSurfaceBrowser.tsx` — discoverable channel/group list with membership and join status.
- `fe/src/modules/im/components/ImJoinApplyControls.tsx` — join/apply/cancel/approve/reject controls for open and
  approval surfaces.
- `fe/src/modules/im/imTypes.test.ts`
- `fe/src/modules/im/imService.test.ts`
- `fe/src/modules/im/useImSocket.test.tsx`
- `fe/src/modules/im/imMappers.test.ts`
- `fe/src/modules/im/components/ImSurfaceBrowser.test.tsx`
- `fe/src/modules/im/components/ImJoinApplyControls.test.tsx`

### Frontend Files To Modify

- `fe/src/modules/clocktower/clocktowerTypes.ts`
- `fe/src/modules/clocktower/clocktowerService.ts`
- `fe/src/modules/clocktower/components/ClocktowerChatPanel.tsx`
- `fe/src/modules/clocktower/components/ClocktowerConversationList.tsx`
- `fe/src/modules/clocktower/components/ClocktowerMessageList.tsx`
- `fe/src/modules/clocktower/components/ClocktowerChatPanel.test.tsx`
- `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- `fe/src/modules/clocktower/ClocktowerAdminAuditPage.tsx`
- `fe/src/app/routes.tsx` only if a standalone platform IM page is added during execution.
- `fe/src/layouts/AdminLayout/menu.tsx` only if a standalone platform IM page is added during execution.

---

## Task 1: Schema Migration V29

**Files:**

- Create: `be/src/main/resources/db/migration/V29__create_im_core_schema.sql`
- Create: `be/src/test/java/top/egon/mario/im/ImCoreSchemaMigrationTests.java`

- [ ] **Step 1: Write the failing schema text test**

Pseudocode:

```pseudocode
Load V29 migration text.
Assert the migration first drops these legacy IM tables:
  im_read_state
  im_message
  im_conversation_member
  im_conversation
  im_group
  im_channel
Assert the migration creates these replacement tables:
  im_channel
  im_group
  im_dm_pair
  im_membership
  im_join_request
  im_conversation
  im_conversation_member
  im_message
  im_outbox
  im_inbox
  im_global_mute
  im_dm_block
  im_ban
  im_ws_ticket
Assert every table has audit columns:
  metadata_json
  created_at
  updated_at
  created_by
  updated_by
  version
  deleted
Assert every table declares the required spec columns:
  im_channel:
    id
    context_type
    context_id
    channel_key
    name
    owner_user_id
    visibility
    join_policy
    status
    announcement
    main_conversation_id
    member_count
    last_active_at
  im_group:
    id
    channel_id
    context_type
    context_id
    group_key
    name
    owner_user_id
    join_policy
    status
    announcement
    conversation_id
    member_count
    last_active_at
  im_dm_pair:
    id
    user_lo_id
    user_hi_id
    conversation_id
    frozen
  im_membership:
    id
    surface_type
    surface_id
    user_id
    member_role
    status
    muted_until
    joined_at
  im_join_request:
    id
    surface_type
    surface_id
    user_id
    status
    decided_by
    decided_at
    decision_reason
  im_conversation:
    id
    conversation_type
    owner_surface_type
    owner_surface_id
    context_type
    context_id
    message_seq
    last_message_id
    last_message_at
    last_active_at
    status
  im_conversation_member:
    id
    conversation_id
    user_id
    last_read_seq
    delivery_mode
    muted
    status
  im_message:
    id
    conversation_id
    sender_user_id
    message_seq
    client_msg_id
    message_type
    content
    payload_json
    status
    sent_at
    edited_at
    deleted_at
  im_outbox:
    id
    conversation_id
    message_id
    message_seq
    event_type
    status
    available_at
    attempts
    last_error
  im_inbox:
    id
    user_id
    conversation_id
    message_id
    message_seq
    read
  im_global_mute:
    id
    user_id
    scope_type
    scope_id
    expires_at
    reason
    status
  im_dm_block:
    id
    blocker_user_id
    blocked_user_id
    status
    reason
  im_ban:
    id
    surface_type
    surface_id
    user_id
    actor_user_id
    reason
    expires_at
    status
  im_ws_ticket:
    id
    token_hash
    user_id
    roles_json
    expires_at
    consumed_at
    status
Assert the migration contains one partial unique constraint for standalone groups.
Assert the migration contains outbox dispatcher index by status and available time.
Assert the migration contains message idempotency unique constraint by conversation, sender, and client message id.
Assert no legacy im_read_state table is recreated.
```

- [ ] **Step 2: Run the schema text test and verify it fails**

Run: `cd be && ./mvnw -Dtest=ImCoreSchemaMigrationTests test`

Expected: FAIL because `V29__create_im_core_schema.sql` does not exist.

- [ ] **Step 3: Write the migration**

Pseudocode:

```pseudocode
Begin migration V29.
Drop legacy IM tables in dependency order.
Create im_channel:
  columns from spec section 4.1
  unique context_type, context_id, channel_key
  index last_active_at
Create im_group:
  columns from spec section 4.1
  nullable channel_id
  unique channel_id and group_key when channel_id exists
  partial unique context_type, context_id, group_key when channel_id is null
Create im_dm_pair:
  id
  user_lo_id
  user_hi_id
  conversation_id
  frozen flag
  unique user_lo_id, user_hi_id
Create im_membership:
  surface_type, surface_id, user_id
  role, status, muted_until, joined_at
  unique surface_type, surface_id, user_id
  indexes for user/status and surface/status
Create im_join_request:
  surface identity, user, status, decision columns
  unique pending request per surface and user
Create im_conversation:
  conversation_type
  owner_surface_type and owner_surface_id
  context fields
  sequence and last-message fields
  last_active_at
  status
  unique owner_surface_type, owner_surface_id, conversation_type
Create im_conversation_member:
  conversation_id, user_id
  last_read_seq
  delivery_mode
  muted
  status
  unique conversation_id, user_id
  index user_id, status
Create im_message:
  conversation_id, sender_user_id, message_seq, client_msg_id
  type, content, payload, status, timestamps
  unique conversation_id, message_seq
  unique conversation_id, sender_user_id, client_msg_id when client_msg_id exists
Create im_outbox:
  conversation/message identity
  event_type, status, available_at, attempts, last_error
  index status, available_at
Create im_inbox:
  user_id, conversation_id, message identity, read flag
  unique user_id, conversation_id, message_id
  index user_id, message_seq
Create im_global_mute:
  user_id, scope fields, expires_at, reason
  index active global mute lookup
Create im_dm_block:
  blocker, blocked, status, reason
  unique blocker, blocked, status for active rows
Create im_ban:
  surface identity, user, actor, reason, expires_at, status
Create im_ws_ticket:
  ticket token hash, user id, roles json, expires_at, consumed_at, status
Create all listed indexes from spec section 4.7.
End migration.
```

- [ ] **Step 4: Run the schema text test and verify it passes**

Run: `cd be && ./mvnw -Dtest=ImCoreSchemaMigrationTests test`

Expected: PASS.

- [ ] **Step 5: Run Flyway migration smoke test**

Run: `cd be && ./mvnw -Dtest=ImCoreSchemaMigrationTests test`

Expected: PASS with H2-compatible migration smoke assertions. If H2 cannot execute PostgreSQL partial-index syntax, keep
a text assertion for the PostgreSQL-specific fragment and isolate execution smoke to H2-compatible table existence
checks.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/resources/db/migration/V29__create_im_core_schema.sql be/src/test/java/top/egon/mario/im/ImCoreSchemaMigrationTests.java && git commit -m "feat: create im core schema"`

Expected: commit succeeds with only schema and schema-test files staged.

## Task 2: PO, Enum, Repository, And Exception Layer

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/po/*.java`
- Create: `be/src/main/java/top/egon/mario/im/po/enums/*.java`
- Create: `be/src/main/java/top/egon/mario/im/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/im/service/ImException.java`
- Create: `be/src/test/java/top/egon/mario/im/ImPersistenceMappingTests.java`

- [ ] **Step 1: Write failing mapping tests**

Pseudocode:

```pseudocode
Start Spring JPA test context.
For each IM PO:
  create a valid minimal row through repository
  flush and clear persistence context
  reload by id and deleted=false
  assert core fields round-trip
For enum columns:
  persist one row with each enum category used by behavior tests
  assert database stores string names and reload returns enum values
For locked conversation query:
  create a conversation
  call repository method that locks by id and deleted=false
  assert returned conversation id matches
For outbox claim query:
  create multiple pending outbox rows
  call repository method that claims pending rows for dispatch
  assert returned rows are pending and ordered by available time
```

- [ ] **Step 2: Run mapping tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImPersistenceMappingTests test`

Expected: FAIL because new PO and repository classes do not exist.

- [ ] **Step 3: Replace old IM persistence model**

Pseudocode:

```pseudocode
Delete old IM PO fields that reference channel_id/group_id/scope_type/participant_key on conversation identity.
Create one PO per V29 table.
Every PO extends the shared auditable base.
Use enum fields for bounded status/type columns.
Use JSON field mapping only where the schema uses JSONB.
Keep repositories package-visible by convention through IM package ownership.
Add repository methods:
  find active channel by context and channel key
  find group by channel and key
  find standalone group by context and key
  find dm pair by ordered users
  find locked conversation by id
  find conversation by owner surface and type
  find message by idempotency key
  page messages by conversation and sequence
  find active conversation member by conversation and user
  list active conversation members by user
  claim pending outbox rows
  find active mute, ban, block records
Create ImException with stable error code and readable message.
```

- [ ] **Step 4: Run mapping tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImPersistenceMappingTests test`

Expected: PASS.

- [ ] **Step 5: Compile backend**

Run: `cd be && ./mvnw -DskipTests compile`

Expected: PASS, proving old IM imports have either been replaced or are now the next intentional compile targets.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im be/src/test/java/top/egon/mario/im/ImPersistenceMappingTests.java && git commit -m "feat: replace im persistence model"`

Expected: commit succeeds.

## Task 3: DTO-Only Facade Contract

**Files:**

- Create/replace: `be/src/main/java/top/egon/mario/im/facade/ImFacade.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/RoomFacade.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/DmFacade.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/GovFacade.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/dto/command/*.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/dto/query/*.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/dto/view/*.java`
- Create: `be/src/main/java/top/egon/mario/im/facade/mapper/ImFacadeMapper.java`
- Create: `be/src/test/java/top/egon/mario/im/ImFacadeContractTests.java`

- [ ] **Step 1: Write failing facade contract tests**

Pseudocode:

```pseudocode
Reflect over public methods in im.facade package.
Assert no public method returns a type from im.po.
Assert no public method accepts a type from im.po or im.repository.
Assert every public facade method accepts either a command, query, or simple id plus ImPrincipal where identity is needed.
Assert command packages contain:
  SendMessageCommand
  CreateChannelCommand
  CreateGroupCommand
  JoinCommand
  ApproveCommand
  RejectJoinCommand
  CancelJoinCommand
  LeaveCommand
  MarkReadCommand
  MuteUserCommand
  GlobalMuteCommand
  BlockUserCommand
  OpenDmCommand
  AnnounceCommand
  BanUserCommand
  MintWsTicketCommand
Assert query packages contain:
  HistoryQuery
  ListConversationsQuery
  ListChannelsQuery
  ListGroupsQuery
Assert view packages contain:
  MessageView
  ConversationView
  ChannelView
  GroupView
  UnreadView
  JoinResultView
  WsTicketView
Assert ImPrincipal contains user id and caller-supplied role/context data only.
```

- [ ] **Step 2: Run contract tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImFacadeContractTests test`

Expected: FAIL because the old facade leaks PO types and DTO packages do not exist.

- [ ] **Step 3: Define DTO contract**

Pseudocode:

```pseudocode
Create command records with only input fields needed by facade operations.
Create query records with pagination, conversation id, surface identity, and principal fields where needed.
Create view records with stable response fields:
  ids
  type/status strings
  names and announcements
  message sequence
  unread count
  last message summary
  timestamps
  governance flags
Keep DTOs free of JPA annotations.
Keep DTOs free of Spring Security types.
Define ImPrincipal as caller-provided user id, role codes, context type, and optional attributes.
```

- [ ] **Step 4: Implement facade shells and mapper shells**

Pseudocode:

```pseudocode
Create ImFacade for send, history, markRead, listConversations.
Create RoomFacade for create channel, create group, apply join, approve join, reject join, cancel join, leave, list channels, list groups.
Add rejectJoin and cancelJoin to RoomFacade so every im_join_request status has a phase-1 behavior.
Create DmFacade for open DM, block, unblock.
Create GovFacade for mute, global mute, announce, ban.
Each facade delegates to services and maps PO state into views.
Facade mutations use retry-on-unique-conflict up to three attempts.
Facade ensure-style operations use independent transactions where idempotent creation may race.
```

- [ ] **Step 5: Run contract tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImFacadeContractTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/facade be/src/main/java/top/egon/mario/im/policy/ImPrincipal.java be/src/test/java/top/egon/mario/im/ImFacadeContractTests.java && git commit -m "feat: define im facade contract"`

Expected: commit succeeds.

## Task 4: Policy Registry And Default Access Rules

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/policy/ImAccessContext.java`
- Create: `be/src/main/java/top/egon/mario/im/policy/SendPolicy.java`
- Create: `be/src/main/java/top/egon/mario/im/policy/VisibilityPolicy.java`
- Create: `be/src/main/java/top/egon/mario/im/policy/PolicyRegistry.java`
- Create: `be/src/main/java/top/egon/mario/im/policy/DefaultSendPolicy.java`
- Create: `be/src/main/java/top/egon/mario/im/policy/DefaultVisibilityPolicy.java`
- Create: `be/src/test/java/top/egon/mario/im/ImPolicyRegistryTests.java`

- [ ] **Step 1: Write failing policy tests**

Pseudocode:

```pseudocode
Create default channel main conversation context.
Assert any authenticated or anonymous principal can read channel main history.
Assert only active channel member can post channel main messages.
Create group conversation context.
Assert only active group member can read and post.
Create DM conversation context.
Assert either pair participant can read and post when pair is not frozen.
Assert non-participant cannot read or post DM.
Assert pending, left, banned, muted, and globally muted members cannot post.
Assert archived conversations cannot be posted to.
Assert registry returns default policy for unknown context type.
Assert registry can return a registered Clocktower policy for CLOCKTOWER_ROOM without IM importing Clocktower classes.
```

- [ ] **Step 2: Run policy tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImPolicyRegistryTests test`

Expected: FAIL because policy classes are not implemented.

- [ ] **Step 3: Implement policy registry**

Pseudocode:

```pseudocode
PolicyRegistry receives all SendPolicy beans and VisibilityPolicy beans.
Each policy declares supported context type or default marker.
Resolve policy by exact context type.
If no exact policy exists, use default policy.
Reject startup when more than one policy owns the same exact context type.
```

- [ ] **Step 4: Implement default send and visibility policy**

Pseudocode:

```pseudocode
Default visibility:
  if conversation is CHANNEL_MAIN and channel active, allow read
  if conversation is GROUP, require active group membership
  if conversation is DM, require requester is one of the ordered pair users
  otherwise deny
Default send:
  reject archived conversation
  reject global mute that is active for platform or context scope
  for CHANNEL_MAIN, require active channel membership and no per-user mute or ban
  for GROUP, require active group membership and no per-user mute or ban
  for DM, require pair participant and pair not frozen
  otherwise deny
```

- [ ] **Step 5: Run policy tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImPolicyRegistryTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/policy be/src/test/java/top/egon/mario/im/ImPolicyRegistryTests.java && git commit -m "feat: add im policy registry"`

Expected: commit succeeds.

## Task 5: Channel And Group Surface Services

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/ConversationService.java`
- Create: `be/src/main/java/top/egon/mario/im/service/MembershipService.java`
- Create: `be/src/test/java/top/egon/mario/im/ImSurfaceServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/im/ImSurfaceDiscoveryTests.java`

- [ ] **Step 1: Write failing surface tests**

Pseudocode:

```pseudocode
Create channel with context, key, owner, public visibility, open join policy.
Assert channel has exactly one CHANNEL_MAIN conversation.
Assert owner has active OWNER membership on channel.
Assert owner has active conversation member row.
Create same channel again.
Assert returned channel id is stable and no duplicate conversation exists.
Create group under channel.
Assert group has exactly one GROUP conversation.
Assert group owner has active OWNER membership and conversation member row.
Create standalone group with null channel id.
Assert standalone unique identity uses context type, nullable context id, and group key.
Assert group cannot nest under another group.
Create public channel A, public channel B, and archived channel C in the same context.
List channels by context.
Assert A and B are returned with join policy, member count, announcement, last active time, and caller membership status.
Assert archived channel C is not returned.
Assert listing channels does not create membership or conversation member rows for the caller.
Create channel group G1, channel group G2, and standalone group SG1.
List groups by channel id.
Assert only G1 and G2 are returned, ordered by last active time then id.
List standalone groups by context.
Assert SG1 is returned and channel-scoped groups are excluded.
Assert group list items expose join policy and caller membership status without allowing non-members to read group history.
```

- [ ] **Step 2: Run surface tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImSurfaceServiceTests,ImSurfaceDiscoveryTests test`

Expected: FAIL because services and discovery queries are not implemented.

- [ ] **Step 3: Implement channel creation and discovery**

Pseudocode:

```pseudocode
Validate channel command fields.
Find existing active channel by context and channel key.
If existing channel exists, return it with main conversation.
Create channel row with owner, visibility PUBLIC, join policy, active status, member count one.
Create CHANNEL_MAIN conversation owned by channel.
Set channel main conversation id.
Create owner membership for channel.
Create owner conversation member for main conversation.
Return channel view.
List channels:
  require context type input; context id may be null for platform-global channels
  load ACTIVE and non-deleted PUBLIC channels for the context
  join or batch-load caller membership rows when a principal is supplied
  map each row to ChannelView with membershipStatus, memberRole, canRead=true, canPost based on active membership
  sort by last_active_at descending, then id descending
  do not create membership, conversation member, inbox, or read cursor rows during discovery
```

- [ ] **Step 4: Implement group creation and discovery**

Pseudocode:

```pseudocode
Validate group command fields.
If channel id exists:
  load active channel
  use channel context by default
  find group by channel id and group key
If channel id is null:
  require context type and group key
  find standalone group by context and group key
If existing group exists, return it with conversation.
Create group row with nullable channel id, owner, join policy, active status, member count one.
Create GROUP conversation owned by group.
Set group conversation id.
Create owner membership for group.
Create owner conversation member for group conversation.
Return group view.
List groups:
  if channel id is supplied, load ACTIVE non-deleted groups under that channel
  if channel id is absent, load ACTIVE non-deleted standalone groups by context type and nullable context id
  join or batch-load caller membership rows when a principal is supplied
  map each row to GroupView with join policy, membershipStatus, memberRole, canRead based on active membership, and canPost based on active membership
  sort by last_active_at descending, then id descending
  do not create membership, conversation member, inbox, or read cursor rows during discovery
```

- [ ] **Step 5: Run surface tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImSurfaceServiceTests,ImSurfaceDiscoveryTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/ConversationService.java be/src/main/java/top/egon/mario/im/service/MembershipService.java be/src/test/java/top/egon/mario/im/ImSurfaceServiceTests.java be/src/test/java/top/egon/mario/im/ImSurfaceDiscoveryTests.java && git commit -m "feat: add im channel and group surfaces"`

Expected: commit succeeds.

## Task 6: Join, Approve, Reject, Cancel, Leave, And Membership Governance

**Files:**

- Modify: `be/src/main/java/top/egon/mario/im/service/MembershipService.java`
- Modify: `be/src/main/java/top/egon/mario/im/facade/RoomFacade.java`
- Create: `be/src/test/java/top/egon/mario/im/ImMembershipWorkflowTests.java`

- [ ] **Step 1: Write failing membership workflow tests**

Pseudocode:

```pseudocode
For OPEN channel:
  apply join as non-member
  assert active membership is created immediately
  assert conversation member row is created
  assert join result status is ACTIVE
For APPROVAL group:
  apply join as non-member
  assert pending join request is created
  assert membership is pending or absent from active conversation members
  approve as owner
  assert membership is active
  assert conversation member row is active
  assert member count increases once
For duplicate apply:
  assert second apply returns existing pending or active state
For rejected approval request:
  apply join to APPROVAL group as user A
  reject as owner with a reason
  assert join request status is REJECTED with decided_by and decided_at
  assert user A has no active conversation member row
  assert user A can apply again and receives a new or reactivated PENDING request
For cancelled approval request:
  apply join to APPROVAL group as user A
  cancel as user A
  assert join request status is CANCELLED
  assert owner cannot later approve the cancelled request
  assert member count is unchanged
For leave:
  leave active membership
  assert membership status is LEFT
  assert conversation member status is LEFT
  assert member count decreases once
For banned user:
  assert apply join is rejected with IM_MEMBER_BANNED
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImMembershipWorkflowTests test`

Expected: FAIL because join workflow is not implemented.

- [ ] **Step 3: Implement apply join**

Pseudocode:

```pseudocode
Load surface by surface type and id.
If active membership exists, return ACTIVE result.
If banned membership exists, reject with IM_MEMBER_BANNED.
If join policy is OPEN:
  upsert active membership with MEMBER role
  create or reactivate conversation member
  increment member count only on transition into active state
  return ACTIVE result
If join policy is APPROVAL:
  create or return pending join request
  create pending membership only if the service uses membership to track pending state
  return PENDING result
```

- [ ] **Step 4: Implement approve, reject, cancel, and leave**

Pseudocode:

```pseudocode
Approve:
  load pending request by surface and user
  require approver has OWNER or ADMIN membership on same surface
  mark request APPROVED with approver and decision time
  create or activate membership
  create or reactivate conversation member
  increment member count only on new active transition
  return ACTIVE join result
Reject:
  load pending request by surface and user
  require actor has OWNER or ADMIN membership on same surface
  mark request REJECTED with actor, decision time, and optional reason
  leave membership absent or non-active
  ensure conversation member is absent or LEFT
  return REJECTED join result
Cancel:
  load pending request by surface and requester user
  require requester is the same user as the pending request
  mark request CANCELLED with requester and decision time
  leave membership absent or non-active
  ensure conversation member is absent or LEFT
  return CANCELLED join result
Leave:
  load active membership by surface and user
  reject owner leaving when no replacement owner exists
  mark membership LEFT and set leave time
  mark conversation member LEFT
  decrement member count once
```

- [ ] **Step 5: Run membership workflow tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImMembershipWorkflowTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/MembershipService.java be/src/main/java/top/egon/mario/im/facade/RoomFacade.java be/src/test/java/top/egon/mario/im/ImMembershipWorkflowTests.java && git commit -m "feat: add im membership workflow"`

Expected: commit succeeds.

## Task 7: DM Pair, Open DM, Block, And Unblock

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/DmService.java`
- Modify: `be/src/main/java/top/egon/mario/im/facade/DmFacade.java`
- Create: `be/src/test/java/top/egon/mario/im/ImDmServiceTests.java`

- [ ] **Step 1: Write failing DM tests**

Pseudocode:

```pseudocode
Open DM for user A and user B.
Assert pair stores lower user id and higher user id.
Assert exactly one DM conversation exists.
Assert exactly two active conversation member rows exist.
Open same DM with reversed users.
Assert same pair and conversation are returned.
Attempt open DM with same user on both sides.
Assert IM_DM_SELF_DENIED.
Block B by A.
Assert block row is active.
Assert pair frozen is true.
Assert neither A nor B can send new DM messages.
Unblock A's block.
Assert pair unfreezes only when no active block remains in either direction.
Assert prior history remains readable while frozen.
```

- [ ] **Step 2: Run DM tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImDmServiceTests test`

Expected: FAIL because DM service is not implemented.

- [ ] **Step 3: Implement open DM**

Pseudocode:

```pseudocode
Validate requester and target user.
Reject self DM.
Normalize ordered pair as lower and higher user id.
Find existing pair by ordered users.
If pair exists, return its conversation view.
Create pair row with frozen false.
Create DM conversation owned by pair.
Create two conversation member rows with delivery mode INBOX.
Set pair conversation id.
Return conversation view.
```

- [ ] **Step 4: Implement block and unblock**

Pseudocode:

```pseudocode
Block:
  validate blocker and blocked users
  normalize pair and ensure pair exists
  create or reactivate block row for blocker -> blocked
  set pair frozen true
Unblock:
  mark blocker -> blocked row inactive
  count active blocks in either direction for the pair
  if count is zero, set pair frozen false
```

- [ ] **Step 5: Run DM tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImDmServiceTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/DmService.java be/src/main/java/top/egon/mario/im/facade/DmFacade.java be/src/test/java/top/egon/mario/im/ImDmServiceTests.java && git commit -m "feat: add im dm workflow"`

Expected: commit succeeds.

## Task 8: Send, History, Mark Read, And Conversation List

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/MessageService.java`
- Modify: `be/src/main/java/top/egon/mario/im/service/ConversationService.java`
- Modify: `be/src/main/java/top/egon/mario/im/facade/ImFacade.java`
- Create: `be/src/test/java/top/egon/mario/im/ImMessageServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/im/ImConversationListTests.java`

- [ ] **Step 1: Write failing message tests**

Pseudocode:

```pseudocode
Create channel main conversation and active member.
Send message with client message id.
Assert message sequence is one.
Assert conversation message sequence is one.
Assert last message fields update.
Send same client message id again.
Assert same message view is returned and no second row exists.
Send three different messages.
Assert sequences are one, two, three with no gaps.
Simulate two concurrent sends against the same conversation.
Assert final message sequences are unique and gap-free.
Attempt send as non-member to channel main.
Assert IM_SEND_DENIED.
Read channel main history as non-member.
Assert public-read history succeeds.
Read group history as non-member.
Assert IM_HISTORY_FORBIDDEN.
Mark read at sequence three, then mark read at sequence one.
Assert cursor remains at three.
List conversations for user.
Assert active memberships appear sorted by last activity.
Assert unread count equals conversation sequence minus last read sequence.
```

- [ ] **Step 2: Run message tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImMessageServiceTests,ImConversationListTests test`

Expected: FAIL because message service is not implemented.

- [ ] **Step 3: Implement send**

Pseudocode:

```pseudocode
Validate command fields.
Lock conversation row.
Load sender principal.
If message with same conversation, sender, and client message id exists:
  return existing message view without mutating conversation or outbox
Build access context from conversation, surface, membership, mute, ban, global mute, and DM frozen state.
Ask policy registry for send policy by conversation context type.
Reject when policy denies.
Compute next sequence as locked conversation sequence plus one.
Insert message row with next sequence.
Update conversation sequence, last message id, last message time, last active time.
Write MESSAGE_CREATED outbox row.
Ask inbox service to fan out rows for INBOX members.
Return message view.
```

- [ ] **Step 4: Implement history and mark read**

Pseudocode:

```pseudocode
History:
  load conversation
  build visibility access context
  ask policy registry for visibility policy
  reject when policy denies
  return page ordered by message sequence
Mark read:
  lock conversation
  require active conversation member for requester
  clamp requested read sequence between zero and conversation sequence
  advance last_read_seq only forward
  update inbox read flags for requester up to target sequence
  write READ_UPDATED outbox row
  return unread view
```

- [ ] **Step 5: Implement conversation list**

Pseudocode:

```pseudocode
Load active conversation members for user.
Join each member to conversation and last message.
Compute unread as conversation message sequence minus member last read sequence.
Return conversation views sorted by last active time descending, then conversation id descending.
Do not include public channels where the user is not a member.
```

- [ ] **Step 6: Run message and list tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImMessageServiceTests,ImConversationListTests test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/MessageService.java be/src/main/java/top/egon/mario/im/service/ConversationService.java be/src/main/java/top/egon/mario/im/facade/ImFacade.java be/src/test/java/top/egon/mario/im/ImMessageServiceTests.java be/src/test/java/top/egon/mario/im/ImConversationListTests.java && git commit -m "feat: add im messaging core"`

Expected: commit succeeds.

## Task 9: Governance Operations

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/GovernanceService.java`
- Modify: `be/src/main/java/top/egon/mario/im/facade/GovFacade.java`
- Modify: `be/src/main/java/top/egon/mario/im/policy/DefaultSendPolicy.java`
- Create: `be/src/test/java/top/egon/mario/im/ImGovernanceServiceTests.java`

- [ ] **Step 1: Write failing governance tests**

Pseudocode:

```pseudocode
Owner mutes member on channel until a future time.
Assert member cannot send while muted.
Move mute expiry into past.
Assert member can send again.
Apply global mute for platform scope.
Assert user cannot send to any channel, group, or DM while mute is active.
Ban group member.
Assert membership status is BANNED.
Assert conversation member is not active.
Assert banned user cannot rejoin.
Set channel announcement.
Assert channel view includes announcement.
Set group announcement.
Assert group view includes announcement.
Reject governance action when actor is not OWNER or ADMIN for the surface.
```

- [ ] **Step 2: Run governance tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImGovernanceServiceTests test`

Expected: FAIL because governance service is not implemented.

- [ ] **Step 3: Implement mute, global mute, ban, and announcement**

Pseudocode:

```pseudocode
For surface-level governance:
  load actor membership on surface
  require role OWNER or ADMIN
Mute:
  set membership muted_until
Global mute:
  create or update global mute by user and scope
Ban:
  mark membership BANNED
  mark conversation member LEFT
  create ban audit row
  reduce member count when active membership transitions away
Announcement:
  update channel or group announcement text
  update modified audit fields
```

- [ ] **Step 4: Wire governance checks into send policy context**

Pseudocode:

```pseudocode
When building send access context:
  include membership status
  include membership muted_until
  include active global mute state for platform and context scope
  include active ban state
Default send policy denies when any governance restriction is active.
```

- [ ] **Step 5: Run governance tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImGovernanceServiceTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/GovernanceService.java be/src/main/java/top/egon/mario/im/facade/GovFacade.java be/src/main/java/top/egon/mario/im/policy/DefaultSendPolicy.java be/src/test/java/top/egon/mario/im/ImGovernanceServiceTests.java && git commit -m "feat: add im governance controls"`

Expected: commit succeeds.

## Task 10: Inbox, Outbox, Dispatcher, And Realtime Router

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/InboxService.java`
- Create: `be/src/main/java/top/egon/mario/im/service/OutboxService.java`
- Create: `be/src/main/java/top/egon/mario/im/realtime/RealtimeRouter.java`
- Create: `be/src/main/java/top/egon/mario/im/realtime/LocalRealtimeRouter.java`
- Create: `be/src/main/java/top/egon/mario/im/realtime/ImConnectionRegistry.java`
- Create: `be/src/main/java/top/egon/mario/im/realtime/OutboxDispatcher.java`
- Create: `be/src/test/java/top/egon/mario/im/ImOutboxDispatcherTests.java`
- Create: `be/src/test/java/top/egon/mario/im/ImInboxServiceTests.java`

- [ ] **Step 1: Write failing inbox/outbox tests**

Pseudocode:

```pseudocode
Create DM conversation with two INBOX members.
Send message.
Assert one outbox row is pending.
Assert inbox rows are created for both active members.
Mark read for receiver.
Assert receiver inbox rows up to read sequence are marked read.
Create large group configured for CURSOR delivery.
Send message.
Assert no inbox rows are created.
Assert unread is still computed from sequence cursor.
Create pending outbox rows.
Dispatch one batch with fake realtime router succeeding.
Assert rows become DISPATCHED.
Create pending outbox row and fake realtime router throwing an error.
Assert row becomes FAILED or PENDING with attempts incremented and future available time according to retry policy.
Run two dispatcher workers against pending rows.
Assert same outbox row is not delivered twice.
```

- [ ] **Step 2: Run inbox/outbox tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImInboxServiceTests,ImOutboxDispatcherTests test`

Expected: FAIL because inbox/outbox services are not implemented.

- [ ] **Step 3: Implement inbox service**

Pseudocode:

```pseudocode
Choose delivery mode:
  DM always INBOX
  groups and channel members use INBOX when active member count is at or below configured threshold
  otherwise CURSOR
On message created:
  load active conversation members with INBOX delivery mode
  create inbox row for each member and message
On mark read:
  update requester inbox rows at or below target sequence as read
```

- [ ] **Step 4: Implement outbox service and dispatcher**

Pseudocode:

```pseudocode
When message or read event occurs inside business transaction:
  insert outbox row with PENDING status
Dispatcher cycle:
  claim pending rows ordered by available time with row lock and skip locked semantics
  for each row:
    load message/conversation/member recipient data
    build realtime frame
    call realtime router
    on success mark DISPATCHED
    on failure increment attempts, store error summary, compute next available time
    after max attempts mark FAILED
Keep dispatcher disabled unless im.realtime.dispatcher.enabled is true in configuration.
```

- [ ] **Step 5: Implement local realtime router interface**

Pseudocode:

```pseudocode
RealtimeRouter exposes delivery by user id and by conversation id.
LocalRealtimeRouter uses in-memory connection registry.
Connection registry maps user id to bounded connection sinks.
When target user has no local connection, delivery is a no-op success.
Reserve interface for future Redis router without implementing Redis routing.
```

- [ ] **Step 6: Run inbox/outbox tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImInboxServiceTests,ImOutboxDispatcherTests test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/InboxService.java be/src/main/java/top/egon/mario/im/service/OutboxService.java be/src/main/java/top/egon/mario/im/realtime be/src/test/java/top/egon/mario/im/ImInboxServiceTests.java be/src/test/java/top/egon/mario/im/ImOutboxDispatcherTests.java && git commit -m "feat: add im reliable delivery"`

Expected: commit succeeds.

## Task 11: WebSocket Ticket, Gateway, And Frames

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/service/ImTicketService.java`
- Create: `be/src/main/java/top/egon/mario/im/realtime/ImFrame.java`
- Create: `be/src/main/java/top/egon/mario/im/web/ImWebSocketHandler.java`
- Create: `be/src/main/java/top/egon/mario/im/web/ImWebSocketConfiguration.java`
- Modify: `be/src/main/java/top/egon/mario/im/web/ImController.java`
- Create: `be/src/test/java/top/egon/mario/im/ImWebSocketGatewayTests.java`

- [ ] **Step 1: Write failing WebSocket tests**

Pseudocode:

```pseudocode
Mint ticket as authenticated principal.
Assert returned ticket has expiry and no raw database token value is exposed.
Connect websocket with valid ticket.
Assert connection resolves to ImPrincipal and registers sink.
Connect websocket with expired ticket.
Assert connection is rejected.
Connect websocket with already consumed ticket.
Assert connection is rejected.
Send PING frame.
Assert PONG frame is returned.
Send SEND_MESSAGE frame with client message id.
Assert facade send is called and SEND_ACK frame includes message view.
Send MARK_READ frame.
Assert facade markRead is called and READ_UPDATED frame is returned.
Subscribe to conversation.
Assert local connection registry tracks subscription.
Force outbound queue overflow.
Assert buffered frames are dropped and RESYNC frame is emitted.
```

- [ ] **Step 2: Run WebSocket tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImWebSocketGatewayTests test`

Expected: FAIL because ticket and WebSocket gateway are not implemented.

- [ ] **Step 3: Implement ticket service**

Pseudocode:

```pseudocode
Mint ticket:
  require authenticated principal
  generate random token
  store token hash, user id, role/context payload, expiry, unused status
  return raw token and expiry once
Consume ticket:
  hash presented token
  load active unexpired unused ticket
  mark consumed
  return ImPrincipal payload
```

- [ ] **Step 4: Implement frame model and handler**

Pseudocode:

```pseudocode
Client frames:
  SEND_MESSAGE
  MARK_READ
  SUBSCRIBE
  PING
Server frames:
  SEND_ACK
  MESSAGE_PUSH
  READ_UPDATED
  RESYNC
  PONG
On connection:
  resolve ticket
  register connection sink with bounded queue
  route inbound frames to facade methods on blocking scheduler
  serialize server frames as JSON
  unregister connection on close
On overflow:
  clear queued frames
  enqueue RESYNC with last known conversation id and sequence hint
```

- [ ] **Step 5: Run WebSocket tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImWebSocketGatewayTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/service/ImTicketService.java be/src/main/java/top/egon/mario/im/realtime/ImFrame.java be/src/main/java/top/egon/mario/im/web/ImWebSocketHandler.java be/src/main/java/top/egon/mario/im/web/ImWebSocketConfiguration.java be/src/main/java/top/egon/mario/im/web/ImController.java be/src/test/java/top/egon/mario/im/ImWebSocketGatewayTests.java && git commit -m "feat: add im websocket gateway"`

Expected: commit succeeds.

## Task 12: Platform REST Controllers And RBAC Boundary

**Files:**

- Create: `be/src/main/java/top/egon/mario/im/web/ReactiveImSupport.java`
- Create: `be/src/main/java/top/egon/mario/im/web/ImController.java`
- Create: `be/src/main/java/top/egon/mario/im/resource/ImRbacResourceProvider.java`
- Create: `be/src/test/java/top/egon/mario/im/ImControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/im/ImRbacResourceProviderTests.java`

- [ ] **Step 1: Write failing controller and RBAC tests**

Pseudocode:

```pseudocode
Assert RBAC provider declares exactly coarse IM API permissions:
  api:im:read
  api:im:write
  api:im:admin
Assert provider does not declare per-channel, per-group, per-conversation, or per-message permissions.
Assert read endpoints are covered by api:im:read.
Assert send, mark-read, join, cancel join, leave, open-DM endpoints are covered by api:im:write.
Assert approve join, reject join, and governance endpoints are covered by api:im:admin.
Call list conversations controller.
Assert controller delegates to facade on blocking scheduler and wraps ApiResponse.
Call send message controller.
Assert request principal is converted into ImPrincipal and command uses client message id.
Call ticket endpoint.
Assert ticket view is returned for authenticated principal.
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ImControllerTests,ImRbacResourceProviderTests test`

Expected: FAIL because REST and RBAC resources are not implemented.

- [ ] **Step 3: Implement REST controller**

Pseudocode:

```pseudocode
Use WebFlux controller with blocking scheduler wrapper.
Expose platform IM endpoints:
  create/list channel
  create/list group
  apply/approve/reject/cancel/leave
  open DM
  block/unblock DM
  send message
  history
  mark read
  list conversations
  mute/global mute/announce/ban
  mint WebSocket ticket
Convert authenticated RBAC principal to ImPrincipal at controller boundary.
Keep controller thin and free of repository access.
```

- [ ] **Step 4: Implement RBAC provider**

Pseudocode:

```pseudocode
Declare one provider for platform IM.
Add menu resource only if a standalone platform IM page is delivered.
Add API resource api:im:read for GET history, list channels, list groups, list conversations.
Add API resource api:im:write for send, mark-read, join, cancel own pending join, leave, open-DM, block, unblock, ticket mint.
Add API resource api:im:admin for approve, reject, mute, global mute, announcement, ban.
Do not create instance-specific permissions.
```

- [ ] **Step 5: Run tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ImControllerTests,ImRbacResourceProviderTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/im/web be/src/main/java/top/egon/mario/im/resource be/src/test/java/top/egon/mario/im/ImControllerTests.java be/src/test/java/top/egon/mario/im/ImRbacResourceProviderTests.java && git commit -m "feat: expose im rest boundary"`

Expected: commit succeeds.

## Task 13: Clocktower IM Adapter And Policies

**Files:**

- Create/replace: `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerImAdapter.java`
- Create/replace: `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerSendPolicy.java`
- Create/replace: `be/src/main/java/top/egon/mario/clocktower/chat/ClocktowerVisibilityPolicy.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/chat/service/ClocktowerChatService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/chat/service/ClocktowerChatServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/chat/web/ClocktowerChatController.java`
- Create/replace: `be/src/test/java/top/egon/mario/clocktower/chat/ClocktowerChatPolicyTests.java`
- Create/replace: `be/src/test/java/top/egon/mario/clocktower/chat/ClocktowerChatServiceTests.java`
- Create/replace: `be/src/test/java/top/egon/mario/clocktower/chat/ClocktowerChatControllerTests.java`

- [ ] **Step 1: Write failing Clocktower policy and adapter tests**

Pseudocode:

```pseudocode
Assert Clocktower policy is registered for context type CLOCKTOWER_ROOM.
For Clocktower channel main:
  storyteller can send according to room/game phase rules.
  players can read and send only in allowed phases.
  spectators can read public channel when allowed by room visibility.
For Clocktower private/whisper flows:
  visibility policy uses Clocktower room/game state from adapter context.
  IM core does not branch on Clocktower types.
Create Clocktower room chat surface through adapter.
Assert adapter creates channel or group using generic RoomFacade command.
Send Clocktower chat message through service.
Assert service delegates to generic ImFacade send command.
Assert controller response shape remains compatible with existing Clocktower frontend fields.
```

- [ ] **Step 2: Run Clocktower tests and verify they fail**

Run: `cd be && ./mvnw -Dtest=ClocktowerChatPolicyTests,ClocktowerChatServiceTests,ClocktowerChatControllerTests test`

Expected: FAIL because Clocktower still depends on old IM persistence/facade flow.

- [ ] **Step 3: Implement Clocktower adapter**

Pseudocode:

```pseudocode
ClocktowerImAdapter responsibilities:
  map room id and game id into IM context type CLOCKTOWER_ROOM
  create or fetch room public channel
  create or fetch game public group
  create or fetch spectator group
  open player private DM or group conversation when Clocktower rules allow
  translate RbacPrincipal to ImPrincipal
  translate generic ConversationView and MessageView into Clocktower response records
Clocktower service responsibilities:
  validate room/game existence and viewer mode
  call adapter
  never access IM repositories directly
```

- [ ] **Step 4: Implement Clocktower send and visibility policies**

Pseudocode:

```pseudocode
Clocktower visibility policy:
  resolve room/game/seat context from IM context fields
  allow storyteller/admin audit visibility according to Clocktower rules
  allow player public and private visibility according to game state
  allow spectator visibility only for spectator/public surfaces
Clocktower send policy:
  start from default membership and governance checks
  add Clocktower phase restrictions for night/day and storyteller-only flows
  reject storyteller-only whispers for non-storytellers
  keep prior history readable when a later send rule denies posting
```

- [ ] **Step 5: Run Clocktower tests and verify they pass**

Run: `cd be && ./mvnw -Dtest=ClocktowerChatPolicyTests,ClocktowerChatServiceTests,ClocktowerChatControllerTests test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:
`git add be/src/main/java/top/egon/mario/clocktower/chat be/src/test/java/top/egon/mario/clocktower/chat && git commit -m "feat: adapt clocktower chat to im core"`

Expected: commit succeeds.

## Task 14: Remove Legacy IM Leakage And Compile All Backend

**Files:**

- Modify/delete: all stale imports under `be/src/main/java/top/egon/mario/**`
- Modify/delete: all stale tests under `be/src/test/java/top/egon/mario/**`
- Create: `be/src/test/java/top/egon/mario/im/ImModuleBoundaryTests.java`

- [ ] **Step 1: Write failing module boundary tests**

Pseudocode:

```pseudocode
Scan production source files outside im package.
Assert no file imports im.po.
Assert no file imports im.repository.
Assert no file references old ImCoreService.
Assert only im.facade, im.facade.dto, im.policy.ImPrincipal, and documented realtime DTOs are imported by upstream packages.
Scan im package.
Assert im package does not import clocktower package.
Scan migration directory.
Assert only V29 modifies IM schema after V26.
```

- [ ] **Step 2: Run boundary tests and verify they fail if stale imports remain**

Run: `cd be && ./mvnw -Dtest=ImModuleBoundaryTests test`

Expected: FAIL until all stale PO/repository imports are removed.

- [ ] **Step 3: Remove legacy references**

Pseudocode:

```pseudocode
Replace upstream imports of im.po with facade DTO views.
Replace upstream direct repository access with facade or adapter calls.
Delete old tests that validate participant-key identity.
Replace old tests with channel/group/DM semantics tests from this plan.
Keep Clocktower response compatibility through adapter mapping instead of old PO exposure.
```

- [ ] **Step 4: Run backend compile and targeted tests**

Run: `cd be && ./mvnw -Dtest=ImModuleBoundaryTests,ImFacadeContractTests,ClocktowerChatServiceTests test`

Expected: PASS.

- [ ] **Step 5: Run full backend tests**

Run: `cd be && ./mvnw test`

Expected: PASS. If unrelated existing tests fail, capture the failing test names and failure messages before continuing.

- [ ] **Step 6: Commit**

Run: `git add be/src/main/java be/src/test/java && git commit -m "refactor: remove legacy im integration"`

Expected: commit succeeds.

## Task 15: Generic Frontend IM Client And WebSocket Hook

**Files:**

- Create: `fe/src/modules/im/imTypes.ts`
- Create: `fe/src/modules/im/imService.ts`
- Create: `fe/src/modules/im/useImSocket.ts`
- Create: `fe/src/modules/im/imMappers.ts`
- Create: `fe/src/modules/im/components/ImSurfaceBrowser.tsx`
- Create: `fe/src/modules/im/components/ImJoinApplyControls.tsx`
- Create: `fe/src/modules/im/imTypes.test.ts`
- Create: `fe/src/modules/im/imService.test.ts`
- Create: `fe/src/modules/im/useImSocket.test.tsx`
- Create: `fe/src/modules/im/imMappers.test.ts`
- Create: `fe/src/modules/im/components/ImSurfaceBrowser.test.tsx`
- Create: `fe/src/modules/im/components/ImJoinApplyControls.test.tsx`

- [ ] **Step 1: Write failing frontend IM client tests**

Pseudocode:

```pseudocode
Assert imTypes includes frame unions:
  client send message
  client mark read
  client subscribe
  client ping
  server send ack
  server message push
  server read updated
  server resync
  server pong
Mock REST request helper.
Call mint ticket helper.
Assert endpoint is platform IM ticket endpoint and method is POST.
Call list channels helper with context type and optional context id.
Assert endpoint is platform IM channel listing endpoint and method is GET.
Call list groups helper with channel id.
Assert endpoint is platform IM channel group listing endpoint and method is GET.
Call list standalone groups helper with context type and optional context id.
Assert endpoint is platform IM standalone group listing endpoint and method is GET.
Call apply, cancel, approve, and reject join helpers.
Assert each helper sends the matching surface identity, user/request id, and command method.
Map conversation view into chat workspace conversation.
Assert unread badge and last active fields map correctly.
Map message view into chat workspace message.
Assert sender and system messages map to stable roles.
Render ImSurfaceBrowser with one public channel, one approval group, and one active-member group.
Assert it displays discoverable surfaces, join policy, membership status, member count, and announcement.
Render ImJoinApplyControls for OPEN surface with no membership.
Assert primary action sends applyJoin and then reports ACTIVE.
Render ImJoinApplyControls for APPROVAL surface with no membership.
Assert primary action sends applyJoin and then reports PENDING.
Render ImJoinApplyControls for PENDING membership owned by current user.
Assert cancel action calls cancelJoin.
Render ImJoinApplyControls for OWNER/ADMIN reviewing a pending request.
Assert approve and reject actions call approveJoin and rejectJoin.
Mock WebSocket.
Assert hook connects with ticket, sends subscribe frame, routes message push into reducer, sends mark read, and requests history reload on RESYNC.
```

- [ ] **Step 2: Run frontend IM tests and verify they fail**

Run: `cd fe && bun run test -- im`

Expected: FAIL because generic IM frontend module does not exist.

- [ ] **Step 3: Implement IM types and REST helper**

Pseudocode:

```pseudocode
Create DTO types matching backend views and commands.
Create frame type union for all client and server frames.
Create mint ticket function.
Create platform IM REST helpers for:
  conversations
  list channels
  list channel groups
  list standalone groups
  history
  send
  mark read
  join/apply/approve/reject/cancel
  DM open/block/unblock
  governance
Do not include Clocktower-specific group filtering in generic module.
```

- [ ] **Step 4: Implement WebSocket hook**

Pseudocode:

```pseudocode
Hook inputs:
  enabled flag
  active conversation id
  callbacks for message push, send ack, read update, resync, and error
Hook behavior:
  mint ticket before opening socket
  build WebSocket URL from API base URL and ticket
  connect and subscribe to active conversation
  send ping on interval while connected
  expose sendMessage, markRead, reconnect, disconnect
  dedupe messages by conversation id, sender id, and client message id
  on RESYNC call provided reload callback with conversation id and sequence hint
  close socket on unmount
```

- [ ] **Step 5: Implement surface browser and join/apply controls**

Pseudocode:

```pseudocode
ImSurfaceBrowser inputs:
  channels
  groups
  loading state
  active surface id
  callbacks for select surface and refresh surfaces
Render channels and groups in one scan-friendly list.
Show surface name, surface type, join policy, member count, unread count when available, membership status, and announcement badge.
Do not describe keyboard shortcuts or implementation details in visible text.
ImJoinApplyControls inputs:
  surface type and id
  join policy
  current membership status
  current member role
  pending request id when known
  callbacks for apply, cancel, approve, reject, leave
For OPEN and no membership:
  show one join action that calls apply and expects ACTIVE result.
For APPROVAL and no membership:
  show one apply action that calls apply and expects PENDING result.
For PENDING owned by current user:
  show pending state and cancel action.
For OWNER or ADMIN reviewing pending requests:
  show approve and reject actions.
For ACTIVE non-owner:
  show leave action.
Disable actions while a request is in flight and surface the returned status through component state.
```

- [ ] **Step 6: Run frontend IM tests and verify they pass**

Run: `cd fe && bun run test -- im`

Expected: PASS.

- [ ] **Step 7: Commit**

Run: `git add fe/src/modules/im && git commit -m "feat: add frontend im client"`

Expected: commit succeeds.

## Task 16: Clocktower Frontend Chat Refactor

**Files:**

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/components/ClocktowerChatPanel.tsx`
- Modify: `fe/src/modules/clocktower/components/ClocktowerConversationList.tsx`
- Modify: `fe/src/modules/clocktower/components/ClocktowerMessageList.tsx`
- Modify: `fe/src/modules/clocktower/components/ClocktowerChatPanel.test.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerAdminAuditPage.tsx`

- [ ] **Step 1: Write failing Clocktower frontend tests**

Pseudocode:

```pseudocode
Render ClocktowerChatPanel with generic IM conversation views mapped to Clocktower fields.
Assert channel, group, and DM conversations display with unread badges.
Assert public channel can be read by spectator mode.
Assert composer disables when policy says read-only.
Mock useImSocket message push.
Assert pushed message appears without manual reload.
Mock SEND_ACK for optimistic message.
Assert optimistic message becomes sent and duplicate push is ignored.
Mock RESYNC.
Assert component reloads history from last known sequence.
Render discoverable approval group where current user is not a member.
Click apply.
Assert apply service is called, conversation list refreshes, and pending state is displayed.
Render discoverable open channel where current user is not a member.
Click join.
Assert join service is called, membership becomes active, and composer becomes enabled.
Render pending approval request as owner/admin.
Click approve and reject in separate cases.
Assert approveJoin and rejectJoin service calls are made and the surface list refreshes.
Render pending approval request as requester.
Click cancel request.
Assert cancelJoin service is called and the surface returns to joinable state.
Click block DM control.
Assert block service is called and composer becomes frozen/read-only.
Click mute control as governance-capable user.
Assert mute service is called and conversation state refreshes.
Render announcement.
Assert announcement banner appears for channel or group.
```

- [ ] **Step 2: Run Clocktower frontend tests and verify they fail**

Run:
`cd fe && bun run test -- ClocktowerChatPanel ClocktowerRoomPlayPage StorytellerGrimoirePage ClocktowerAdminAuditPage`

Expected: FAIL because Clocktower chat still uses old REST-only flow.

- [ ] **Step 3: Update Clocktower service and types**

Pseudocode:

```pseudocode
Replace old Clocktower chat DTOs with fields backed by generic IM views:
  conversation id
  conversation type
  surface type and id
  channel/group keys
  unread count
  announcement
  last message summary
  governance flags
  frozen DM flag
Keep response aliases needed by existing page props until pages are migrated.
Add service functions for:
  list conversations
  list discoverable channels
  list channel groups
  list standalone groups
  history
  send through generic IM endpoint or Clocktower adapter endpoint
  mark read
  apply join
  cancel pending join
  approve join
  reject join
  open private DM
  block/unblock
  mute
  announcement refresh
  WebSocket ticket through generic IM helper
```

- [ ] **Step 4: Refactor ClocktowerChatPanel**

Pseudocode:

```pseudocode
Use generic IM WebSocket hook.
Load conversation list on mount and after join/governance actions.
Load discoverable channel/group surfaces when the panel opens and after join/apply/approve/reject/cancel actions.
Load history for active conversation.
Render conversation list with unread badge and last activity.
Render discoverable surfaces that are not yet in the user's active conversation list.
Render join/apply/cancel/approve/reject controls through the generic IM join controls.
Render announcement banner when active surface has announcement.
Send message with generated client message id.
Optimistically append pending message.
On SEND_ACK, replace pending state with server message.
On MESSAGE_PUSH, append if not already present.
On READ_UPDATED, update unread counters.
On RESYNC, reload history and conversation list.
Expose block, unblock, mute, and leave controls only when current user state allows them.
Keep Clocktower phase policy text and disabled composer behavior.
```

- [ ] **Step 5: Update room, grimoire, and audit pages**

Pseudocode:

```pseudocode
Room play page passes current viewer mode, room id, game id, and phase into chat panel.
Storyteller grimoire page uses same panel with storyteller policy.
Admin audit page keeps read-only history table and uses backend audit endpoints for privileged visibility.
Remove any local assumptions about old group keys where generic surface fields now exist.
```

- [ ] **Step 6: Run Clocktower frontend tests and verify they pass**

Run:
`cd fe && bun run test -- ClocktowerChatPanel ClocktowerRoomPlayPage StorytellerGrimoirePage ClocktowerAdminAuditPage`

Expected: PASS.

- [ ] **Step 7: Commit**

Run: `git add fe/src/modules/clocktower fe/src/modules/im && git commit -m "feat: wire clocktower chat to im realtime"`

Expected: commit succeeds.

## Task 17: Real PostgreSQL Contract And Behavior Validation

**Files:**

- Create: `be/src/test/java/top/egon/mario/im/ImPostgresContractIT.java`
- Create: `be/src/test/java/top/egon/mario/im/ImPostgresBehaviorIT.java`

- [ ] **Step 1: Write the PostgreSQL schema and SQL contract gate**

Pseudocode:

```pseudocode
Create ImPostgresContractIT with tests that run only when invoked directly with -Dtest=ImPostgresContractIT.
Read connection settings from:
  IM_POSTGRES_TEST_URL
  IM_POSTGRES_TEST_USERNAME
  IM_POSTGRES_TEST_PASSWORD
If any setting is missing, fail with a clear message that a disposable PostgreSQL database is required for this gate.
Create DriverManagerDataSource using the PostgreSQL driver already on the Maven test classpath.
Run Flyway migrate against that disposable database with the project migration locations.
Assert V29 migration succeeds.
Query information_schema.columns.
Assert every V29 table has the column contract listed in Task 1:
  im_channel has context_type, context_id, channel_key, name, owner_user_id, visibility, join_policy, status, announcement, main_conversation_id, member_count, and last_active_at.
  im_group has channel_id, context_type, context_id, group_key, name, owner_user_id, join_policy, status, announcement, conversation_id, member_count, and last_active_at.
  im_dm_pair has user_lo_id, user_hi_id, conversation_id, and frozen.
  im_membership has surface_type, surface_id, user_id, member_role, status, muted_until, and joined_at.
  im_join_request has surface_type, surface_id, user_id, status, decided_by, decided_at, and decision_reason.
  im_conversation has conversation_type, owner_surface_type, owner_surface_id, context_type, context_id, message_seq, last_message_id, last_message_at, last_active_at, and status.
  im_conversation_member has conversation_id, user_id, last_read_seq, delivery_mode, muted, and status.
  im_message has conversation_id, sender_user_id, message_seq, client_msg_id, message_type, content, payload_json, status, sent_at, edited_at, and deleted_at.
  im_outbox has conversation_id, message_id, message_seq, event_type, status, available_at, attempts, and last_error.
  im_inbox has user_id, conversation_id, message_id, message_seq, and read.
  im_global_mute has user_id, scope_type, scope_id, expires_at, reason, and status.
  im_dm_block has blocker_user_id, blocked_user_id, status, and reason.
  im_ban has surface_type, surface_id, user_id, actor_user_id, reason, expires_at, and status.
  im_ws_ticket has token_hash, user_id, roles_json, expires_at, consumed_at, and status.
Assert every V29 table has audit columns metadata_json, created_at, updated_at, created_by, updated_by, version, and deleted.
Assert JSONB columns exist for metadata_json and payload_json.
Query pg_indexes.
Assert partial unique index for standalone groups exists and includes WHERE channel_id IS NULL.
Assert message idempotency unique index exists for conversation_id, sender_user_id, and client_msg_id.
Assert outbox dispatcher index exists for status and available_at.
Insert pending outbox rows.
Open two JDBC transactions.
In transaction A, claim one row with FOR UPDATE SKIP LOCKED and keep the transaction open.
In transaction B, claim rows with the same statement.
Assert transaction B does not see the row claimed by transaction A.
Commit both transactions.
Create one conversation row.
Run concurrent send-like sequence allocation through two threads that each lock the same conversation row, increment message_seq, and insert im_message.
Assert final message_seq is two and messages have sequence values one and two with no gap.
```

- [ ] **Step 2: Run the PostgreSQL schema gate and verify it fails before the contract is complete**

Run:
`cd be && IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test IM_POSTGRES_TEST_USERNAME=postgres IM_POSTGRES_TEST_PASSWORD=postgres ./mvnw -Dtest=ImPostgresContractIT test`

Expected: FAIL until the V29 migration, indexes, outbox claim SQL, and sequence locking behavior are implemented. If the
database does not exist, create an empty disposable local database named `cyber_mario_im_test` and rerun.

- [ ] **Step 3: Write the PostgreSQL service behavior gate**

Pseudocode:

```pseudocode
Create ImPostgresBehaviorIT with tests that run only when invoked directly with -Dtest=ImPostgresBehaviorIT.
Use @SpringBootTest with the same application wiring as the normal backend tests.
Register an ApplicationContextInitializer that:
  reads IM_POSTGRES_TEST_URL, IM_POSTGRES_TEST_USERNAME, and IM_POSTGRES_TEST_PASSWORD
  throws IllegalStateException with a message naming all three variables when any value is missing
  applies these properties to the Spring test context:
    spring.datasource.url = IM_POSTGRES_TEST_URL
    spring.datasource.username = IM_POSTGRES_TEST_USERNAME
    spring.datasource.password = IM_POSTGRES_TEST_PASSWORD
    spring.datasource.driver-class-name = org.postgresql.Driver
    spring.jpa.hibernate.ddl-auto = validate
    spring.flyway.enabled = true
    spring.ai.dashscope.api-key = test-api-key
Before each test:
  delete rows from IM tables only, in dependency order
  do not delete room_* or clocktower_* rows
  do not call flyway.clean
Autowire the DTO-only facades:
  RoomFacade
  ImFacade
  DmFacade
  GovFacade
Test send and idempotency on real PostgreSQL:
  create an OPEN channel
  join a sender
  send with client_msg_id pg-idem-1
  send again with the same client_msg_id
  assert the same message id and message_seq are returned
  assert exactly one im_message row exists for that idempotency key
  assert exactly one MESSAGE_CREATED outbox row exists for that message
Test sequence concurrency on real PostgreSQL:
  create an OPEN channel and active sender memberships
  run two concurrent facade.send calls against the same conversation with different client_msg_id values
  assert the resulting sequences are 1 and 2 with no duplicate and no gap
  assert im_conversation.message_seq is 2
Test mark-read and conversation list on real PostgreSQL:
  send three messages
  mark read to sequence 3, then mark read to sequence 1
  assert last_read_seq remains 3
  assert listConversations returns unread count 0 for that user
  send a fourth message from another active member
  assert listConversations returns unread count 1
Test join and approval workflow on real PostgreSQL:
  create an APPROVAL group
  apply as user A
  assert pending join request exists
  approve as owner
  assert user A has ACTIVE membership and ACTIVE conversation member
  assert member_count increments once
Test mute, ban, and global mute on real PostgreSQL:
  mute an active member until a future timestamp
  assert facade.send rejects the member while muted
  clear or expire the mute and assert send succeeds
  global-mute the member for PLATFORM scope and assert send to channel, group, and DM is denied
  ban the member from a group and assert rejoin is rejected with IM_MEMBER_BANNED
Test DM block freeze on real PostgreSQL:
  open DM between user A and user B
  send one message
  block B by A
  assert sends by both A and B are rejected with IM_DM_FROZEN
  assert both users can still read the prior message
  unblock A's block
  assert sending succeeds only when no active block remains in either direction
Test visibility rules on real PostgreSQL:
  read channel main history as a non-member and assert it succeeds
  read group history as a non-member and assert IM_HISTORY_FORBIDDEN
  read DM history as a non-participant and assert IM_HISTORY_FORBIDDEN
  read group history as an active group member and assert it succeeds
```

- [ ] **Step 4: Run the PostgreSQL behavior gate and verify it exposes any missing real-database behavior**

Run:
`cd be && IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test IM_POSTGRES_TEST_USERNAME=postgres IM_POSTGRES_TEST_PASSWORD=postgres ./mvnw -Dtest=ImPostgresBehaviorIT test`

Expected: FAIL if any service behavior still only works under H2 or mocks. PASS is acceptable only when all earlier
tasks already satisfy the real-PostgreSQL behavior contract.

- [ ] **Step 5: Implement PostgreSQL-specific assertions, helpers, and any required production fixes**

Pseudocode:

```pseudocode
Keep both PostgreSQL classes outside normal fast test discovery by naming them with IT suffixes and invoking them explicitly.
Do not add Testcontainers or a new dependency for this task.
Use plain JDBC and Flyway in ImPostgresContractIT to keep schema and locking checks independent of Spring application startup.
Use SpringBootTest in ImPostgresBehaviorIT so real facades, services, repositories, transactions, mappers, and policies are exercised together.
Use unique test data keys per test method so reruns against the same disposable database are deterministic.
Clean only IM tables in this disposable database before each test method.
Never call flyway.clean.
Use explicit transaction boundaries for SKIP LOCKED assertions.
Use ExecutorService with two tasks for sequence locking assertions.
If ImPostgresContractIT fails:
  add the missing V29 column, JSONB type, partial unique index, dispatcher index, idempotency index, row-lock query, or SKIP LOCKED claim SQL in the relevant migration/repository task output
  do not edit any migration other than V29
If ImPostgresBehaviorIT fails:
  fix the corresponding service, policy, facade transaction, or repository lock method
  keep the fix scoped to the failing behavior named by the test
  rerun the single failing test method before rerunning the full PostgreSQL gate
Fail with assertion messages that name the missing index, column, locking behavior, or service invariant.
```

- [ ] **Step 6: Run the full PostgreSQL gate and verify it passes**

Run:
`cd be && IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test IM_POSTGRES_TEST_USERNAME=postgres IM_POSTGRES_TEST_PASSWORD=postgres ./mvnw -Dtest=ImPostgresContractIT,ImPostgresBehaviorIT test`

Expected: PASS against a disposable real PostgreSQL database.

- [ ] **Step 7: Commit**

Run:
`git add be/src/test/java/top/egon/mario/im/ImPostgresContractIT.java be/src/test/java/top/egon/mario/im/ImPostgresBehaviorIT.java be/src/main/java/top/egon/mario/im be/src/main/resources/db/migration/V29__create_im_core_schema.sql && git commit -m "test: add im postgres contract gates"`

Expected: commit succeeds with only PostgreSQL gate files plus scoped IM production/schema fixes staged.

## Task 18: Backend Integration Test Pass

**Files:**

- Modify tests only as needed under `be/src/test/java/top/egon/mario/im/**`
- Modify tests only as needed under `be/src/test/java/top/egon/mario/clocktower/chat/**`

- [ ] **Step 1: Run focused IM backend suite**

Run: `cd be && ./mvnw -Dtest='top.egon.mario.im.*Tests' test`

Expected: PASS.

- [ ] **Step 2: Run focused Clocktower chat suite**

Run: `cd be && ./mvnw -Dtest='top.egon.mario.clocktower.chat.*Tests' test`

Expected: PASS.

- [ ] **Step 3: Run backend full test suite**

Run: `cd be && ./mvnw test`

Expected: PASS.

- [ ] **Step 4: Fix any failing tests caused by the IM rewrite**

Pseudocode:

```pseudocode
For each failing test:
  identify whether failure is caused by renamed IM contract, changed Clocktower chat behavior, or unrelated existing module behavior
  if caused by IM rewrite, update implementation or test expectation to match this plan and the redesign spec
  if unrelated, record exact failure and leave implementation unchanged
Run the smallest failing test again after each fix.
```

- [ ] **Step 5: Commit**

Run: `git add be/src/main/java be/src/test/java && git commit -m "test: stabilize im backend rewrite"`

Expected: commit succeeds only when backend validation is green or unrelated failures are explicitly recorded.

## Task 19: Frontend Integration Test Pass

**Files:**

- Modify tests only as needed under `fe/src/modules/im/**`
- Modify tests only as needed under `fe/src/modules/clocktower/**`
- Modify shared chat workspace tests only if generic IM mapping requires it.

- [ ] **Step 1: Run focused frontend IM suite**

Run: `cd fe && bun run test -- im`

Expected: PASS.

- [ ] **Step 2: Run focused Clocktower frontend suite**

Run: `cd fe && bun run test -- clocktower`

Expected: PASS.

- [ ] **Step 3: Run frontend typecheck**

Run: `cd fe && bun run typecheck`

Expected: PASS.

- [ ] **Step 4: Run frontend lint**

Run: `cd fe && bun run lint`

Expected: PASS.

- [ ] **Step 5: Run frontend full tests**

Run: `cd fe && bun run test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run: `git add fe/src && git commit -m "test: stabilize im frontend rewrite"`

Expected: commit succeeds only when frontend validation is green or unrelated failures are explicitly recorded.

## Task 20: Final Contract And Manual QA Checklist

**Files:**

- Create: `docs/im-core-contract.md`
- Modify: `README.md` only if it already documents backend/frontend validation commands.

- [ ] **Step 1: Write contract documentation**

Pseudocode:

```pseudocode
Document IM concepts:
  channel public read and member post
  discoverable channel and group listings
  group member read and post
  approval join request states PENDING, APPROVED, REJECTED, and CANCELLED
  DM implicit pair and block freeze
  unified conversation sequence
  unread calculation from sequence cursor
  outbox and inbox delivery model
  WebSocket frames
  facade package rule
  RBAC coarse boundary
Document Clocktower adapter:
  context type CLOCKTOWER_ROOM
  where Clocktower policy plugs in
  which APIs Clocktower uses
Document non-goals:
  no per-instance RBAC
  no multi-node router implementation
  no search/reactions/threads/media transcoding
```

- [ ] **Step 2: Run documentation check**

Run:
`rg -n "im\\.po|im\\.repository|ImCoreService|im_read_state|participantKey|participant_key" docs README.md be/src/main/java/top/egon/mario/clocktower be/src/main/java/top/egon/mario/im`

Expected: no stale legacy references except in historical spec/plan files and migration drop statements.

- [ ] **Step 3: Run final backend validation**

Run: `cd be && ./mvnw test`

Expected: PASS.

- [ ] **Step 4: Run final PostgreSQL validation**

Run:
`cd be && IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test IM_POSTGRES_TEST_USERNAME=postgres IM_POSTGRES_TEST_PASSWORD=postgres ./mvnw -Dtest=ImPostgresContractIT,ImPostgresBehaviorIT test`

Expected: PASS against a disposable real PostgreSQL database.

- [ ] **Step 5: Run final frontend validation**

Run: `cd fe && bun run typecheck && bun run lint && bun run test`

Expected: PASS.

- [ ] **Step 6: Manual QA checklist for the user to run**

Pseudocode:

```pseudocode
Create public channel.
Read channel history while not joined.
Join channel and send message.
Open the discoverable surface list and verify the channel shows join policy, member count, and membership status.
Create approval group.
Open the discoverable surface list and verify the approval group is visible before joining.
Apply join as user A.
Cancel the pending join as user A and verify the group becomes joinable again.
Apply join as user A again and reject as owner.
Apply join as user A again and approve as owner.
Send group message as user A.
Open DM between user A and user B.
Send DM message.
Block DM from user A.
Verify both users can read prior DM history and neither can send new DM message.
Unblock and send again.
Open Clocktower room.
Verify Clocktower public chat, player chat, spectator chat, and storyteller/audit visibility follow phase rules.
Disconnect WebSocket, send message from another session, reconnect, and verify RESYNC/history catches up.
```

- [ ] **Step 7: Commit**

Run: `git add docs/im-core-contract.md README.md && git commit -m "docs: document im core contract"`

Expected: commit succeeds if documentation files changed.

## Self-Review

Spec coverage:

- Channel/group/DM semantics are covered by Tasks 5, 6, 7, 8, and 16.
- Channel/group discoverability is covered by Tasks 5, 12, 15, and 16.
- Join request states PENDING, APPROVED, REJECTED, and CANCELLED are covered by Tasks 3, 6, 12, 15, and 16.
- Unified conversation host, exact V29 schema columns, sequencing, unread, delivery, and conversation list are covered
  by Tasks 1, 8, 10, and 17.
- Reliable delivery and IM-owned realtime are covered by Tasks 10 and 11.
- Governance is covered by Tasks 6, 7, and 9.
- DTO-only in-process facade is covered by Tasks 3 and 14.
- Clocktower context adapter and policies are covered by Task 13.
- RBAC coarse API boundary is covered by Task 12.
- Frontend surface, including join/apply controls, is covered by Tasks 15 and 16.
- Migration and transition are covered by Tasks 1 and 14.
- Validation is covered per task plus the PostgreSQL schema and service behavior gates in Task 17, backend integration
  in Task 18, frontend integration in Task 19, and final checks in Task 20.

Placeholder scan:

- No banned placeholder wording, unspecified future implementation promise, or copy-forward wording is required for
  execution.
- All implementation steps use concrete pseudocode and exact file paths.
- All tasks include targeted validation and commit steps.

Type consistency:

- Surface names use `CHANNEL`, `GROUP`, and `DM_PAIR`.
- Conversation types use `CHANNEL_MAIN`, `GROUP`, and `DM`.
- Member statuses use `ACTIVE`, `PENDING`, `LEFT`, and `BANNED`.
- Join request statuses use `PENDING`, `APPROVED`, `REJECTED`, and `CANCELLED`.
- Outbox statuses use `PENDING`, `DISPATCHED`, and `FAILED`.
- Frame names use `SEND_MESSAGE`, `MARK_READ`, `SUBSCRIBE`, `PING`, `SEND_ACK`, `MESSAGE_PUSH`, `READ_UPDATED`,
  `RESYNC`, and `PONG`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-27-im-system-redesign.md`. Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
