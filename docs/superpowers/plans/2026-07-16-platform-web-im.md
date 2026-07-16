# Platform Web IM Productization — Implementation Plan

Date: 2026-07-16
Status: Ready for execution after an explicit start instruction
Design: docs/superpowers/specs/2026-07-16-platform-web-im-design.md

## Goal

Turn the implemented reusable IM core into a first-class /im Web product in the current CyberMario modular monolith.
Deliver friendship-gated DM, public/general channel, user-created groups, exact unread, enabled single-node realtime,
normal-user RBAC access, and a three-column React workspace without changing Clocktower behavior or destroying current
IM data.

## Architecture

Keep the existing three-layer IM module. Add a narrow platform composition adapter:

    Platform Web controller and WebSocket
        -> PlatformImFacade
        -> FriendshipService plus safe RBAC user directory
        -> existing ImFacade, DmFacade, RoomFacade, and GovFacade
        -> existing services, policies, repositories, outbox, and local router

ClocktowerImAdapter continues to call the existing generic Facades directly. Platform friendship checks are enforced
at both Web send entry points, REST and WebSocket, so a historical DM cannot bypass the rule after friendship deletion.

## Technical Baseline

- Java 21, Spring Boot 3.5, WebFlux, Spring Data JPA
- PostgreSQL, Flyway, H2 PostgreSQL mode
- Reactor WebSocket and the existing transactional Outbox
- React 19, TypeScript 6, Ant Design 6, Bun, Vite, Vitest
- existing RBAC resource-provider and role-preset bootstrap

## Execution Guardrails

- Do not execute this plan until the user explicitly says to start.
- Work in the current repository unless the user later requests a branch or worktree.
- Before every task, inspect adjacent implementation and tests and follow their naming/comment style.
- Preserve unrelated staged, unstaged, and untracked work.
- Commit each task exactly once and stage only that task's files.
- Never edit an existing Flyway migration.
- The current next migration is V40. Recheck immediately before Task 1; if another migration exists, use the actual next
  version and update new references consistently.
- Task 1 creates exactly one new versioned migration for all friendship schema changes.
- Do not start the application. Validation uses tests, compile/typecheck, build, lint, and explicit database gates.
- Do not introduce dependencies, a broker, Redis fanout, code generation, a new module system, or new DDD layers.
- Do not update FEATURE_CHECKLIST.md until the final behavior has passed its gates.

## Design Pattern Decision

Retain Facade, Strategy, Adapter, Repository, Mapper, and Outbox because they already correspond to real boundaries.
Add PlatformImFacade as an Adapter/Facade composition seam because platform DM authorization differs from trusted
in-process integrations. Use direct transactional services for friendship transitions. Factory, Chain of
Responsibility, CQRS, event sourcing, and a new domain-layer hierarchy would add indirection without solving a V1
variation point.

## Delivery Order

    additive schema
        -> safe identity and friendship workflow
        -> group queries and general-channel bootstrap
        -> platform read model and Web send gate
        -> RBAC and user backfill
        -> realtime activation/recovery
        -> frontend data/state
        -> frontend workspace
        -> full regression and documentation

---

## Task 1: Add the Friendship Persistence Slice

### Files

Create:

- be/src/main/resources/db/migration/V40__create_im_platform_friendship_schema.sql
- be/src/main/java/top/egon/mario/im/po/ImFriendshipPo.java
- be/src/main/java/top/egon/mario/im/po/ImContactPo.java
- be/src/main/java/top/egon/mario/im/po/enums/ImFriendshipStatus.java
- be/src/main/java/top/egon/mario/im/po/enums/ImContactStatus.java
- be/src/main/java/top/egon/mario/im/repository/ImFriendshipRepository.java
- be/src/main/java/top/egon/mario/im/repository/ImContactRepository.java

Modify:

