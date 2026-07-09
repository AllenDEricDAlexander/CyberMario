# Clocktower Storyteller Agent Control Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Storyteller control console for Agent control, public mic overrides, and night-task fallback.

**Architecture:** Add a focused backend Agent control facade plus controller, extend existing mic/night task services only at their current boundaries, and add three focused Storyteller frontend panels under `StorytellerGameSurface`. Preserve existing game view, mic, night task, and Agent runtime contracts instead of replacing them.

**Tech Stack:** Spring Boot, WebFlux controller wrappers, Spring Data JPA, Jackson, JUnit 5, AssertJ, React 19, TypeScript 6, Ant Design 6, Vitest, existing `requestJson` wrapper.

---

## File Structure

Create:

- `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentConsoleView.java`: Storyteller-facing Agent list row.
- `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentTaskView.java`: Storyteller-facing Agent task row.
- `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentMemoryView.java`: Storyteller-facing Agent memory row.
- `be/src/main/java/top/egon/mario/clocktower/agent/control/service/ClocktowerAgentControlService.java`: Agent control API boundary.
- `be/src/main/java/top/egon/mario/clocktower/agent/control/service/impl/ClocktowerAgentControlServiceImpl.java`: permission checks, Agent mutations, run-now task creation, memory/task listing, audit events.
- `be/src/main/java/top/egon/mario/clocktower/agent/control/web/ClocktowerAgentControlController.java`: `/api/clocktower/games/{gameId}/agents` endpoints.
- `be/src/test/java/top/egon/mario/clocktower/agent/control/ClocktowerAgentControlServiceTests.java`: backend Agent control coverage.
- `fe/src/modules/clocktower/components/StorytellerAgentPanel.tsx`: Agent operations panel.
- `fe/src/modules/clocktower/components/StorytellerAgentPanel.test.tsx`: Agent panel rendering and action tests.
- `fe/src/modules/clocktower/components/StorytellerMicControlPanel.tsx`: Storyteller mic operations panel.
- `fe/src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx`: mic panel rendering and action tests.
- `fe/src/modules/clocktower/components/StorytellerNightTaskPanel.tsx`: night task fallback panel.
- `fe/src/modules/clocktower/components/StorytellerNightTaskPanel.test.tsx`: night task panel rendering and action tests.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerType.java`: add `ST_RUN_NOW`.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/repository/ClocktowerAgentTaskRepository.java`: add Agent-specific recent task query.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/repository/ClocktowerAgentMemoryRepository.java`: add Agent-specific recent memory query.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`: emit Storyteller-specific override event types for skip, extend, and close.
- `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`: cover Storyteller mic override event names.
- `be/src/main/java/top/egon/mario/clocktower/game/night/dto/ClocktowerNightResolveRequest.java`: add optional target override fields.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerGameNightTaskService.java`: add Storyteller random-choice operation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerGameNightTaskServiceImpl.java`: implement random-choice choice writing and audit.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerNightResolutionServiceImpl.java`: implement Storyteller target override before normal resolution.
- `be/src/main/java/top/egon/mario/clocktower/game/night/web/ClocktowerGameNightTaskController.java`: add `POST /{taskId}/random-choice`.
- `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java`: random-choice coverage.
- `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerNightResolutionServiceTests.java`: target override coverage.
- `fe/src/modules/clocktower/clocktowerTypes.ts`: add Agent console, Agent task, Agent memory, and night task control types.
- `fe/src/modules/clocktower/clocktowerService.ts`: add Agent control and game night task service functions.
- `fe/src/modules/clocktower/clocktowerService.test.ts`: endpoint contract tests.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`: wire new tabs into `StorytellerGameSurface`.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`: new tab rendering coverage and service mocks.

No Flyway migration is required.

## Design Pattern Decision

Use a small Facade/Domain Service for Agent control: `ClocktowerAgentControlService`. It coordinates permission checks, Agent instance mutation, task creation, memory/task projection, and game events. Strategy or Command objects would add layers without a real variation point because the Storyteller actions are fixed.

For night tasks, keep the existing RoleSkill strategy and reuse it for target validation and resolution. Do not introduce a second night-task rules abstraction.

For frontend, use component composition inside the existing Storyteller surface. A new page-level abstraction is unnecessary because `StorytellerGameSurface` already owns the Storyteller game context.

### Task 1: Backend Agent Control API

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentConsoleView.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentTaskView.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/dto/ClocktowerAgentMemoryView.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/service/ClocktowerAgentControlService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/service/impl/ClocktowerAgentControlServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/control/web/ClocktowerAgentControlController.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/control/ClocktowerAgentControlServiceTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerType.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/repository/ClocktowerAgentTaskRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/memory/repository/ClocktowerAgentMemoryRepository.java`

- [ ] **Step 1: Write failing Agent control service tests**

