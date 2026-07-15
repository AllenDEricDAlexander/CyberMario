# 任务 13：前端改造 —— 大厅 Agent、游戏动作、公聊麦序 UI

## 目标

把前端从“只认识真实 user seat”的页面升级为：

```text
- 大厅能正确显示 Agent seat 并允许开局
- 新 game view 能提交新 /games/{gameId}/actions
- 公聊区域显示轮流麦、抢麦 5 分钟、当前麦持有人和倒计时
- Agent 有清晰标识，但不会被当成 IM 用户
```

## 依赖

- 任务 03：后端创建 Agent seat。
- 任务 04：开局允许 Agent。
- 任务 05：公聊麦序接口。
- 任务 06：新 game action API。

## 当前前端现状

- `RoomListPage.tsx` 已有 `agentSeatCount` 初始字段和表单请求。
- `RoomLobbyPage.tsx` 的开始判断目前会把 `Boolean(seat.userId)` 当作“有玩家”。Agent 没有 `userId` 后会导致前端误判。
- `ClocktowerRoomPlayPage.tsx` 在新 `gameView` 下给玩家和观众传 `actionControlsEnabled={false}`。
- 旧 `GameRoomSurface` 动作提交仍走 `submitClocktowerPlayerAction(view.roomId, ...)`，对应旧
  `/api/clocktower/rooms/{roomId}/actions`。
- `clocktowerService.ts` 已有 `getClocktowerGameView('/api/clocktower/games/{gameId}/view')`，但 action submit 还走旧
  room endpoint。

## 类型改造

在 `fe/src/modules/clocktower/types.ts` 或实际类型文件中补：

```ts
export type ClocktowerActorType = 'HUMAN' | 'AGENT' | 'STORYTELLER' | 'SYSTEM';

export interface ClocktowerRoomSeatView {
  id: number;
  seatNo: number;
  userId?: number | null;
  actorId?: number | null;
  actorType?: ClocktowerActorType;
  agentInstanceId?: number | null;
  isAgent?: boolean;
  displayName: string;
  roleCode?: string | null;
  status: string;
  metadata?: Record<string, unknown>;
}

export interface ClocktowerGameSeatView {
  id: number;
  seatNo: number;
  userId?: number | null;
  actorId?: number | null;
  actorType?: ClocktowerActorType;
  agentInstanceId?: number | null;
  isAgent?: boolean;
  displayName: string;
  roleCode?: string | null;
  lifeStatus?: string;
  publicLifeStatus?: string;
  hasDeadVote?: boolean;
}
```

## 大厅页改造

### `RoomLobbyPage.tsx`

把“是否有玩家”改成：

```ts
const isAgentSeat = (seat: ClocktowerRoomSeatView) =>
  seat.actorType === 'AGENT' || seat.isAgent || Boolean(seat.agentInstanceId);

const hasPlayer = (seat: ClocktowerRoomSeatView) =>
  Boolean(seat.userId) || isAgentSeat(seat);
```

开始游戏判断：

```ts
const canStartSeat = (seat: ClocktowerRoomSeatView) =>
  hasPlayer(seat) && Boolean(seat.roleCode) && isReady(seat);
```

UI 展示：

```text
Agent Alice [Agent] [Ready] [自动]
```

不要显示“等待用户加入”。

## Service 改造

### 新 action API

`clocktowerService.ts` 新增：

```ts
export async function submitClocktowerGameAction(
  gameId: number,
  request: ClocktowerGameActionRequest,
): Promise<ClocktowerGameActionResponse> {
  return api.post(`/api/clocktower/games/${gameId}/actions`, request);
}
```

保留旧：

```ts
submitClocktowerPlayerAction(roomId, request)
```

旧函数只给旧 room play 页面使用。

### Mic API

```ts
export async function getClocktowerMicSession(gameId: number) { ... }
export async function grabClocktowerMic(gameId: number) { ... }
export async function releaseClocktowerMic(gameId: number) { ... }
export async function finishClocktowerMicTurn(gameId: number, turnId: number) { ... }
export async function skipClocktowerMicTurn(gameId: number, turnId: number) { ... }
export async function extendClocktowerMicSession(gameId: number, seconds: number) { ... }
```

## 游戏页改造

### `ClocktowerRoomPlayPage.tsx`

对于新 `gameView`：

```tsx
<GameRoomSurface
  view={gameRoomView}
  gameView={gameView}
  actionControlsEnabled={gameView.viewerMode === 'PLAYER'}
  useGameActionApi
/>
```

或者拆一个新组件：

```text
ClocktowerGameSurface
```

推荐拆新组件，避免旧 room view 和新 game view 的 action 混在一起。

## 公聊麦序组件

新增：

```text
fe/src/modules/clocktower/components/PublicMicPanel.tsx
```

展示：

```text
当前公聊阶段：轮流麦 / 抢麦 / 已结束
当前发言人：Seat 3 Agent Alice
当前发言剩余：00:31
抢麦剩余：04:12
轮流麦队列：1 -> 2 -> 3 -> 4 -> 5
按钮：
  - 说完了
  - 抢麦
  - 释放麦
  - ST 跳过
  - ST 延长 2 分钟
```

按钮启用规则：

```text
说完了：当前 viewer 是 current_holder
抢麦：session.status=GRAB_MIC && 无 current_holder && viewer 是玩家 && 未超时
释放麦：当前 viewer 是 current_holder
ST 跳过/延长：viewerMode=STORYTELLER
```

## 公聊输入限制

`PUBLIC_SPEECH` 输入框：

```text
- 只有当前麦持有人可输入/发送
- 非持有人显示“等待麦序”
- 抢麦阶段没麦时显示“抢麦后发言”
- 字数限制和后端一致，例如 500 字
```

提交：

```ts
submitClocktowerGameAction(gameId, {
  actorGameSeatId: gameView.mySeat.id,
  actionType: 'PUBLIC_SPEECH',
  content,
});
```

提交成功后可自动调用 `finishClocktowerMicTurn`，或者让用户手动点“说完了”。建议 v1 用户手动，Agent 自动。

## Agent 标识

所有 seat / event / 发言处：

```text
Agent badge：轻量显示，避免误以为真人在线
```

示例：

```text
Agent 2 🤖
```

但不要在聊天列表里把 Agent 当 IM 成员展示。

## 验收标准

- 创建房间时选择 `agentSeatCount=4`，大厅能看到 4 个 Agent seat。
- 前端开始按钮能把 Agent 当作已占用 ready seat。
- 新 game view 玩家可以通过 `/games/{gameId}/actions` 提交公聊。
- 没麦时输入框禁用。
- 轮流麦队列和当前发言人可见。
- 抢麦 5 分钟倒计时可见。
- Agent 发言有 Agent badge。
- 旧 room action 页面不被破坏。

## 测试建议

- `canStartClocktowerRoom_countsAgentSeatAsPlayer`
- `publicSpeechInput_disabledWhenNotMicHolder`
- `grabMicButton_visibleDuringGrabStage`
- `agentSeat_rendersAgentBadge`
- `newGameAction_usesGameEndpoint`

## 不在本任务做

- Agent 决策逻辑。
- ST Agent 控制台详情；任务 14 做。
- 私聊。