- be/src/test/java/top/egon/mario/im/ImCoreSchemaMigrationTests.java
- be/src/test/java/top/egon/mario/im/ImPersistenceMappingTests.java
- be/src/test/java/top/egon/mario/im/ImModuleBoundaryTests.java
- be/src/test/java/top/egon/mario/im/ImPostgresContractIT.java
- be/src/test/java/top/egon/mario/im/ImPostgresBehaviorIT.java

Use the actual next migration version if V40 is no longer free.

### Steps

- [ ] Verify the live migration sequence and confirm no existing migration is changed.
- [ ] Add failing schema assertions for im_friendship and im_contact, their audit columns, ordered-pair check, unique
      normalized pair, unique directed contact, foreign-key/index coverage, and accepted status values.
- [ ] Replace the module-boundary test's obsolete “only V30 may reference IM tables” rule with an exact allow-list for
      the immutable V30 baseline and this one approved additive migration. Keep rejection of any other IM migration.
- [ ] Create both tables using the repository's portable H2/PostgreSQL SQL style. Avoid a PostgreSQL-only partial unique
      index; the normalized pair and one durable row per pair make ordinary unique constraints sufficient.
- [ ] Map both entities with the same common audit, soft-delete, enum, and optimistic-version conventions as current IM
      persistence.
- [ ] Add repository methods for normalized pair lookup, directed contact lookup, active friend listing, and locked
      friendship transition reads.
- [ ] Update PostgreSQL contract table/column lists and behavior-test cleanup order so the new tables are validated and
      isolated.
- [ ] Verify that Flyway applies V30 followed by the new migration on a fresh H2 database and that no old table/data is
      dropped.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImCoreSchemaMigrationTests,ImPersistenceMappingTests,ImModuleBoundaryTests test

If disposable PostgreSQL credentials are available, also run the updated contract gate in Task 9. Do not silently use
the developer's normal database.

### Commit

    git add <only Task 1 files>
    git commit -m "feat(im): add platform friendship persistence"

---

## Task 2: Add Safe User Discovery and Friendship Workflow

### Files

Create, adapting exact names to existing RBAC conventions:

- be/src/main/java/top/egon/mario/rbac/application/RbacUserDirectoryFacade.java
- be/src/main/java/top/egon/mario/rbac/dto/response/UserDirectoryItemResponse.java
- be/src/main/java/top/egon/mario/im/service/FriendshipService.java
- be/src/main/java/top/egon/mario/im/facade/FriendFacade.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/RequestFriendCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/DecideFriendRequestCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/CancelFriendRequestCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/RemoveFriendCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/UpdateFriendRemarkCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/query/ListFriendsQuery.java
- be/src/main/java/top/egon/mario/im/facade/dto/query/ListFriendRequestsQuery.java
- be/src/main/java/top/egon/mario/im/facade/dto/view/FriendView.java
- be/src/main/java/top/egon/mario/im/facade/dto/view/FriendRequestView.java
- be/src/main/java/top/egon/mario/im/web/PlatformFriendController.java
- be/src/test/java/top/egon/mario/im/ImFriendshipServiceTests.java
- be/src/test/java/top/egon/mario/im/PlatformFriendControllerTests.java

Modify:

- be/src/main/java/top/egon/mario/rbac/repository/UserRepository.java
- existing RBAC converter/test files required by the safe projection
- be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java only if new stable IM codes need existing mapping

### Steps

- [ ] Write tests that the directory requires a nonblank keyword, caps the page size at 20, excludes the caller and
      disabled/deleted users, and returns no username, email, mobile, password, role, lock, or audit data.
- [ ] Add a batch find-by-id operation to the safe directory Facade for later conversation composition. Avoid exposing
      UserPo or UserRepository outside RBAC.
- [ ] Write friendship state-machine tests before service implementation: self-request, new pending, duplicate pending,
      crossed request, receiver-only accept/reject, requester-only cancel, accept creating two contacts, delete removing
      both contacts, re-request after removal, caller-only remark, and blocked-pair rejection.
