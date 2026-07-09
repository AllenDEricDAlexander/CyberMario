# Clocktower Agent Task Queue Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the task-10 asynchronous Agent task queue skeleton so game events schedule Agent work after commit and a worker eventually executes default runtime actions through existing game action services.

**Architecture:** Publish a game-event signal from `ClocktowerGameEventAppender`, schedule idempotent `clocktower_agent_task` rows in an AFTER_COMMIT listener, then claim/process pending rows with a worker patterned after the existing IM outbox dispatcher. Runtime behavior remains intentionally small: skip non-auto agents, pass mic turns, false-vote open nominations, and delegate night choices to `ClocktowerGameNightTaskService.autoChooseTask`.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, H2 PostgreSQL mode tests, JUnit 5, AssertJ, existing Clocktower game/action/night/mic/nomination services.

---

## File Structure

Create:

- `be/src/main/resources/db/migration/V37__clocktower_agent_task.sql`: queue table with audit fields, idempotency key, pending index, and lock columns.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskStatus.java`: string constants for queue statuses.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerType.java`: string constants for supported trigger types.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentWorkerProperties.java`: worker defaults.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskScheduler.java`: idempotent queue creation APIs.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java`: default task execution behavior.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskWorker.java`: claim/process/retry loop.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskWorkerRunner.java`: SmartLifecycle runner.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerListener.java`: AFTER_COMMIT event-to-task mapping.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerGameEventAppendedSignal.java`: saved game event signal.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/po/ClocktowerAgentTaskPo.java`: JPA mapping.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/repository/ClocktowerAgentTaskRepository.java`: lookup and claim queries.
- `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`: scheduling, idempotency, worker, runtime behavior.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameEventAppender.java`: publish `ClocktowerGameEventAppendedSignal` after saving events.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerGameNightTaskServiceImpl.java`: include `taskId` and `actorGameSeatId` in `NIGHT_TASKS_CREATED` payload entries.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`: migration coverage.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`: JPA mapping coverage.

## Tasks

### Task 1: Persistence Red Test

- [ ] Add tests proving `V37__clocktower_agent_task.sql` exists, includes audit fields and unique trigger index, migrates under H2 PostgreSQL mode, and stores trigger metadata.
- [ ] Add JPA mapping coverage for `ClocktowerAgentTaskPo`.
- [ ] Run `./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests test` from `be/` and verify failure because the migration and PO do not exist yet.

### Task 2: Persistence Green

- [ ] Add `V37__clocktower_agent_task.sql` with exactly one new Flyway migration.
- [ ] Add status/trigger constants, PO, and repository with H2 pessimistic claim and PostgreSQL `for update skip locked` claim.
- [ ] Re-run the mapping/migration tests and keep failures scoped to missing runtime behavior if any.

### Task 3: Scheduler and Listener Red

- [ ] Add `ClocktowerAgentTaskRuntimeTests` for: game started schedules all agent instances, duplicate signal does not duplicate tasks, mic turn schedules only the current agent, nomination opens false-vote work for eligible agents, and night task creation schedules the owning agent.
- [ ] Run `./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentTaskRuntimeTests test` and verify failure on missing scheduler/listener classes.

### Task 4: Scheduler and Listener Green

- [ ] Publish `ClocktowerGameEventAppendedSignal` from `ClocktowerGameEventAppender`.
- [ ] Implement `ClocktowerAgentTaskScheduler` idempotent creation methods.
- [ ] Implement `ClocktowerAgentTriggerListener` with `@TransactionalEventListener(AFTER_COMMIT)` mappings for `GAME_STARTED`, `PHASE_CHANGED`, `MIC_TURN_STARTED`, `NOMINATION_OPENED`, and `NIGHT_TASKS_CREATED`.
- [ ] Re-run `ClocktowerAgentTaskRuntimeTests`.

### Task 5: Worker and Runtime Red

- [ ] Extend runtime tests for worker claiming pending tasks, marking DONE, PAUSED/ST_APPROVAL skip, mic PASS execution, night auto choice, and retry/last_error on failure.
- [ ] Run `ClocktowerAgentTaskRuntimeTests` and verify failure on missing worker/runtime methods.

### Task 6: Worker and Runtime Green

- [ ] Implement `ClocktowerAgentRuntime` default behavior via existing `ClocktowerAgentGameActionService` and `ClocktowerGameNightTaskService`.
- [ ] Implement `ClocktowerAgentTaskWorker` with bounded retries, `last_error`, and `available_at` retry delay.
- [ ] Add `ClocktowerAgentTaskWorkerRunner` gated by `clocktower.agent.worker.enabled` and `clocktower.agent.worker.runner.enabled`.
- [ ] Re-run targeted runtime tests.

### Task 7: Final Verification and Commit

- [ ] Run targeted backend tests:
  `./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests,ClocktowerAgentTaskRuntimeTests,ClocktowerGameActionServiceTests test`
- [ ] Check `git diff --stat` and inspect scope.
- [ ] Commit all task-10 changes with message `feat(clocktower): add agent task queue runtime`.

## Scope Notes

- Do not implement Agent memory, LLM, complete heuristic policy, private view, or decision audit.
- Do not modify existing Flyway migrations.
- Use Observer/Event Listener and Domain Service only; avoid Strategy/Chain abstractions until later tasks add real policy variation.