Create `ClocktowerAgentControlServiceTests.java` with these test methods. The helper methods in this step create one human seat plus four Agent seats, mark all seats ready with roles, start the game, and create a memory row for the selected Agent.

```java
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.worker.runner.enabled=false"
})
@Transactional
class ClocktowerAgentControlServiceTests {

    @Autowired
    private ClocktowerAgentControlService controlService;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ClocktowerAgentTaskRepository agentTaskRepository;

    @Autowired
    private ClocktowerAgentMemoryRepository agentMemoryRepository;

    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Test
    void pauseAgent_setsAutoModePausedAndAppendsEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());

        ClocktowerAgentConsoleView paused = controlService.pauseAgent(game.gameId(), instance.getId(), owner());

        assertThat(paused.autoMode()).isEqualTo(ClocktowerAgentAutoMode.PAUSED);
        assertThat(agentInstanceRepository.findByIdAndDeletedFalse(instance.getId()).orElseThrow().getAutoMode())
                .isEqualTo(ClocktowerAgentAutoMode.PAUSED);
        assertThat(eventTypes(game.gameId())).contains("AGENT_PAUSED_BY_ST");
    }

    @Test
    void resumeAgent_allowsFullAutoAgainAndAppendsEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        controlService.pauseAgent(game.gameId(), instance.getId(), owner());

        ClocktowerAgentConsoleView resumed = controlService.resumeAgent(game.gameId(), instance.getId(), owner());

        assertThat(resumed.autoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);
        assertThat(eventTypes(game.gameId())).contains("AGENT_RESUMED_BY_ST");
    }

    @Test
    void runNowAgent_createsImmediateTaskAndEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());

        ClocktowerAgentTaskView task = controlService.runNow(game.gameId(), instance.getId(), owner());

        assertThat(task.triggerType()).isEqualTo(ClocktowerAgentTriggerType.ST_RUN_NOW);
        assertThat(task.status()).isEqualTo(ClocktowerAgentTaskStatus.PENDING);
        assertThat(task.availableAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(eventTypes(game.gameId())).contains("AGENT_RUN_NOW_REQUESTED_BY_ST");
    }

    @Test
    void listAgentsIncludesSeatRoleAndRecentTask() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        controlService.runNow(game.gameId(), instance.getId(), owner());

        List<ClocktowerAgentConsoleView> agents = controlService.listAgents(game.gameId(), owner());

        assertThat(agents).hasSize(4);
        assertThat(agents.getFirst().seatNo()).isGreaterThan(1);
        assertThat(agents.getFirst().roleCode()).isNotBlank();
        assertThat(agents.getFirst().recentTaskStatus()).isEqualTo(ClocktowerAgentTaskStatus.PENDING);
    }

    @Test
    void memoryAndTasksAreScopedToSelectedAgentAndGame() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        ClocktowerAgentTaskView task = controlService.runNow(game.gameId(), instance.getId(), owner());
        createMemory(game.gameId(), instance);

        assertThat(controlService.listTasks(game.gameId(), instance.getId(), owner()))
                .extracting(ClocktowerAgentTaskView::taskId)
                .contains(task.taskId());
        assertThat(controlService.listMemory(game.gameId(), instance.getId(), owner()))
                .extracting(ClocktowerAgentMemoryView::memoryType)
                .contains("OBSERVATION");
    }

    private StartedGame startGameWithAgents(int agentSeatCount) {
        List<String> roleCodes = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(roleCodes, agentSeatCount), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId(), roleCodes);
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        return new StartedGame(room.roomId(), started.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
    }

    private void assignReadyRoles(Long roomId, List<String> roleCodes) {
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        for (int index = 0; index < roleCodes.size(); index++) {
            ClocktowerRoomSeatPo seat = seats.get(index);
            seat.setRoleCode(roleCodes.get(index));
            seat.setMetadataJson("{\"ready\":true}");
        }
        roomSeatRepository.saveAllAndFlush(seats);
    }

    private ClocktowerAgentInstancePo firstAgent(Long gameId) {
        return agentInstanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(gameId).getFirst();
    }

    private void createMemory(Long gameId, ClocktowerAgentInstancePo instance) {
        ClocktowerAgentMemoryPo memory = new ClocktowerAgentMemoryPo();
        memory.setGameId(gameId);
        memory.setAgentInstanceId(instance.getId());
        memory.setGameSeatId(instance.getGameSeatId());
        memory.setMemoryType("OBSERVATION");
        memory.setContentJson("{\"summary\":\"seat 1 claimed good\"}");
        memory.setDayNo(1);
        memory.setNightNo(1);
        agentMemoryRepository.saveAndFlush(memory);
    }

    private List<String> eventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
    }

    private ClocktowerRoomCreateRequest createRequest(List<String> roleCodes, int agentSeatCount) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                roleCodes.size(),
                null,
                null,
                roleCodes,
                "HUMAN",
                true,
                true,
                agentSeatCount,
                "PUBLIC",
                "OPEN_SEATING"
        );
    }

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
```