- [ ] Normalize every pair as userLoId less than userHiId. Lock an existing pair before transitions and treat the
      database unique constraint as the final race guard when two first requests arrive concurrently.
- [ ] Convert uniqueness races and invalid transitions to stable IM error codes; never expose SQL constraint text.
- [ ] Keep accept/delete plus both directed contact updates in one transaction.
- [ ] Add bounded incoming/outgoing request and active-friend queries with batch identity mapping.
- [ ] Add /api/im/platform/users, /friends, and /friend-requests endpoints from the design contract.
- [ ] Verify block interaction through the existing DM block state without duplicating a block table.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImFriendshipServiceTests,PlatformFriendControllerTests test

Run the closest existing RBAC repository/application tests affected by the user-directory query.

### Commit

    git add <only Task 2 files>
    git commit -m "feat(im): add platform friendship workflow"

---

## Task 3: Complete Platform Group Reads and Bootstrap the General Channel

### Files

Create:

- be/src/main/java/top/egon/mario/im/facade/dto/query/ListSurfaceMembersQuery.java
- be/src/main/java/top/egon/mario/im/facade/dto/query/ListJoinRequestsQuery.java
- be/src/main/java/top/egon/mario/im/facade/dto/command/RemoveMemberCommand.java
- be/src/main/java/top/egon/mario/im/facade/dto/view/SurfaceMemberView.java
- be/src/main/java/top/egon/mario/im/facade/dto/view/JoinRequestView.java
- be/src/main/java/top/egon/mario/im/platform/PlatformImConfiguration.java
- be/src/main/java/top/egon/mario/im/platform/PlatformImBootstrapProperties.java
- be/src/main/java/top/egon/mario/im/platform/PlatformImBootstrap.java
- be/src/main/java/top/egon/mario/im/platform/PlatformRoomFacade.java
- be/src/test/java/top/egon/mario/im/ImSurfaceAdministrationTests.java
- be/src/test/java/top/egon/mario/im/PlatformImBootstrapTests.java

Modify:

- be/src/main/java/top/egon/mario/im/facade/RoomFacade.java
- be/src/main/java/top/egon/mario/im/service/MembershipService.java
- be/src/main/java/top/egon/mario/im/repository/ImMembershipRepository.java
- be/src/main/java/top/egon/mario/im/repository/ImJoinRequestRepository.java
- be/src/main/java/top/egon/mario/im/web/ImController.java
- be/src/main/resources/application.yaml
- be/src/test/resources/application.yaml

### Steps

- [ ] Add failing owner/admin authorization tests for listing pending join requests, listing members, and removing an
      ordinary member. Preserve self-leave, ban, and mute behavior.
- [ ] Keep owner promotion, ownership transfer, invitations, and INVITE_ONLY out of the contract.
- [ ] Add PlatformRoomFacade methods that force contextType PLATFORM and create standalone groups only. Do not accept an
      arbitrary browser context value.
- [ ] Expose platform group create/list and surface member/review endpoints with bounded results.
- [ ] Add bootstrap properties for enabled, owner-account-no, channel-key, and channel-name. Default to enabled,
      configured RBAC admin account, general, and 公共频道.
- [ ] Run the bootstrap after RBAC resource/admin bootstraps. Resolve the owner through the safe user directory boundary
      and delegate channel creation/reuse to RoomFacade rather than inserting core rows manually.
- [ ] Fail clearly when bootstrap is enabled but the owner does not exist. When disabled, perform no query or write.
- [ ] Test repeat execution, an already-existing general channel, public nonmember history, immediate OPEN join, member
      post, and nonmember lack of realtime/unread membership.
- [ ] Disable application runners in generic tests and enable them only in bootstrap-specific tests.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImSurfaceAdministrationTests,PlatformImBootstrapTests,ImMembershipWorkflowTests,ImSurfaceDiscoveryTests test

### Commit

    git add <only Task 3 files>
    git commit -m "feat(im): add platform groups and general channel"

