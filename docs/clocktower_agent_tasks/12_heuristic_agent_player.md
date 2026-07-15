# 任务 12：Heuristic Agent Player —— 不依赖 LLM 也能完整参与

## 目标

实现第一版真正 Agent Player 决策策略。没有 LLM 时，Agent 也要能：

```text
- 轮到自己公聊时发言或 pass
- 抢麦阶段按策略抢麦
- 主动提名
- 投票
- 夜晚选择目标
- 邪恶阵营 bluff
- 死亡后继续有限参与，并管理死票
```

## 依赖

- 任务 05：公聊麦序。
- 任务 06：新 action executor。
- 任务 09：night task。
- 任务 10：Agent Runtime。
- 任务 11：AgentPrivateView + memory。

## 包结构

```text
clocktower/agent/strategy
  ├── ClocktowerAgentPolicy.java
  ├── AgentDecision.java
  ├── AgentDecisionContext.java
  ├── AgentIntent.java
  ├── HeuristicAgentPolicy.java
  ├── AgentSpeechPlanner.java
  ├── AgentNominationPlanner.java
  ├── AgentVotePlanner.java
  ├── AgentNightChoicePlanner.java
  └── troublebrewing
      ├── TroubleBrewingBluffPlanner.java
      ├── ImpAgentSkill.java
      ├── PoisonerAgentSkill.java
      ├── MonkAgentSkill.java
      └── ...
```

## 策略接口

```java
public interface ClocktowerAgentPolicy {
    AgentDecision decide(AgentDecisionContext context);
}
```

```java
public record AgentDecisionContext(
        AgentPrivateView view,
        ClocktowerAgentProfilePo profile,
        List<AgentIntent> legalIntents,
        String triggerType,
        Map<String, Object> runtimeState
) {}
```

```java
public sealed interface AgentIntent {
    record PublicSpeech(String content) implements AgentIntent {}
    record GrabMic(String reason) implements AgentIntent {}
    record FinishSpeech() implements AgentIntent {}
    record Nominate(Long targetGameSeatId, String reason) implements AgentIntent {}
    record Vote(Long nominationId, boolean vote, String reason) implements AgentIntent {}
    record NightChoice(Long taskId, List<Long> targetGameSeatIds, Map<String, Object> payload) implements AgentIntent {}
    record Pass(String reason) implements AgentIntent {}
}
```

## 公聊策略

### 轮流麦

触发：`MIC_TURN_STARTED`

Agent 必须选择：

```text
PUBLIC_SPEECH
或 PASS
```

基础规则：

```text
- talkativeness 高：更倾向发言
- 有私人信息且是好人：倾向分享部分信息
- 邪恶：根据 bluff plan 发言，不暴露真实角色
- 没信息：提出问题或总结投票/提名观察
- 发言长度限制，避免刷屏
```

示例模板：

```text
好人信息位：
“我这边有一点信息，暂时更想听 {seatNo} 的说法。{reason}”

普通好人：
“目前我比较在意 {seatNo} 的投票/发言，想看他怎么解释。”

邪恶 bluff：
“我倾向相信 {allySeat}，但 {targetSeat} 的说法有点对不上。”
```

### 抢麦 5 分钟

触发：`MIC_GRAB_OPENED` 或 `PUBLIC_EVENT_APPENDED`

抢麦条件：

```text
- 自己有强信息要公布
- 自己被点名质疑
- 邪恶需要转移焦点
- 当前没有麦持有人
- profile.talkativeness / aggression 足够
```

抢麦后仍只能发一条或短发言，然后 `FINISH_SPEECH`。

## 提名策略

触发：`PHASE_CHANGED:NOMINATION` 或 `TIMER_TICK`。

好人评分：

```text
goodNominationScore(target) =
    suspicion(target)
  + contradictionScore(target)
  + privateInfoHit(target)
  + votePatternSuspicion(target)
  - trust(target)
  - usefulClaimRisk(target)
```

邪恶评分：

```text
evilNominationScore(target) =
    goodThreat(target)
  + frameable(target)
  + protectsDemon(target)
  - evilAllyRisk(target)
```

提名阈值：

```text
好人：score >= 65
邪恶：score >= 55，且不要过早牺牲恶魔
```

限制：

```text
- 死人不能提名
- 每天只能提名一次
- 有 open nomination 时不提名
```

## 投票策略

触发：`VOTE_WINDOW_OPENED`。

好人：

```text
- 目标高嫌疑：投 yes
- 信息不足且票已经很高：谨慎 no
- 自己死了且只有死票：阈值提高，例如 score >= 80 才 yes
```

邪恶：

```text
- 目标是好人且可能过票：yes
- 目标是恶魔：no，除非特殊牺牲策略
- 目标是爪牙：可根据局势牺牲
- 偶尔伪装性投票，避免投票模式太机械
```

## 夜晚选择策略

Trouble Brewing v0：

| 角色             | Heuristic                                       |
|----------------|-------------------------------------------------|
| Imp            | 优先杀信息位、可信好人、保护链外目标；避免杀可被怀疑的锅位                   |
| Poisoner       | 优先毒 Fortune Teller / Empath / Undertaker / Monk |
| Monk           | 保护可信信息位或自己认为会被杀的人，不能选自己时排除自己                    |
| Fortune Teller | 选一个高嫌疑 + 一个低嫌疑，或重复验证关键对象                        |
| Butler         | 选择最可信活人作为 master                                |
| Ravenkeeper    | 死亡触发时查最高嫌疑对象                                    |
| Slayer         | 白天强嫌疑时才开枪；v0 可以作为特殊 action 后置                   |

```

## Bluff 策略

邪恶 Agent 开局生成 bluff plan：

```json
{
  "claimRoleCode": "chef",
  "backupClaimRoleCode": "soldier",
  "fakeInfo": {
    "chefNumber": 1
  },
  "protectSeats": [4],
  "pushTargets": [2, 5]
}
```

规则：

```text
- 不要所有邪恶都跳同一角色
- bluff 要和公开死亡/投票/已知声明尽量不冲突
- Imp 优先选择安全 bluff
- Spy 可以基于 grimoire 选择更强 bluff
```

## Agent 决策记录

即使任务 15 才做完整 LLM 审计，Heuristic 也应该写基础 decision summary：

```text
trigger_type
legal_intents
selected_intent
reasoning_summary
```

可以先写到 task metadata，任务 15 再迁正式 `clocktower_agent_decision` 表。

## 验收标准

- Agent 轮到轮流麦时会发言或 pass，并释放麦。
- 抢麦阶段 Agent 不会违反“同一时间一个麦”。
- Agent 能在 nomination 阶段主动提名。
- Agent 能对 open nomination 投票。
- 死 Agent 不会提名，使用死票后 `hasDeadVote=false`。
- Agent 能为基础 night task 选择合法目标。
- 邪恶 Agent 会生成并使用 bluff plan，而不是直接暴露真实身份。

## 测试建议

- `agentMicTurn_speaksAndFinishes`
- `agentGrabMic_respectsMicAvailability`
- `goodAgentNominatesHighSuspicionTarget`
- `evilAgentAvoidsNominatingDemon`
- `deadAgentCannotNominate`
- `deadAgentUsesDeadVoteOnce`
- `impAgentChoosesAliveNonSelfTarget`
- `poisonerAgentPrioritizesInfoRole`

## 不在本任务做

- LLM。
- 私聊。
- 高级脚本角色。
