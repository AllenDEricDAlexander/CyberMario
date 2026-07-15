# 任务 01：数据库基础 —— Actor / Agent / Seat 字段

## 目标

引入一等 Actor 模型，让 Agent 不再伪装成真实用户，也不再依赖负数 `userId` 或“系统用户”。这是后续所有 Agent
Player、麦序、动作执行器、记忆和任务队列的基础。

## 当前代码现状

## 当前仓库断点

- `fe/src/modules/clocktower/RoomListPage.tsx` 已经提交 `agentSeatCount`，但后端创建房间还没有消费这个字段。
- `ClocktowerRoomLobbyServiceImpl#createRoom` 当前只按 `playerCount` 循环创建 `openSeat(...)`，`openSeat` 的 metadata 只有
  `ready=false`。
- `ClocktowerGameLifecycleServiceImpl#validateStartSeats` 当前要求座位有 `userId`、`OCCUPIED`、`ready=true`，并且
  `realUser(metadata)` 会拒绝 `fake=true` 或 `agent=true`。
- 新游戏视图已经在读 `clocktower_game_*`：`ClocktowerGameViewServiceImpl` / `ClocktowerViewerResolver`。
- 旧玩家动作和旧流程仍然读写旧模型：`ClocktowerActionServiceImpl`、`ClocktowerFlowServiceImpl` 使用旧 `ClocktowerRoomPo` /
  `ClocktowerSeatPo` / `ClocktowerNominationPo` / `ClocktowerVotePo`。
- `ClocktowerRoomPlayPage.tsx` 对新 `gameView` 的玩家动作目前是 `actionControlsEnabled={false}`；旧 `GameRoomSurface` 仍会向
  `/api/clocktower/rooms/{roomId}/actions` 提交动作。
- IM 表 `im_conversation_member.user_id` 是非空用户 ID；Agent 不应该被塞进 IM 成员表。

当前 `V26__create_clocktower_room_game_and_im.sql` 已经有：

```text
clocktower_room_profile
clocktower_room_seat
clocktower_game
clocktower_game_seat
clocktower_game_event
im_conversation
im_conversation_member
```

但 `clocktower_room_seat` 和 `clocktower_game_seat` 都是以 `user_id` 表达玩家身份。真正 Agent Player 需要不依赖
`user_id`。

## 改动范围

新增迁移：`Vxx__clocktower_actor_agent_foundation.sql`。

### 1. 新增 `clocktower_actor`

```sql
create table clocktower_actor (
    id bigserial primary key,
    actor_type varchar(32) not null,
    user_id bigint null,
    display_name varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_actor_type on clocktower_actor(actor_type) where deleted = false;
create index idx_clocktower_actor_user on clocktower_actor(user_id) where deleted = false;
```

建议枚举：

```text
HUMAN
AGENT
STORYTELLER
SYSTEM
```

第一阶段可以只落 `HUMAN` 和 `AGENT`。ST 仍然通过 `room_profile.storyteller_user_id` 判断，后续再考虑正式 Actor 化。

### 2. 新增 `clocktower_agent_profile`

```sql
create table clocktower_agent_profile (
    id bigserial primary key,
    name varchar(128) not null,
    display_name_template varchar(128) not null,
    strategy_level varchar(32) not null default 'NORMAL',
    talkativeness int not null default 50,
    deception_level int not null default 50,
    aggression int not null default 50,
    risk_tolerance int not null default 50,
    model_provider varchar(64) null,
    model_name varchar(128) null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create unique index uk_clocktower_agent_profile_name
    on clocktower_agent_profile(name)
    where deleted = false;
```

初始化 5 - 10 个默认 profile：

```sql
insert into clocktower_agent_profile(name, display_name_template, strategy_level, talkativeness, deception_level, aggression, risk_tolerance)
values
('balanced', 'Agent {n}', 'NORMAL', 50, 50, 50, 50),
('quiet', 'Agent {n}', 'QUIET', 25, 40, 35, 40),
('aggressive', 'Agent {n}', 'AGGRESSIVE', 65, 60, 75, 60),
('careful', 'Agent {n}', 'CAREFUL', 45, 35, 35, 25);
```

### 3. 新增 `clocktower_agent_instance`

```sql
create table clocktower_agent_instance (
    id bigserial primary key,
    room_id bigint not null,
    game_id bigint null,
    profile_id bigint not null,
    actor_id bigint not null,
    room_seat_id bigint null,
    game_seat_id bigint null,
    status varchar(32) not null default 'ACTIVE',
    auto_mode varchar(32) not null default 'FULL_AUTO',
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_agent_instance_room
    on clocktower_agent_instance(room_id)
    where deleted = false;

create index idx_clocktower_agent_instance_game
    on clocktower_agent_instance(game_id)
    where deleted = false;

create unique index uk_clocktower_agent_instance_actor
    on clocktower_agent_instance(actor_id)
    where deleted = false;
```

`auto_mode` 建议：

```text
FULL_AUTO      -- 自动发言、提名、投票、夜晚选择
ST_APPROVAL    -- Agent 给建议，ST 确认后执行
PAUSED         -- 暂停 Agent 自动行为
```

### 4. 扩展 room seat

```sql
alter table clocktower_room_seat
    add column actor_id bigint null,
    add column actor_type varchar(32) not null default 'HUMAN',
    add column agent_instance_id bigint null;

create index idx_clocktower_room_seat_actor
    on clocktower_room_seat(actor_id)
    where deleted = false;

create index idx_clocktower_room_seat_agent
    on clocktower_room_seat(agent_instance_id)
    where deleted = false;
```

兼容约束建议先用应用层校验，不急着加复杂 DB check constraint，避免历史数据迁移卡住。

### 5. 扩展 game seat

```sql
alter table clocktower_game_seat
    add column actor_id bigint null,
    add column actor_type varchar(32) not null default 'HUMAN',
    add column agent_instance_id bigint null;

create index idx_clocktower_game_seat_actor
    on clocktower_game_seat(actor_id)
    where deleted = false;

create index idx_clocktower_game_seat_agent
    on clocktower_game_seat(agent_instance_id)
    where deleted = false;
```

## 数据兼容策略

已有真人 seat：

```text
actor_type = HUMAN
actor_id = null
agent_instance_id = null
user_id 保持原样
```

不要在迁移里强行给历史用户补 actor。可以在后续服务层懒创建 `HUMAN` actor，或者仅新房间使用 actor。

## 验收标准

- 迁移可以在空库和已有 V26+ 库上执行。
- 旧房间不需要立即拥有 `actor_id` 也能继续读取。
- 新字段不会破坏现有 unique index。
- Agent seat 可以表达为：

```text
user_id = null
actor_type = AGENT
actor_id != null
agent_instance_id != null
```

## 测试建议

- Flyway migration 测试。
- 插入 HUMAN seat / AGENT seat 的 repository smoke test。
- 确认 `im_conversation_member.user_id not null` 不受 Agent seat 影响。

## 不在本任务做

- 不改创建房间逻辑。
- 不改开局校验。
- 不实现 Agent 行为。
- 不改前端。