---

## Task 4: Add the Platform Conversation Read Model and Enforce DM Friendship

### Files

Create:

- be/src/main/java/top/egon/mario/im/platform/PlatformImFacade.java
- be/src/main/java/top/egon/mario/im/platform/dto/PlatformBootstrapView.java
- be/src/main/java/top/egon/mario/im/platform/dto/PlatformConversationView.java
- be/src/main/java/top/egon/mario/im/platform/dto/PlatformUserView.java
- be/src/main/java/top/egon/mario/im/web/PlatformImController.java
- be/src/test/java/top/egon/mario/im/PlatformImFacadeTests.java
- be/src/test/java/top/egon/mario/im/PlatformImControllerTests.java

Modify:

- be/src/main/java/top/egon/mario/im/web/ImController.java
- be/src/main/java/top/egon/mario/im/web/ImWebSocketHandler.java
- existing DTO mapper/repository methods required for batch surface resolution
- be/src/test/java/top/egon/mario/im/ImControllerTests.java
- be/src/test/java/top/egon/mario/im/ImWebSocketGatewayTests.java

### Steps

- [ ] Write tests for display-ready DM, group, and public-channel summaries, title/avatar fallback, unread total, pending
      friend-request count, and no per-conversation identity query.
- [ ] Batch-load all needed sys_user projections and surface rows. Never add sys_user joins to generic IM repositories.
- [ ] Add PlatformImFacade.openDm: require ACTIVE friendship, then delegate to DmFacade.
- [ ] Add PlatformImFacade.send: inspect the conversation; for DM require ACTIVE friendship, then delegate to ImFacade.
      Non-DM sends continue through existing membership/policy checks.
- [ ] Route POST /api/im/dms and POST /api/im/messages through PlatformImFacade.
- [ ] Route WebSocket SEND_MESSAGE through PlatformImFacade as well. This is required so an old DM conversation cannot
      send after friend deletion by bypassing REST.
- [ ] Keep generic DmFacade and ImFacade unchanged for trusted in-process use. ClocktowerImAdapter must not import or
      call PlatformImFacade.
- [ ] Preserve history and mark-read access for a removed friend's existing DM, but expose canPost false and a stable
      friendship-required error for send.
- [ ] Compose block and friendship: ACTIVE friendship is necessary, and existing DM frozen/block policy must also pass.
- [ ] Expose /api/im/platform/bootstrap and /api/im/platform/conversations.
- [ ] Add or extend boundary tests proving Clocktower depends only on generic Facades and the platform adapter does not
      leak persistence types.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=PlatformImFacadeTests,PlatformImControllerTests,ImControllerTests,ImWebSocketGatewayTests,ImModuleBoundaryTests test

Run the existing DM and Clocktower chat-focused tests that exercise the generic Facades.

### Commit

    git add <only Task 4 files>
    git commit -m "feat(im): enforce platform dm friendship"

---

## Task 5: Add IM RBAC, Registration Assignment, and Existing-User Backfill

### Files

Create if the existing provider is not kept self-contained:

- be/src/main/java/top/egon/mario/im/resource/ImPermissionCatalog.java
- be/src/main/java/top/egon/mario/im/platform/ImUserRoleBackfill.java
- be/src/main/java/top/egon/mario/im/platform/ImUserRoleBackfillProperties.java
- be/src/test/java/top/egon/mario/im/ImUserRoleBackfillTests.java

Modify:

- be/src/main/java/top/egon/mario/im/resource/ImRbacResourceProvider.java
- be/src/main/java/top/egon/mario/im/platform/PlatformImConfiguration.java
- be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java
- corresponding RBAC registration tests
- be/src/test/java/top/egon/mario/im/ImRbacResourceProviderTests.java
- be/src/main/resources/application.yaml
- be/src/test/resources/application.yaml

### Steps

- [ ] Add menu:im at /im and explicit read, write, instance-management, and platform-admin API resources covering every
      implemented route and HTTP method. Keep risk levels consistent with current RBAC providers.