- [ ] **Step 2: Run Agent control tests to verify failure**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.agent.control.ClocktowerAgentControlServiceTests test
```

Expected: compilation fails because the control service, controller, DTOs, and repository methods do not exist.

- [ ] **Step 3: Add DTO records**

Add DTO records with only projection fields needed by the console:

```java
public record ClocktowerAgentConsoleView(
        Long agentInstanceId,
        Long actorId,
        Long gameSeatId,
        Integer seatNo,
        String displayName,
        String profileName,
        String status,
        String autoMode,
        String roleCode,
        String alignment,
        String recentTaskStatus,
        String recentTaskTriggerType,
        Map<String, Object> recentTaskResult,
        String recentError
) {
}
```

```java
public record ClocktowerAgentTaskView(
        Long taskId,
        Long gameId,
        Long agentInstanceId,
        Long gameSeatId,
        String triggerType,
        String triggerKey,
        String status,
        int priority,
        Instant availableAt,
        Instant lockedAt,
        String lockedBy,
        int attempts,
        String lastError,
        Map<String, Object> metadata,
        Map<String, Object> result
) {
}
```

```java
public record ClocktowerAgentMemoryView(
        Long memoryId,
        Long gameId,
        Long agentInstanceId,
        Long gameSeatId,
        Long sourceEventId,
        Long sourceEventSeq,
        String memoryType,
        String visibility,
        Long subjectGameSeatId,
        Map<String, Object> content,
        int confidence,
        int dayNo,
        int nightNo,
        Map<String, Object> metadata
) {
}
```

- [ ] **Step 4: Add repository methods and trigger type**

Add:

```java
public static final String ST_RUN_NOW = "ST_RUN_NOW";
```

to `ClocktowerAgentTriggerType`.

Add to `ClocktowerAgentTaskRepository`:

```java
List<ClocktowerAgentTaskPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByIdDesc(
        Long gameId, Long agentInstanceId);
```

Add to `ClocktowerAgentMemoryRepository`:

```java
List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
        Long gameId, Long agentInstanceId);
```

- [ ] **Step 5: Add service interface**

```java
public interface ClocktowerAgentControlService {

    List<ClocktowerAgentConsoleView> listAgents(Long gameId, RbacPrincipal principal);

    ClocktowerAgentConsoleView pauseAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    ClocktowerAgentConsoleView resumeAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    ClocktowerAgentTaskView runNow(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    List<ClocktowerAgentMemoryView> listMemory(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    List<ClocktowerAgentTaskView> listTasks(Long gameId, Long agentInstanceId, RbacPrincipal principal);
}
```

- [ ] **Step 6: Implement minimal service**

Implement `ClocktowerAgentControlServiceImpl` with these rules:

- Load game by `findLockedByIdAndDeletedFalse` for mutating methods and `findByIdAndDeletedFalse` for read-only methods.
- Call `ClocktowerRoomAccessPolicy.requireOwner(...)` through `RoomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())`.
- Require Agent instance belongs to `gameId`.
- For `pause`, set `autoMode=ClocktowerAgentAutoMode.PAUSED`, save, append `AGENT_PAUSED_BY_ST`.
- For `resume`, set `autoMode=ClocktowerAgentAutoMode.FULL_AUTO`, save, append `AGENT_RESUMED_BY_ST`.
- For `runNow`, create a `ClocktowerAgentTaskPo` directly with `availableAt=Instant.now()`, `priority=10`, `triggerType=ST_RUN_NOW`, and unique trigger key `st-run-now:%d:%d:%d`. Append `AGENT_RUN_NOW_REQUESTED_BY_ST`.
- Parse task `metadataJson/resultJson` and memory `contentJson/metadataJson` with `ObjectMapper` and the same `TypeReference<Map<String,Object>>` pattern used by night services.

- [ ] **Step 7: Add reactive controller**

Add `ClocktowerAgentControlController`:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/agents")
public class ClocktowerAgentControlController extends ClocktowerReactiveSupport {

    private final ClocktowerAgentControlService controlService;

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerAgentConsoleView>>> listAgents(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listAgents(gameId, principal));
    }

    @PostMapping("/{agentInstanceId}/pause")
    public Mono<ApiResponse<ClocktowerAgentConsoleView>> pause(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.pauseAgent(gameId, agentInstanceId, principal));
    }

    @PostMapping("/{agentInstanceId}/resume")
    public Mono<ApiResponse<ClocktowerAgentConsoleView>> resume(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.resumeAgent(gameId, agentInstanceId, principal));
    }

    @PostMapping("/{agentInstanceId}/run-now")
    public Mono<ApiResponse<ClocktowerAgentTaskView>> runNow(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.runNow(gameId, agentInstanceId, principal));
    }

    @GetMapping("/{agentInstanceId}/memory")
    public Mono<ApiResponse<List<ClocktowerAgentMemoryView>>> memory(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listMemory(gameId, agentInstanceId, principal));
    }

    @GetMapping("/{agentInstanceId}/tasks")
    public Mono<ApiResponse<List<ClocktowerAgentTaskView>>> tasks(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listTasks(gameId, agentInstanceId, principal));
    }
}
```

- [ ] **Step 8: Run backend Agent control tests**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.agent.control.ClocktowerAgentControlServiceTests test
```

Expected: PASS.

- [ ] **Step 9: Commit Task 1**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent be/src/test/java/top/egon/mario/clocktower/agent/control
git commit -m "feat(clocktower): add storyteller agent control api"
```

### Task 2: Storyteller Mic Override Events

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`

- [ ] **Step 1: Add failing mic event assertions**

Add tests to `ClocktowerPublicMicServiceTests`:

```java
@Test
void storytellerSkipTurnAppendsStorytellerEvent() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());

    micService.skipTurn(game.gameId(), started.currentTurnId(), owner());

    assertThat(eventTypes(game.gameId())).contains("MIC_TURN_SKIPPED_BY_ST");
}

