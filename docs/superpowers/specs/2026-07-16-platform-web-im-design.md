# Platform Web IM Productization — Design Document

Date: 2026-07-16
Status: Historical — implemented, then partially superseded on 2026-07-17

> 2026-07-17 correction: the public `PLATFORM/general`, discovery, and global
> join rules in this document are superseded by
> `2026-07-17-platform-web-im-membership-correction.md`. The friendship, DM,
> realtime, RBAC, and Clocktower boundaries remain applicable.
Related baseline:

- docs/superpowers/specs/2026-06-27-im-system-redesign-design.md
- docs/superpowers/plans/2026-06-27-im-system-redesign.md
- docs/im-core-contract.md

## 1. Decision

Continue in the current CyberMario repository and productize the implemented IM core as a first-class Web module.
Do not create a separate project and do not rebuild the old desktop-client architecture.

This is the smallest safe direction because the current repository already has:

- a unified channel, group, DM, conversation, message, unread, inbox, outbox, and governance model;
- DTO-only Java Facades used by Clocktower;
- REST and JSON WebSocket entry points;
- Flyway-managed schema and focused H2/PostgreSQL tests;
- an existing React administration shell, authentication, menu, and RBAC system.

A new project would duplicate identity, authorization, deployment, realtime delivery, and data ownership, then require
cross-service consistency before any user-facing value appears. A separate service should only be reconsidered after
there is verified pressure from independent scaling, independent release cadence, or organizational ownership. None of
those conditions exists in the confirmed V1 scope.

## 2. Confirmed Product Boundary

The approved V1 is:

- one Web IM page at /im inside the current AdminLayout;
- friend request, accept, reject, cancel, delete, and per-user remark;
- 1-to-1 platform DM only between active friends;
- user-created standalone groups with OPEN or APPROVAL join policy;
- one initialized public PLATFORM/general channel;
- TEXT and SYSTEM messages plus ordinary Unicode emoji input;
- exact unread counts and the current user's read cursor;
- single-instance realtime using the existing local router and Outbox dispatcher;
- one tenant and human users from sys_user only.

The following are explicitly deferred:

- Netty, Protobuf, a private TCP protocol, and a shared desktop-client Jar;
- a standalone IM service or a new repository;
- INVITE_ONLY group policy;
- file and image upload, reactions, threads, search, edit, recall, and rich cards;
- presence, typing, delivery receipts, peer-read receipts, and multi-device cursor semantics;
- Redis-based multi-node fanout;
- agent identities as platform IM users;
- friend folders and historical friend-request audit trails.

## 3. Current-State Assessment

### 3.1 Capabilities to retain

The implemented IM core already provides the correct mechanical foundation:

| Area | Current implementation to retain |
|---|---|
| Surface model | im_channel, im_group, im_dm_pair |
| Membership | im_membership and im_conversation_member |
| Message stream | one im_conversation host for CHANNEL_MAIN, GROUP, and DM |
| Message storage | ordered im_message sequence with client message idempotency |
| Unread | message sequence minus the user's last read sequence |
| Reliability | transactional im_outbox and im_inbox |
| Realtime | ticket-authenticated /ws/im JSON WebSocket |
| Governance | join approval, mute, global mute, announcement, ban, DM block |
| Integration | Facades plus the ClocktowerImAdapter |

These are implemented behavior, not a greenfield proposal. docs/im-core-contract.md remains the source of truth for
core semantics.

### 3.2 Product gaps to close

| Gap | Required adjustment |
|---|---|
| No /im route or page | Add a dedicated three-column Web workspace |
| No normal-user IM role/menu | Add IM_USER, IM_ADMIN, menu/API resources, registration assignment, and existing-user backfill |
| No safe user lookup | Add a paginated directory projection that never exposes email, mobile, password, or admin fields |
| No friendship model | Add a pair-level friendship state plus directed contact metadata |
| Generic DM open | Put a friendship gate in the platform Web adapter while preserving the reusable core DmFacade |
| Thin conversation views | Compose user and surface display data in a platform read model |
| Missing review/member queries | Add pending join-request and member-list read APIs |
| Public channel not bootstrapped | Idempotently create PLATFORM/general after RBAC bootstrap |
| Dispatcher disabled by default | Enable local dispatch in normal runtime and keep it disabled in tests |
| SUBSCRIBE does not route | Do not rely on it in the platform client; retain it only as a deprecated compatibility no-op |
| Frontend/backend policy drift | Limit IM join policy to OPEN and APPROVAL; Clocktower seating INVITE_ONLY remains a separate concept |
| Stale feature checklist | Update it only after implementation and verification |