- [ ] Add provider-owned IM_USER and IM_ADMIN role presets. IM_USER includes menu, normal APIs, self-auth, and me-self.
      IM_ADMIN includes IM_USER permissions plus governance/platform administration.
- [ ] Grant join decisions, member list/removal, and per-surface governance API entry to IM_USER because regular group
      owners need them. Keep OWNER/ADMIN membership checks in IM services.
- [ ] Do not grant generic channel/group creation endpoints to IM_USER. Grant the fixed PLATFORM group endpoint instead;
      reserve platform channel creation and global/cross-surface governance for IM_ADMIN.
- [ ] Keep instance checks in IM services: an API-level permission does not bypass ownership/membership rules unless the
      existing policy explicitly recognizes that role.
- [ ] Add IM_USER to the new-registration default role list and update tests for a missing/disabled role and successful
      atomic assignment.
- [ ] Add an idempotent, configuration-controlled backfill after resource sync. Grant IM_USER to existing enabled,
      non-deleted sys_user rows only; never grant IM_ADMIN.
- [ ] Publish the existing permission-changed event/cache invalidation after actual backfill changes, not after a no-op.
- [ ] Test disabled backfill, missing role, repeat execution, disabled/deleted users, existing assignment, and new grant.
- [ ] Verify SUPER_ADMIN bypass remains unchanged.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImRbacResourceProviderTests,ImUserRoleBackfillTests test

Also run the exact RbacAuthApplication registration test class discovered in the repository.

### Commit

    git add <only Task 5 files>
    git commit -m "feat(im): grant platform im access"

---

## Task 6: Enable and Harden Single-Node Realtime

### Files

Modify:

- be/src/main/resources/application.yaml
- be/src/main/resources/application-auto.yaml only if an explicit profile override is needed
- be/src/test/resources/application.yaml
- be/src/main/java/top/egon/mario/im/web/ImWebSocketHandler.java
- be/src/test/java/top/egon/mario/im/ImOutboxDispatcherTests.java
- be/src/test/java/top/egon/mario/im/ImWebSocketGatewayTests.java
- fe/src/modules/im/useImSocket.ts
- fe/src/modules/im/useImSocket.test.tsx
- docs/im-core-contract.md realtime section if the test-proven contract changes

### Steps

- [ ] Enable im.realtime.dispatcher and its runner by default in normal runtime, with environment overrides for enabled,
      batch size, initial delay, fixed delay, and retry behavior already supported by code.
- [ ] Explicitly disable the runner in generic test configuration to prevent background polling and nondeterminism.
- [ ] Test that one committed outbox event is claimed, delivered once per active user connection, and marked DISPATCHED;
      retries remain PENDING or become FAILED according to existing limits.
- [ ] Test that a nonmember public-channel reader receives no push, while an active joined member does.
- [ ] Remove SUBSCRIBE emission from useImSocket. Actual routing is user-membership based and active-conversation
      subscription currently has no effect.
- [ ] Keep server-side SUBSCRIBE parsing only as a documented deprecated compatibility no-op. Do not build a second
      per-session routing system in V1.
- [ ] Retain heartbeat, ticket consumption, SEND_ACK, READ_UPDATED, MESSAGE_PUSH, bounded outbound queue, and RESYNC.
- [ ] Test reconnect and RESYNC recovery by sequence, duplicate clientMsgId retry, active-conversation reconciliation,
      and unread summary refresh.

### Validation

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImOutboxDispatcherTests,ImWebSocketGatewayTests test

    cd ../fe
    bun run test -- useImSocket

### Commit

    git add <only Task 6 files>
    git commit -m "feat(im): enable local realtime delivery"

---

## Task 7: Build the Platform IM Frontend Data and State Layer

### Files

Create:

- fe/src/modules/im/platformImTypes.ts
- fe/src/modules/im/platformImService.ts
- fe/src/modules/im/platformImService.test.ts
- fe/src/modules/im/usePlatformImWorkspace.ts
- fe/src/modules/im/usePlatformImWorkspace.test.tsx
- focused mapper/helper files only where state logic would otherwise be duplicated

Modify:

- fe/src/modules/im/imTypes.ts
- fe/src/modules/im/imTypes.test.ts
- fe/src/modules/im/imService.ts where generic endpoints are reused
- fe/src/modules/im/imService.test.ts
- fe/src/modules/im/components/ImSurfaceBrowser.tsx
- fe/src/modules/im/components/ImJoinApplyControls.tsx
- their focused tests

### Steps

- [ ] Add exact TypeScript contracts for bootstrap, display-ready conversations, safe users, friends, friend requests,
      surface members, and pending group requests.
- [ ] Align ImJoinPolicy with backend OPEN and APPROVAL. Remove IM-level INVITE_ONLY branches and tests without changing
      Clocktower's separate seating-policy type.
- [ ] Add service methods for every new platform endpoint, including bounded search/query serialization and stable
      request bodies.
- [ ] Build one workspace hook/reducer that owns selected activity, selected conversation, pages, optimistic sends,
      friend/request state, group state, unread totals, and socket reconciliation.
- [ ] Merge messages by conversationId plus messageSeq/id and replace optimistic rows by clientMsgId.
- [ ] On reconnect/RESYNC, reload summaries and fetch only messages after the active conversation's last known sequence;
      fall back to latest page only when no sequence is known.
- [ ] Mark read after the active thread has reconciled and become visible, not merely when a list row is clicked.
- [ ] Derive read-only DM composer state from canPost/friendship status.
- [ ] Keep network and state tests independent from Ant Design rendering.

### Validation

    cd fe
    bun run test -- im
    bun run typecheck

### Commit

    git add <only Task 7 files>
    git commit -m "feat(im): add platform workspace state"

---

## Task 8: Build the Three-Column /im Workspace

### Files

Create:

- fe/src/modules/im/PlatformImPage.tsx
- fe/src/modules/im/PlatformImPage.test.tsx
- fe/src/modules/im/PlatformImPage.css
- fe/src/modules/im/components/ImActivityRail.tsx
- fe/src/modules/im/components/ImConversationPane.tsx
- fe/src/modules/im/components/ImThreadPane.tsx
- fe/src/modules/im/components/ImFriendPane.tsx
- fe/src/modules/im/components/ImGroupPane.tsx
- focused tests for components with business interaction

Modify:

- fe/src/app/routes.tsx
- fe/src/app/routes.test.ts
- fe/src/layouts/AdminLayout/menu.tsx
- fe/src/layouts/AdminLayout/menu.test.tsx

Avoid creating one component per trivial visual fragment; keep components at independent state/interaction boundaries.

### Steps

- [ ] Add the lazy /im route and static menu path/icon entry that is filtered by server-provided menu:im.
- [ ] Render the activity rail, bounded list pane, and flexible detail pane within AdminLayout without creating another
      global shell.
- [ ] Pin 公共频道 and show its read/join/post states.
- [ ] Implement conversation ordering, exact unread badges, latest-message preview, loading/empty/error states, and
      accessible keyboard selection.
- [ ] Implement text/system message rendering, Unicode emoji insertion, optimistic pending/failed rows, idempotent retry,
      and disabled/read-only composer explanations.
- [ ] Implement friend discovery, incoming/outgoing request actions, friend remark/edit/delete, DM open, and add-friend
      action for historical read-only DM.
- [ ] Implement group creation with OPEN/APPROVAL only, discovery/join/leave, member list, pending review, and remove
      member for authorized users.
- [ ] Reuse generic IM message and join primitives. Extract reusable state from ClocktowerChatPanel if needed; never
      import Clocktower UI into PlatformImPage.
- [ ] Make desktop widths the primary layout. At narrower widths collapse the activity/list panes rather than adding a
      separate mobile application.