@Test
void storytellerExtendGrabAppendsStorytellerEvent() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());
    while ("ROUND_ROBIN".equals(view.status())) {
        view = micService.finishCurrentTurn(game.gameId(), view.currentTurnId(), owner());
    }

    micService.extendGrabMic(game.gameId(), 120, owner());

    assertThat(eventTypes(game.gameId())).contains("MIC_SESSION_EXTENDED_BY_ST");
}

@Test
void storytellerCloseSessionAppendsStorytellerEvent() {
    StartedGame game = startDayGameWithAgents();
    micService.startDayMicSession(game.gameId(), owner());

    micService.closeSession(game.gameId(), owner());

    assertThat(eventTypes(game.gameId())).contains("MIC_SESSION_CLOSED_BY_ST");
}
```

If `eventTypes` is not already present, add:

```java
private List<String> eventTypes(Long gameId) {
    return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
            .stream()
            .map(ClocktowerGameEventPo::getEventType)
            .toList();
}
```

- [ ] **Step 2: Run mic tests to verify failure**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.game.mic.ClocktowerPublicMicServiceTests test
```

Expected: tests fail because current event types are `MIC_TURN_SKIPPED`, `MIC_SESSION_EXTENDED`, and `MIC_SESSION_CLOSED`.

- [ ] **Step 3: Add Storyteller-specific event constants and close helper parameter**

In `ClocktowerPublicMicServiceImpl`, add:

```java
private static final String EVENT_MIC_TURN_SKIPPED_BY_ST = "MIC_TURN_SKIPPED_BY_ST";
private static final String EVENT_MIC_SESSION_EXTENDED_BY_ST = "MIC_SESSION_EXTENDED_BY_ST";
private static final String EVENT_MIC_SESSION_CLOSED_BY_ST = "MIC_SESSION_CLOSED_BY_ST";
```

Change `skipTurn` to append `EVENT_MIC_TURN_SKIPPED_BY_ST`. Change `extendGrabMic` to append `EVENT_MIC_SESSION_EXTENDED_BY_ST`. Change Storyteller `closeSession` to call a helper that appends `EVENT_MIC_SESSION_CLOSED_BY_ST`, while automatic expiry paths keep the existing `EVENT_MIC_SESSION_CLOSED`.

- [ ] **Step 4: Run mic tests**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.game.mic.ClocktowerPublicMicServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java
git commit -m "feat(clocktower): audit storyteller mic overrides"
```

### Task 3: Night Task Override And Random Choice

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/dto/ClocktowerNightResolveRequest.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerGameNightTaskService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerGameNightTaskServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerNightResolutionServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/web/ClocktowerGameNightTaskController.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerNightResolutionServiceTests.java`

- [ ] **Step 1: Write failing random-choice and override tests**

Add to `ClocktowerGameNightTaskServiceTests`:

```java
@Test
void storytellerRandomChoiceMarksTaskChosenAndAudited() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));
    ClocktowerGameNightTaskPo task = taskFor(game.gameId(), "POISONER");

    ClocktowerNightTaskView view = taskService.randomChoiceTask(game.gameId(), task.getId(), owner());

    assertThat(view.status()).isEqualTo("CHOSEN");
    assertThat(view.choice()).containsKey("targetGameSeatIds");
    assertThat(view.metadata()).containsEntry("source", "ST_RANDOM_CHOICE");
}
```