## 4. Adapting the Three Source Articles

The articles describe a useful learning project, but their desktop assumptions should not be copied into this Web
application.

### 4.1 Architecture article

Retain the separation of transport, application orchestration, business rules, and persistence. Adapt it to the
repository's existing three-layer style:

    Browser / AdminLayout
        -> REST and JSON WebSocket controllers
        -> platform adapter and existing IM Facades
        -> services and policies
        -> repositories and Flyway-managed tables

Do not introduce a new DDD project hierarchy. The existing Facade, Strategy, Adapter, Repository, and Mapper seams are
sufficient and already tested.

### 4.2 Protocol article

Replace the Netty plus Protobuf plus command-code protocol with:

- HTTPS REST for discovery, bootstrap, history, friendship operations, and group administration;
- ticket-authenticated JSON WebSocket for PING, SEND_MESSAGE, MARK_READ, acknowledgements, message notifications, and
  resynchronization;
- DTOs shared through generated TypeScript definitions only if the repository later adopts generation; V1 continues
  the current handwritten typed service pattern.

The browser cannot consume the old shared Java client Jar, and a private binary protocol would add a gateway without
solving a current requirement.

### 4.3 Six-table database article

| Article table | CyberMario mapping |
|---|---|
| User | existing sys_user |
| Group | existing im_channel and im_group |
| User friend | new im_friendship plus im_contact |
| User group | existing im_membership |
| Talk box | existing im_conversation plus im_conversation_member |
| Chat record | existing im_message plus im_outbox and im_inbox |

The current model is deliberately richer where reliable Web delivery requires it. No old table should replace the
implemented core.

## 5. Target Architecture

### 5.1 Component flow

    /im React workspace
        |
        +-- REST ---------------------------------------------------+
        |                                                          |
        +-- /ws/im JSON WebSocket                                  |
                                                                   v
    PlatformImController -> PlatformImFacade / FriendFacade -> existing IM Facades
                                 |                         |
                                 |                         +-> RoomFacade
                                 |                         +-> ImFacade
                                 |                         +-> DmFacade
                                 |
                                 +-> safe RbacUserDirectoryFacade
                                 +-> FriendshipService

    ClocktowerImAdapter -------------------------------------> existing IM Facades

    Existing IM Facades -> services/policies -> repositories -> PostgreSQL
                                      |
                                      +-> im_outbox -> LocalRealtimeRouter

The platform adapter is the only new composition boundary. It may depend on the safe RBAC user-directory Facade and the
generic IM Facades. The generic IM core must not import RBAC repositories or platform Web DTOs. Clocktower continues to
call the generic Facades and never passes through platform friendship rules.

### 5.2 Pattern decisions

Use:

- Facade for stable application boundaries and DTO-only composition;
- Strategy for the existing context-specific send and visibility rules;
- Adapter for platform and Clocktower context differences;
- Outbox for commit-before-push reliability;
- Mapper/read-model composition for display names and avatars.

Do not add:

- a new DDD aggregate/application/domain/infrastructure module split;
- factories or handler chains for simple friendship state transitions;
- event sourcing, CQRS infrastructure, or a message broker;
- another WebSocket implementation.

Direct transactional services are clearer for the friendship workflow. The platform adapter is justified because DM
eligibility differs by product context and must not leak into the reusable core.

## 6. Identity and Friendship

### 6.1 Identity source

sys_user is the only identity source. A safe directory projection returns only:

- userId;
- accountNo;
- nickname, falling back to accountNo for display;
- avatarUrl;
- enabled status where needed internally.

The public search response must not contain username, email, mobile, password hash, lock state, role assignments, audit
metadata, or deletion state.

Search rules:

- authenticated IM_USER only;
- keyword required and trimmed;
- exact accountNo or nickname prefix/contains matching according to existing repository capabilities;
- maximum page size 20;
- disabled and deleted users excluded;
- the current user excluded from add-friend candidates;
- empty keyword never enumerates all users.

### 6.2 Additive tables

Implementation adds exactly one new versioned Flyway migration. The current next version is V40, but execution must
recheck the sequence immediately before creating it. Existing migrations, especially V30, are immutable.

im_friendship is the pair-level source of truth:

| Column group | Meaning |
|---|---|
| user_lo_id, user_hi_id | normalized unordered pair with a unique constraint |
| requester_user_id | user who opened the current request cycle |
| status | PENDING, ACTIVE, REJECTED, CANCELLED, or REMOVED |
| request_message | optional request note |
| decided_by, decided_at, decision_reason | latest decision |
| requested_at, activated_at, removed_at | lifecycle timestamps |
| common audit columns | repository-standard metadata, optimistic version, and soft-delete fields |

im_contact stores each user's directed view:

| Column group | Meaning |
|---|---|
| friendship_id | pair-level friendship |
| owner_user_id, contact_user_id | unique directed pair |
| remark | owner-specific friend remark |
| status | ACTIVE or REMOVED |
| common audit columns | repository-standard metadata, optimistic version, and soft-delete fields |

The pair row is reused when users reconnect after rejection, cancellation, or deletion. V1 intentionally stores the
latest workflow state rather than an append-only request history.

### 6.3 State transitions and invariants

- A user cannot request themself.
- Only enabled, non-deleted sys_user rows can participate.
- Only one pair row exists regardless of request direction.
- A second request while PENDING is idempotent for the same requester and reported as already pending for the receiver.
- Crossed requests do not auto-accept; the receiver must explicitly accept.
- Only the receiver can accept or reject.
- Only the requester can cancel a pending request.
- Accepting changes the pair to ACTIVE and upserts two ACTIVE im_contact rows in one transaction.
- Either friend can delete; deletion changes the pair to REMOVED and both directed contact rows to REMOVED.
- Re-requesting reopens the same pair row and preserves no hidden active contact.
- Remark changes only the caller's directed contact row.
- An active DM block prevents a new friend request but does not silently delete an existing friendship.
- Friendship acceptance does not create a DM until one user opens it.

### 6.4 DM behavior

The platform Web adapter checks im_friendship.status = ACTIVE before opening a DM or sending to an existing DM, then
delegates to DmFacade or ImFacade. Both POST /api/im/messages and WebSocket SEND_MESSAGE use this adapter, so an old
conversation cannot bypass the friendship rule. The generic Facades remain friendship-agnostic for trusted in-process
adapters.

After a friend is deleted:

- the existing DM conversation and history are retained;
- both participants may still read their own historical conversation;
- the platform UI renders it read-only;
- sending or reopening for active chat fails until friendship is ACTIVE again.

DM block remains a stronger, directed governance action. A blocked pair cannot send even while friendship is ACTIVE.
Unblocking restores sending only when the friendship is still ACTIVE.

## 7. Platform Surfaces

### 7.1 Public general channel

An idempotent bootstrap creates or reuses:

| Field | Value |
|---|---|
| contextType | PLATFORM |
| contextId | null |
| channelKey | general |
| name | 公共频道 |
| visibility | PUBLIC |
| joinPolicy | OPEN |

The owner is resolved from a configured account number, defaulting to the configured RBAC bootstrap administrator.
The IM bootstrap runs after RBAC resource and administrator bootstrap. If general-channel bootstrap is enabled but the
owner cannot be resolved, startup fails with a clear configuration error instead of writing an ownerless channel.

Behavior:

- any authenticated IM_USER may discover and read history;
- joining is required to post, receive realtime notifications, and accumulate unread;
- join is immediate because the policy is OPEN;
- the platform page pins the channel in discovery;
- regular users cannot create additional PLATFORM channels in V1.

### 7.2 Groups

- Every IM_USER may create a standalone group under contextType PLATFORM.
- The creator becomes OWNER and an active conversation member.
- Supported join policies are OPEN and APPROVAL.
- OPEN activates membership immediately.
- APPROVAL produces a pending request for owner review.
- Owner/admin can list pending requests, accept, reject, list members, and remove a member.
- Member promotion, ownership transfer, invitations, and nested groups are deferred.
- Existing mute, announcement, ban, leave, and read/post policies remain authoritative.

The platform adapter fixes contextType to PLATFORM rather than trusting a browser-supplied arbitrary context.

## 8. Web API Contract

Existing core endpoints remain unless explicitly wrapped below. New platform endpoints are under /api/im/platform.

### 8.1 Bootstrap and read models

| Method and path | Result |
|---|---|
| GET /api/im/platform/bootstrap | current user projection, public channel, platform conversations, unread total, friend-request count |
| GET /api/im/platform/conversations | display-ready conversation summaries |
| GET /api/im/platform/users?keyword=... | safe user search |

A platform conversation summary contains the core conversation fields plus:

- displayType: PUBLIC_CHANNEL, GROUP, or DM;
- title and avatarUrl;
- peerUserId for DM;
- surface id and current membership status for channels/groups;
- canRead and canPost;
- last-message sender display projection where available.

