# Platform Web IM Membership and Invitation Correction

Date: 2026-07-17
Status: Confirmed and implemented
Supersedes: the public `PLATFORM/general`, surface discovery, and global join
rules in `2026-07-16-platform-web-im-design.md`

## Decision

Platform Web IM is member scoped. It has no automatically created public
channel and no searchable channel/group directory. Channels and standalone
groups are user-created, invitation-only collaboration spaces. A channel can
contain child groups, and parent-channel membership is a hard prerequisite for
seeing or joining them.

The reusable generic IM core and Clocktower behavior remain unchanged. The
Platform rules are enforced by the Platform facade, membership service guards,
and a `PLATFORM` visibility policy.

## Business Rules

1. A new Platform IM user starts with zero channels and zero groups.
2. Any `IM_USER` can create a channel or standalone group and becomes its
   `OWNER`.
3. Channels and standalone groups cannot be searched, discovered, or joined
   through a public/global join endpoint.
4. A surface `OWNER` or `ADMIN` invites an enabled user directly. The invitee
   accepts or rejects the invitation; invitation links and codes are not used.
5. Only an active channel member can list that channel's child groups.
6. Only the channel `OWNER` or `ADMIN` can create a child group.
7. A child group supports `OPEN` or `APPROVAL`, but both modes are available
   only to active members of its parent channel.
8. A child-group invitation also requires the invitee to be an active parent
   channel member both when the invitation is created and accepted.
9. Leaving or removal from a channel deactivates every active membership and
   conversation membership in its child groups, and cancels pending child
   applications and invitations.
10. A former Platform channel/group member retains stored data but cannot read
    its history, receive push, or accrue unread. Rejoining restores access to
    retained history.
11. An owner must transfer every owned child group before leaving its parent
    channel. Ownership transfers only to an active member; the former owner
    becomes `ADMIN`.
12. Existing `PLATFORM/general` rows are not deleted. They are no longer
    bootstrapped or special-cased and appear only to active members as ordinary
    channels.

## Persistence

Exactly one additive migration, `V48__create_im_surface_invitation_schema.sql`,
adds `im_surface_invitation`. The natural key is
`(surface_type, surface_id, invitee_user_id)` and the workflow statuses are
`PENDING`, `ACCEPTED`, `REJECTED`, and `CANCELLED`.

Existing migrations and existing IM data are immutable. Re-invitation reuses
the natural invitation row and returns it to `PENDING` rather than creating an
unbounded duplicate history.

## Platform API Contract

| Endpoint                                                 | Contract                                       |
|----------------------------------------------------------|------------------------------------------------|
| `POST /api/im/platform/channels`                         | Create a caller-owned channel.                 |
| `GET /api/im/platform/channels`                          | List caller's active channel memberships only. |
| `POST /api/im/platform/groups`                           | Create a caller-owned standalone group.        |
| `GET /api/im/platform/groups`                            | List caller's active standalone groups only.   |
| `POST /api/im/platform/channels/{id}/groups`             | Channel owner/admin creates a child group.     |
| `GET /api/im/platform/channels/{id}/groups`              | Active channel member lists child groups.      |
| `POST /api/im/platform/surfaces/{type}/{id}/invitations` | Surface owner/admin directly invites a user.   |
| `GET /api/im/platform/invitations`                       | List caller's pending incoming invitations.    |
| `POST /api/im/platform/invitations/{id}/accept`          | Accept and activate membership.                |
| `POST /api/im/platform/invitations/{id}/reject`          | Reject without creating membership.            |
| `POST /api/im/platform/surfaces/{type}/{id}/owner`       | Transfer ownership to an active member.        |

Generic surface administration remains available to privileged integrations,
but Platform guards reject global channel/standalone joins and recheck parent
membership for child groups.

## Web Workspace

The `/im` rail contains Messages, Contacts, Channels, Standalone Groups, and
Invitations. There is no public-channel shortcut and no surface-search UI.

- Messages sort all active conversations by activity; no channel is pinned.
- Channels lists only active memberships, supports channel creation, and shows
  child groups after a channel is selected.
- Standalone Groups lists only active memberships and explains its direct
  invitation model.
- Owners/admins can search users only for the purpose of a direct invitation,
  inspect members, remove ordinary members, review eligible child-group
  applications, and transfer ownership.
- Invitations allows the invitee to accept or reject pending direct invites.

## Design Pattern Decision

The existing Facade/Adapter and Strategy/Policy seams are retained:

- `PlatformRoomFacade` and `PlatformInvitationFacade` adapt fixed Platform
  business rules without leaking them into Clocktower.
- `PlatformVisibilityPolicy` is the context Strategy that replaces generic
  public channel readability for `PLATFORM` only.
- `MembershipService` remains the transaction boundary for membership and
  cascade invariants.

No new factory, handler chain, or state-machine framework is introduced. The
workflow has four stable statuses and direct transactional transitions, so a
new abstraction would add indirection without a real variation point.

## Verification Boundary

Required proof includes the V42 migration contract, invitation workflow,
parent membership checks, departure cascade, retained-history visibility,
ownership transfer, Platform RBAC routes, Clocktower regression, frontend
service/state/UI tests, TypeScript typecheck, and production build. PostgreSQL
locking/index semantics remain a separate explicit environment gate; H2 does
not substitute for it.