Add to `ClocktowerNightResolutionServiceTests`:

```java
@Test
void storytellerOverrideTargetRecordsMetadataAndUsesResolution() {
    StartedGame game = startNightTwoWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH",
            "BUTLER", "CHEF", "WASHERWOMAN", "RAVENKEEPER"));
    ClocktowerGameNightTaskPo impTask = taskFor(game.gameId(), "IMP");
    Long targetId = game.seats().getFirst().getId();

    ClocktowerNightTaskView resolved = resolutionService.resolveTask(game.gameId(), impTask.getId(),
            new ClocktowerNightResolveRequest(null, "ST override", List.of(targetId), Map.of()), owner());

    assertThat(resolved.status()).isEqualTo("DONE");
    assertThat(resolved.choice()).containsEntry("source", "ST_OVERRIDE");
    assertThat(gameSeatRepository.findByIdAndDeletedFalse(targetId).orElseThrow().getLifeStatus())
            .isEqualTo("DEAD");
    assertThat(eventTypes(game.gameId())).contains("NIGHT_CHOICE_OVERRIDDEN_BY_ST");
}
```

- [ ] **Step 2: Run night tests to verify failure**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.game.night.ClocktowerGameNightTaskServiceTests,top.egon.mario.clocktower.game.night.ClocktowerNightResolutionServiceTests test
```

Expected: compilation fails because `randomChoiceTask` and the extended resolve request do not exist.

- [ ] **Step 3: Extend resolve request record**

Change `ClocktowerNightResolveRequest` to:

```java
public record ClocktowerNightResolveRequest(
        Map<String, Object> result,
        String note,
        List<Long> targetGameSeatIds,
        Map<String, Object> payload
) {
}
```

- [ ] **Step 4: Add random-choice service method and controller endpoint**

Add to `ClocktowerGameNightTaskService`:

```java
ClocktowerNightTaskView randomChoiceTask(Long gameId, Long taskId, RbacPrincipal principal);
```

Add to `ClocktowerGameNightTaskController`:

```java
@PostMapping("/{taskId}/random-choice")
public Mono<ApiResponse<ClocktowerNightTaskView>> randomChoice(
        @PathVariable Long gameId,
        @PathVariable Long taskId,
        @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> nightTaskService.randomChoiceTask(gameId, taskId, principal));
}
```

- [ ] **Step 5: Implement random-choice**

In `ClocktowerGameNightTaskServiceImpl`, load locked game and task, require Storyteller, require current night, reject done/skipped tasks, load actor seat, seats, role skill, and legal targets. Select a target from `legalTargets.stream().filter(AvailableTargetSpec::selectable).toList()` when non-empty; otherwise use `skill.autoChoose(...)`.

Write `choiceJson` with:

```java
Map<String, Object> choicePayload = new LinkedHashMap<>();
choicePayload.put("targetGameSeatIds", choice.targetGameSeatIds());
choicePayload.put("payload", choice.payload());
choicePayload.put("source", "ST_RANDOM_CHOICE");
```

Merge metadata with:

```java
metadata.put("source", "ST_RANDOM_CHOICE");
metadata.put("requestedByStorytellerUserId", principal.userId());
```

Set status to `CHOSEN`, save, and append `NIGHT_CHOICE_RANDOMIZED_BY_ST`.

- [ ] **Step 6: Implement target override before resolution**

In `ClocktowerNightResolutionServiceImpl.resolveTask`, before manual result handling, check `request.targetGameSeatIds()`. When non-null, write override choice, validate targets through `roleSkill.legalTargets(...)`, set task status to `CHOSEN`, merge metadata `source=ST_OVERRIDE` and `requestedByStorytellerUserId`, append `NIGHT_CHOICE_OVERRIDDEN_BY_ST`, then call the existing normal resolution path.

Manual `result` still bypasses role resolution as before. Empty target override for `RECEIVE_INFO` remains valid; empty target override for target-choice tasks throws `CLOCKTOWER_NIGHT_TARGET_COUNT_INVALID`.

- [ ] **Step 7: Run night tests**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.game.night.ClocktowerGameNightTaskServiceTests,top.egon.mario.clocktower.game.night.ClocktowerNightResolutionServiceTests test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 3**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night be/src/test/java/top/egon/mario/clocktower/game/night
git commit -m "feat(clocktower): add storyteller night task fallback"
```

### Task 4: Frontend Service And Type Contracts

**Files:**
- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Add failing endpoint tests**

Import the new functions in `clocktowerService.test.ts`, then add:

```ts
it('uses storyteller agent control endpoints', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerGameAgents(11)
    await pauseClocktowerAgent(11, 81)
    await resumeClocktowerAgent(11, 81)
    await runClocktowerAgentNow(11, 81)
    await getClocktowerAgentMemory(11, 81)
    await getClocktowerAgentTasks(11, 81)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/pause', {method: 'POST'})
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/resume', {method: 'POST'})
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/run-now', {method: 'POST'})
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/memory')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/tasks')
})

it('uses game night task endpoints', async () => {
    const {requestJson} = await import('../../services/request')
    const resolveRequest = {targetGameSeatIds: [31], payload: {}, note: 'override'}

    await getClocktowerNightTasks(11)
    await resolveClocktowerNightTask(11, 91, resolveRequest)
    await skipClocktowerGameNightTask(11, 91, {reason: 'manual skip'})
    await randomChoiceClocktowerNightTask(11, 91)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/resolve', {
        method: 'POST',
        body: resolveRequest,
    })
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/skip', {
        method: 'POST',
        body: {reason: 'manual skip'},
    })
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/random-choice', {method: 'POST'})
})
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts
```

Expected: FAIL because the new functions/types are missing.

- [ ] **Step 3: Add TypeScript types**

Add these to `clocktowerTypes.ts` after the mic types:

```ts
export type ClocktowerAgentConsoleView = {
    agentInstanceId: number
    actorId?: number | null
    gameSeatId?: number | null
    seatNo?: number | null
    displayName?: string | null
    profileName?: string | null
    status: string
    autoMode: string
    roleCode?: string | null
    alignment?: string | null
    recentTaskStatus?: string | null
    recentTaskTriggerType?: string | null
    recentTaskResult?: Record<string, unknown> | null
    recentError?: string | null
}

export type ClocktowerAgentTaskView = {
    taskId: number
    gameId: number
    agentInstanceId: number
    gameSeatId: number
    triggerType: string
    triggerKey: string
    status: string
    priority: number
    availableAt: string
    lockedAt?: string | null
    lockedBy?: string | null
    attempts: number
    lastError?: string | null
    metadata: Record<string, unknown>
    result: Record<string, unknown>
}

export type ClocktowerAgentMemoryView = {
    memoryId: number
    gameId: number
    agentInstanceId: number
    gameSeatId: number
    sourceEventId?: number | null
    sourceEventSeq?: number | null
    memoryType: string
    visibility: string
    subjectGameSeatId?: number | null
    content: Record<string, unknown>
    confidence: number
    dayNo: number
    nightNo: number
    metadata: Record<string, unknown>
}

export type ClocktowerNightTaskView = {
    taskId: number
    gameId: number
    nightNo: number
    actorGameSeatId: number
    roleCode: string
    taskType: string
    status: string
    mandatory: boolean
    sortOrder: number
    choice: Record<string, unknown>
    result: Record<string, unknown>
    metadata: Record<string, unknown>
}

export type ClocktowerNightResolveRequest = {
    result?: Record<string, unknown> | null
    note?: string | null
    targetGameSeatIds?: number[] | null
    payload?: Record<string, unknown> | null
}

export type ClocktowerNightSkipRequest = {
    reason?: string | null
}
```

- [ ] **Step 4: Add service functions**

Import the new types and add:

```ts
export function getClocktowerGameAgents(gameId: number) {
    return requestJson<ClocktowerAgentConsoleView[]>(`/api/clocktower/games/${gameId}/agents`)
}

export function pauseClocktowerAgent(gameId: number, agentInstanceId: number) {
    return requestJson<ClocktowerAgentConsoleView>(`/api/clocktower/games/${gameId}/agents/${agentInstanceId}/pause`, {method: 'POST'})
}

export function resumeClocktowerAgent(gameId: number, agentInstanceId: number) {
    return requestJson<ClocktowerAgentConsoleView>(`/api/clocktower/games/${gameId}/agents/${agentInstanceId}/resume`, {method: 'POST'})
}

export function runClocktowerAgentNow(gameId: number, agentInstanceId: number) {
    return requestJson<ClocktowerAgentTaskView>(`/api/clocktower/games/${gameId}/agents/${agentInstanceId}/run-now`, {method: 'POST'})
}

export function getClocktowerAgentMemory(gameId: number, agentInstanceId: number) {
    return requestJson<ClocktowerAgentMemoryView[]>(`/api/clocktower/games/${gameId}/agents/${agentInstanceId}/memory`)
}

export function getClocktowerAgentTasks(gameId: number, agentInstanceId: number) {
    return requestJson<ClocktowerAgentTaskView[]>(`/api/clocktower/games/${gameId}/agents/${agentInstanceId}/tasks`)
}

export function getClocktowerNightTasks(gameId: number) {
    return requestJson<ClocktowerNightTaskView[]>(`/api/clocktower/games/${gameId}/night-tasks`)
}

export function resolveClocktowerNightTask(gameId: number, taskId: number, request?: ClocktowerNightResolveRequest) {
    return requestJson<ClocktowerNightTaskView>(`/api/clocktower/games/${gameId}/night-tasks/${taskId}/resolve`, {
        method: 'POST',
        body: request,
    })
}

export function skipClocktowerGameNightTask(gameId: number, taskId: number, request?: ClocktowerNightSkipRequest) {
    return requestJson<ClocktowerNightTaskView>(`/api/clocktower/games/${gameId}/night-tasks/${taskId}/skip`, {
        method: 'POST',
        body: request,
    })
}

export function randomChoiceClocktowerNightTask(gameId: number, taskId: number) {
    return requestJson<ClocktowerNightTaskView>(`/api/clocktower/games/${gameId}/night-tasks/${taskId}/random-choice`, {method: 'POST'})
}
```