The response should batch-load users and surfaces to avoid per-row identity queries.

### 8.2 Friendship

| Method and path | Action |
|---|---|
| GET /api/im/platform/friends | list active directed contacts |
| GET /api/im/platform/friend-requests?box=INCOMING | list incoming requests |
| GET /api/im/platform/friend-requests?box=OUTGOING | list outgoing requests |
| POST /api/im/platform/friend-requests | create or reopen a request |
| POST /api/im/platform/friend-requests/{id}/accept | accept as receiver |
| POST /api/im/platform/friend-requests/{id}/reject | reject as receiver |
| POST /api/im/platform/friend-requests/{id}/cancel | cancel as requester |
| PATCH /api/im/platform/friends/{friendUserId} | update caller-owned remark |
| DELETE /api/im/platform/friends/{friendUserId} | remove the friendship for both sides |

### 8.3 DM, group, and membership

| Method and path | Adjustment |
|---|---|
| POST /api/im/dms | route through the platform friendship gate before DmFacade |
| POST /api/im/messages | route DM sends through the platform friendship gate before ImFacade |
| WebSocket SEND_MESSAGE | apply the same platform DM gate before ImFacade |
| POST /api/im/platform/groups | create a PLATFORM standalone group |
| GET /api/im/platform/groups | discover PLATFORM groups |
| GET /api/im/surfaces/{type}/{id}/members | owner/member-authorized member list |
| GET /api/im/surfaces/{type}/{id}/join-requests | owner/admin-authorized pending list |
| DELETE /api/im/surfaces/{type}/{id}/members/{userId} | owner/admin removes a member |

Message history, non-DM send, mark-read, join, leave, block, mute, announcement, ban, and WebSocket-ticket paths continue
to use the implemented core contract.

All collection endpoints are bounded and paginated where the result can grow. Commands return stable IM error codes for
frontend state handling rather than exposing repository exceptions.

## 9. Authorization

### 9.1 Roles

IM_USER receives:

- menu:im;
- platform bootstrap, directory, friendship, conversation, history, send, read, join, leave, group-create, DM,
  block/unblock, and WebSocket-ticket permissions;
- instance-management endpoints for join decisions, member lists/removal, and per-surface governance; the service still
  requires OWNER or ADMIN membership on the target surface;
- the existing self-authentication and current-user permissions.

IM_ADMIN receives:

- all IM_USER permissions;
- platform channel administration;
- global/cross-surface governance endpoints.

SUPER_ADMIN continues to bypass through the existing RBAC mechanism. IM_ADMIN is not automatically assigned.

### 9.2 Registration and existing users

- New registrations receive IM_USER in the existing default-role assignment transaction.
- An idempotent bootstrap grants IM_USER to existing enabled human sys_user rows that do not already have it.
- Disabled/deleted users and non-sys_user agent records are excluded.
- The backfill is configuration-controlled and tested separately; it does not grant IM_ADMIN.

Generic channel/group creation endpoints are not granted to IM_USER; the user role receives the fixed-context platform
group endpoint instead. API-level RBAC remains coarse. Surface ownership, membership, friendship, mute, ban, and block
checks remain in services and policies.

## 10. Realtime and Consistency

### 10.1 Runtime

Normal single-instance runtime enables:

- im.realtime.dispatcher.enabled = true;
- im.realtime.dispatcher.runner.enabled = true;
- the existing LocalRealtimeRouter.

Tests explicitly disable the runner unless they are testing dispatch. No Redis pub/sub or broker is introduced.

The local router continues to send a conversation event only to active conversation members. Consequently, a user who
has not joined the public channel can read it through REST but does not receive push or unread tracking, matching the
confirmed rule.

### 10.2 Client synchronization

The WebSocket is a notification and command channel, not the only source of truth:

- SEND_ACK supplies the sender's committed message;
- MESSAGE_PUSH carries conversation and sequence hints;
- the client fetches messages after its last known sequence;
- READ_UPDATED updates the caller's exact cursor;
- RESYNC or reconnect reloads conversation summaries and incrementally reconciles the active conversation;
- clientMsgId prevents duplicate messages after retry.

The platform client does not send SUBSCRIBE. Existing SUBSCRIBE input remains accepted as a deprecated no-op for
compatibility because actual routing is membership-based at the user connection registry. A future protocol version may
remove it after all clients stop sending it.

### 10.3 Read semantics

V1 guarantees only:

- exact unread count for the current user;
- monotonic lastReadSeq for the current user;
- idempotent mark-read;
- history recovery by sequence.