- [ ] Verify no upload, image, search, reaction, typing, presence, or receipt controls appear.

### Validation

    cd fe
    bun run test -- PlatformImPage AdminLayout routes im
    bun run typecheck
    bun run lint
    bun run build

### Commit

    git add <only Task 8 files>
    git commit -m "feat(im): add web im workspace"

---

## Task 9: Run Full Gates and Publish the Implemented Contract

### Files

Modify only after all behavior passes:

- docs/im-core-contract.md
- FEATURE_CHECKLIST.md
- any focused test or documentation file required to accurately record verified behavior

Do not rewrite the confirmed design document to match accidental implementation drift. Fix the implementation or record
an explicit approved deviation.

### Steps

- [ ] Compare every acceptance criterion in the design document with a passing automated test or an explicit manual
      browser test instruction for the user.
- [ ] Run the full focused IM suite and relevant RBAC/Clocktower integration tests.
- [ ] Run the disposable PostgreSQL schema and behavior gates. The database must be disposable; never point them at the
      normal development database.
- [ ] Run the complete frontend tests, typecheck, lint, and production build.
- [ ] Confirm application.yaml enables normal local dispatch and test configuration disables background runners.
- [ ] Confirm only one new Flyway version exists and every historical migration checksum is untouched.
- [ ] Confirm git status contains no generated build output or unrelated staged changes.
- [ ] Update docs/im-core-contract.md with friendship/platform/realtime behavior that is now implemented.
- [ ] Update FEATURE_CHECKLIST.md from stale “platform IM missing” language to verified endpoints/page/tests.
- [ ] Record any manual runtime checks the user must perform; do not start the application.

### Validation

Focused backend:

    cd be
    ./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.im.*Tests' test

Disposable PostgreSQL:

    IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test IM_POSTGRES_TEST_USERNAME=postgres IM_POSTGRES_TEST_PASSWORD=postgres ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImPostgresContractIT,ImPostgresBehaviorIT test

Frontend:

    cd ../fe
    bun run test
    bun run typecheck
    bun run lint
    bun run build

Repository hygiene:

    cd ..
    git diff --check
    git status --short

If PostgreSQL credentials are unavailable, report that gate as not run. Do not describe H2 as proof of PostgreSQL
locking, filtered-index, or type semantics.

### Commit

    git add docs/im-core-contract.md FEATURE_CHECKLIST.md <only Task 9 test/doc fixes>
    git commit -m "docs(im): publish platform im contract"

---

## Final Completion Check

Before handoff, verify all of the following:

- [ ] All nine task commits exist and contain only their stated scope.
- [ ] Exactly one new Flyway version was added.
- [ ] No existing migration was modified.
- [ ] Friendship is required for platform REST DM open/send and WebSocket DM send.
- [ ] Historical removed-friend DM is readable but read-only.
- [ ] Clocktower still calls generic Facades and its tests pass.
- [ ] Public general is idempotent, public-readable, and join-gated for post/push/unread.
- [ ] IM_USER reaches /im; IM_ADMIN is not assigned automatically.
- [ ] Existing enabled users were idempotently backfilled.
- [ ] OPEN/APPROVAL are the only IM group policies.
- [ ] Realtime uses the local Outbox path and recovers by sequence.
- [ ] The /im page covers messages, contacts, groups, and review flows.
- [ ] Deferred features did not enter the implementation.
- [ ] Validation results and any unrun PostgreSQL/manual gates are reported exactly.
- [ ] The project was not started automatically.

## User Runtime Handoff

After implementation, provide the user with:

- required environment variables for database, RBAC bootstrap owner, and optional realtime overrides;
- the existing backend/frontend start commands from repository documentation;
- a short two-user manual scenario for friend acceptance, DM, group approval, public-channel join, unread, reconnect,
  friend deletion, and Clocktower regression;
- no claim of runtime success until the user performs that check.