- [ ] **Step 5: Run service tests**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts fe/src/modules/clocktower/clocktowerService.ts fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add storyteller console service contracts"
```

### Task 5: Frontend Storyteller Panels

**Files:**
- Create: `fe/src/modules/clocktower/components/StorytellerAgentPanel.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerAgentPanel.test.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerMicControlPanel.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerNightTaskPanel.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerNightTaskPanel.test.tsx`

- [ ] **Step 1: Write panel rendering tests**

Use `renderToStaticMarkup` for static coverage and mocked service functions for action coverage. Each test should use explicit mock rows:

```ts
const agents: ClocktowerAgentConsoleView[] = [{
    agentInstanceId: 81,
    actorId: 71,
    gameSeatId: 31,
    seatNo: 2,
    displayName: 'Agent Bob',
    profileName: 'Default Agent',
    status: 'ACTIVE',
    autoMode: 'PAUSED',
    roleCode: 'IMP',
    alignment: 'EVIL',
    recentTaskStatus: 'PENDING',
    recentTaskTriggerType: 'ST_RUN_NOW',
    recentTaskResult: {},
    recentError: null,
}]
```

Expected Agent panel markup contains `Agent Bob`, `PAUSED`, `IMP`, `查看记忆`, and `立即运行`.

Expected mic panel markup contains session status, current holder, `跳过当前`, `关闭公聊`, and `延长 2 分钟`.

Expected night panel markup contains `POISONER`, `CHOOSE_TARGET`, `确认`, `跳过`, `随机`, and `手动选目标`.

- [ ] **Step 2: Run panel tests to verify failure**

Run:

```bash
cd fe && bun run test -- StorytellerAgentPanel.test.tsx StorytellerMicControlPanel.test.tsx StorytellerNightTaskPanel.test.tsx
```

Expected: FAIL because the components do not exist.

- [ ] **Step 3: Implement StorytellerAgentPanel**

Build a `Card` with a vertical `List`, status `Tag`s, and action `Button`s. Load rows using `getClocktowerGameAgents(gameId)` in `useEffect`. On pause/resume/run-now, call the matching service, show `message.success(...)`, and refresh rows. Use `Drawer` to show `getClocktowerAgentMemory` and `getClocktowerAgentTasks` for selected Agent.

Keep JSON summaries compact:

```ts
function compactJson(value?: Record<string, unknown> | null) {
    if (!value || Object.keys(value).length === 0) {
        return '-'
    }
    return JSON.stringify(value)
}
```

- [ ] **Step 4: Implement StorytellerMicControlPanel**

Build a `Card` that loads `getClocktowerMicSession(gameId)`. Render session status, holder, countdowns using helpers from `PublicMicPanel` if exported, or duplicate only tiny formatting helpers locally. Actions call:

- `startClocktowerDayMic(gameId)`
- `skipClocktowerMicTurn(gameId, session.currentTurnId)`
- `extendClocktowerMicSession(gameId, 120)`
- `closeClocktowerMicSession(gameId)`

Disable actions when required session ids are absent.

- [ ] **Step 5: Implement StorytellerNightTaskPanel**

Build a `Card` with a `List` of night tasks loaded from `getClocktowerNightTasks(gameId)`. Enrich rows from `seats` prop by `task.actorGameSeatId === seat.gameSeatId`. Add buttons for resolve, skip, random, and manual target. Manual target uses an Ant Design `Modal` plus `Select` with grimoire seat options, then calls:

```ts
resolveClocktowerNightTask(gameId, task.taskId, {
    targetGameSeatIds: selectedTargetIds,
    payload: {},
    note: 'ST override',
})
```

- [ ] **Step 6: Run panel tests**

Run:

```bash
cd fe && bun run test -- StorytellerAgentPanel.test.tsx StorytellerMicControlPanel.test.tsx StorytellerNightTaskPanel.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