It does not claim peer-read, device-specific read, online, typing, or delivered-to-device state.

## 11. Web Experience

The /im page is desktop-Web-first and uses three internal columns inside AdminLayout:

1. Activity rail: messages, contacts, groups, public channel, and pending-request badge.
2. List pane: conversations, friends, requests, groups, or discovery results for the selected activity.
3. Detail pane: message thread and composer, friend profile/actions, or group member/review management.

Core interactions:

- public channel is pinned and shows join state;
- conversation list is sorted by latest activity with exact unread badges;
- selecting a conversation loads latest messages and marks read only after visible reconciliation;
- send uses an optimistic row keyed by clientMsgId, then replaces it with SEND_ACK;
- failed sends remain retryable without creating duplicates;
- friend search opens a bounded drawer/modal and never exposes admin user fields;
- group creation supports name plus OPEN/APPROVAL only;
- group management shows members and pending requests to authorized owners/admins;
- historical DM after friend deletion shows a read-only explanation and an add-friend action;
- basic emoji is ordinary Unicode text and requires no new dependency.

Reuse imService, useImSocket, ImSurfaceBrowser, and join controls where their contracts fit. Extract generic message and
reconnect state from ClocktowerChatPanel rather than importing Clocktower components into the platform page. The
AI-oriented ChatWorkspace may contribute layout primitives, but platform IM must not inherit agent-chat semantics.

## 12. Compatibility and Migration

- Add one new Flyway version; never edit V26, V30, or any historical migration.
- Do not drop, rename, truncate, or rewrite current IM tables.
- Existing channels, groups, conversations, messages, read cursors, outbox rows, and Clocktower mappings remain valid.
- Friendship rules apply at the platform Web adapter. ClocktowerImAdapter continues to call core Facades directly.
- Existing non-friend DM history remains readable and becomes read-only for platform sends.
- No data is copied from im_message into another chat-record table.
- Rollback is feature/config rollback plus leaving additive tables in place; it is not a destructive down migration.

## 13. Delivery Slices

| Slice | Outcome |
|---|---|
| 1 | additive friendship schema and transactional domain behavior |
| 2 | safe user directory, friend APIs, and friend-gated platform DM |
| 3 | platform conversation read model, group member/review queries, and general-channel bootstrap |
| 4 | IM RBAC resources, default role assignment, and existing-user backfill |
| 5 | enabled single-node Outbox dispatch and recovery contract |
| 6 | /im data layer, state model, and three-column UI |
| 7 | full regression, PostgreSQL gate, docs, and checklist update |

Each slice is independently tested and committed. Implementation must not start the application automatically.

## 14. Acceptance Criteria

The V1 is complete only when:

- a newly registered and an existing enabled user both receive the /im menu;
- two users can request, accept, list, remark, and delete friendship;
- a non-friend cannot open or send a platform DM;
- a deleted friend retains readable DM history but cannot send;
- block/unblock and friendship checks compose correctly;
- an IM_USER can create OPEN and APPROVAL groups;
- owners can review requests and see/remove members;
- PLATFORM/general is created once across repeated startup;
- users can read general without joining and must join to post, receive push, and accrue unread;
- WebSocket reconnect and RESYNC recover messages without duplicates;
- exact unread and mark-read behavior remains correct;
- Clocktower IM regression tests pass unchanged in behavior;
- H2/Flyway, focused backend, frontend, and disposable PostgreSQL gates pass;
- no existing migration is modified and no existing IM data is destroyed.

## 15. Resolved Questions

All questions from the analysis pass are resolved as follows:

1. Current CyberMario modular monolith, not a new project.
2. Friend DM, groups, and one public channel.
3. Friendship gate applies only to platform Web DM; Clocktower/business adapters are unchanged.
4. Full friend lifecycle and remarks; no friend folders.
5. Every IM_USER may create groups; OPEN and APPROVAL only.
6. Bootstrap PLATFORM/general; public REST read, join to post/push/unread.
7. IM_USER for registered users and separate IM_ADMIN.
8. /im three-column workspace within AdminLayout.
9. TEXT, SYSTEM, and Unicode emoji only.
10. Exact own unread/read cursor only.
11. Single instance with local Outbox dispatch; no Redis fanout.
12. Preserve all current IM and Clocktower data and behavior.
13. One tenant, sys_user humans only; agents are not platform IM users.

No unresolved product question blocks implementation. Operational values such as the bootstrap owner account and
disposable PostgreSQL test credentials are configuration inputs, not design decisions.
