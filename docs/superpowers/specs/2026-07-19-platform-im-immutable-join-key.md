# 2026-07-19 Platform IM Immutable Join Key

Status: Confirmed and implemented
Supersedes: the invitation-only join-entry rule in
`2026-07-17-platform-web-im-membership-correction.md`

## Decision

Channel and group membership entry uses a system-generated immutable join key,
not a database primary key. Existing `channelKey` and `groupKey` remain
context-scoped internal business keys; they are not reused as public join
identities.

The join key follows a Telegram-style share-and-enter flow without making the
database id public:

- channel keys use `chn_` plus a 128-bit Base64 URL-safe random suffix;
- group keys use `grp_` plus the same suffix format;
- the disjoint prefixes make the namespace globally unambiguous across the two
  tables;
- the user can copy a key from a joined surface and another user can enter it
  in the Channel or Group page;
- the surface's existing `OPEN` or `APPROVAL` policy still decides whether the
  result is immediate membership or a pending request;
- direct invitations remain supported.

## Persistence

Exactly one additive migration,
`V49__add_im_surface_join_keys.sql`, adds `join_key` to `im_channel` and
`im_group`, backfills existing rows, makes both columns non-null, applies
type-prefix checks, and creates unique indexes:

- `uk_im_channel_join_key`;
- `uk_im_group_join_key`.

The prefixes occupy disjoint namespaces, so a channel key cannot collide with
a group key. New values are assigned only during entity creation. The JPA
columns are `updatable = false`, entity assignment rejects a second value, and
there is no update API or UI.

## Join and Cache Flow

`POST /api/im/join-requests` accepts:

```json
{
  "joinKey": "grp_0123456789abcdefghijkl",
  "reason": "optional"
}
```

It no longer accepts `surfaceType` or `surfaceId`. The backend derives the
surface type from the key prefix, then resolves the internal id through
`ImSurfaceJoinKeyService`.

The immutable key-to-surface mapping uses cache-aside two-level caching:

1. Guava local cache (L1);
2. Redis cache (L2);
3. indexed database lookup on a miss.

Only the immutable identity mapping is cached. The membership transaction
still performs a pessimistic database lookup by resolved id, ensuring current
status, soft deletion, policy, bans, and parent-channel membership are never
decided from cached surface state. Redis failures degrade to database lookup.

## Platform Rules

- Platform channel and standalone-group joins by internal id are rejected.
- A correct join key permits joining or applying to a Platform channel or
  standalone group.
- A child-group join key does not bypass the active parent-channel membership
  prerequisite.
- Member-scoped lists, history visibility, departure cascades, ownership
  transfer, and invitations remain unchanged.

## UI

- Channel and standalone-group list headers expose “使用 Key 加入”.
- Joined channels and groups display a copyable key.
- Child-group join actions submit the group's key, even though the UI already
  has its internal id for rendering and management.
- Clocktower's browser join call also submits the surfaced join key; trusted
  in-process Clocktower orchestration may continue using the internal facade
  command because it is not a public user entry point.

## Design Pattern Decision

A narrow domain service/facade resolves one public key namespace across channel
and group repositories. This removes duplicated prefix, cache, and lookup
logic. Strategy, Factory, and handler-chain abstractions were rejected because
the key format has only two stable variants and direct branching is clearer.