```bash
git add fe/src/modules/clocktower/components/StorytellerAgentPanel.tsx fe/src/modules/clocktower/components/StorytellerAgentPanel.test.tsx fe/src/modules/clocktower/components/StorytellerMicControlPanel.tsx fe/src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx fe/src/modules/clocktower/components/StorytellerNightTaskPanel.tsx fe/src/modules/clocktower/components/StorytellerNightTaskPanel.test.tsx
git commit -m "feat(clocktower): add storyteller console panels"
```

### Task 6: Wire Panels Into StorytellerGameSurface

**Files:**
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

- [ ] **Step 1: Add failing surface tab test**

Update the `clocktowerService` mock in `StorytellerGrimoirePage.test.tsx` to include all new service functions as `vi.fn()`. Add:

```ts
test('renders storyteller game surface console tabs', () => {
    const markup = renderToStaticMarkup(
        <StorytellerGameSurface
            roomName="Friday"
            view={storytellerGameView()}
        />,
    )

    expect(markup).toContain('Agent')
    expect(markup).toContain('麦序')
    expect(markup).toContain('夜晚任务')
    expect(markup).toContain('聊天监控')
})
```

Add a local `storytellerGameView()` helper returning a `ClocktowerGameViewResponse` with `viewerMode: 'STORYTELLER'`, one grimoire seat, empty events, and empty conversations.

- [ ] **Step 2: Run surface test to verify failure**

Run:

```bash
cd fe && bun run test -- StorytellerGrimoirePage.test.tsx
```

Expected: FAIL because new tab labels are not present.

- [ ] **Step 3: Wire panel imports and tabs**

Import the panels:

```ts
import {StorytellerAgentPanel} from './components/StorytellerAgentPanel'
import {StorytellerMicControlPanel} from './components/StorytellerMicControlPanel'
import {StorytellerNightTaskPanel} from './components/StorytellerNightTaskPanel'
```

Add these items before existing `flow` tab:

```tsx
{
    key: 'agents',
    label: 'Agent',
    children: <StorytellerAgentPanel gameId={view.gameId}/>,
},
{
    key: 'mic',
    label: '麦序',
    children: <StorytellerMicControlPanel gameId={view.gameId}/>,
},
{
    key: 'night-tasks',
    label: '夜晚任务',
    children: <StorytellerNightTaskPanel gameId={view.gameId} seats={view.grimoire}/>,
},
```

- [ ] **Step 4: Run surface test**

Run:

```bash
cd fe && bun run test -- StorytellerGrimoirePage.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

```bash
git add fe/src/modules/clocktower/StorytellerGrimoirePage.tsx fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx
git commit -m "feat(clocktower): wire storyteller control console"
```

### Task 7: Focused Verification And Cleanup

**Files:**
- Inspect all changed files.

- [ ] **Step 1: Run focused backend verification**

Run:

```bash
./mvnw -pl be -Dtest=top.egon.mario.clocktower.agent.control.ClocktowerAgentControlServiceTests,top.egon.mario.clocktower.game.mic.ClocktowerPublicMicServiceTests,top.egon.mario.clocktower.game.night.ClocktowerGameNightTaskServiceTests,top.egon.mario.clocktower.game.night.ClocktowerNightResolutionServiceTests test
```

Expected: PASS.

- [ ] **Step 2: Run focused frontend tests**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts StorytellerAgentPanel.test.tsx StorytellerMicControlPanel.test.tsx StorytellerNightTaskPanel.test.tsx StorytellerGrimoirePage.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Run frontend typecheck**

Run:

```bash
cd fe && bun run typecheck
```

Expected: PASS.

- [ ] **Step 4: Inspect git diff for unrelated changes**

Run:

```bash
git status --short
git diff --stat HEAD~6..HEAD
```

Expected: only task-14 backend, frontend, test, and plan files are changed across the new commits.

- [ ] **Step 5: Commit any verification cleanup**

If verification required small cleanup, commit it:

```bash
git add <changed-files>
git commit -m "test(clocktower): verify storyteller control console"
```

If no cleanup is needed, do not create an empty commit.

## Self-Review

- Spec coverage: Agent list/pause/resume/run-now/memory/tasks are covered in Task 1; mic skip/extend/close override events are covered in Task 2; night resolve/skip/random/manual target fallback is covered in Task 3; frontend services and panels are covered in Tasks 4-6; verification is covered in Task 7.
- Scope: no Flyway migration, no LLM logic, no private chat control, and no browser/runtime launch.
- Type consistency: frontend service names use `ClocktowerAgentConsoleView`, `ClocktowerAgentTaskView`, `ClocktowerAgentMemoryView`, `ClocktowerNightTaskView`, `ClocktowerNightResolveRequest`, and `ClocktowerNightSkipRequest`; backend DTO names match those service contracts.
- Risk: the largest implementation risk is night-task target override because it must reuse RoleSkill validation without bypassing existing resolution effects. Task 3 explicitly tests an IMP kill override to catch that path.
