package top.egon.mario.clocktower.agent.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClocktowerAgentTriggerListener {

    private final ClocktowerAgentTaskScheduler taskScheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameEvent(ClocktowerGameEventAppendedSignal signal) {
        if (signal == null || signal.gameId() == null || signal.eventType() == null) {
            return;
        }
        switch (signal.eventType()) {
            case "GAME_STARTED" -> scheduleGameStarted(signal);
            case "PHASE_CHANGED" -> schedulePhaseChanged(signal);
            case "MIC_TURN_STARTED" -> scheduleMicTurn(signal);
            case "NOMINATION_OPENED" -> scheduleVoteWindow(signal);
            case "NIGHT_TASKS_CREATED" -> scheduleNightTasks(signal);
            default -> {
            }
        }
    }

    private void scheduleGameStarted(ClocktowerGameEventAppendedSignal signal) {
        taskScheduler.scheduleForGameAgents(signal.gameId(), ClocktowerAgentTriggerType.GAME_STARTED,
                "game:%s:started".formatted(signal.gameId()), metadata(signal));
    }

    private void schedulePhaseChanged(ClocktowerGameEventAppendedSignal signal) {
        String phase = stringValue(signal.payload().getOrDefault("phase", signal.phase()));
        taskScheduler.scheduleForGameAgents(signal.gameId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
                "phase:%s:%s:%s".formatted(signal.gameId(), phase, signal.eventSeq()), metadata(signal));
    }

    private void scheduleMicTurn(ClocktowerGameEventAppendedSignal signal) {
        Long gameSeatId = longValue(signal.payload().get("gameSeatId"));
        Long turnId = longValue(signal.payload().get("turnId"));
        taskScheduler.scheduleForGameSeat(signal.gameId(), gameSeatId, ClocktowerAgentTriggerType.MIC_TURN_STARTED,
                "micTurn:%s".formatted(turnId == null ? signal.eventId() : turnId), metadata(signal));
    }

    private void scheduleVoteWindow(ClocktowerGameEventAppendedSignal signal) {
        Long nominationId = longValue(signal.payload().get("nominationId"));
        taskScheduler.scheduleForActiveAgentSeats(signal.gameId(), ClocktowerAgentTriggerType.VOTE_WINDOW_OPENED,
                "nomination:%s:vote".formatted(nominationId == null ? signal.eventId() : nominationId),
                metadata(signal));
    }

    private void scheduleNightTasks(ClocktowerGameEventAppendedSignal signal) {
        Object created = signal.payload().get("created");
        if (!(created instanceof List<?> createdTasks)) {
            return;
        }
        for (Object entry : createdTasks) {
            if (!(entry instanceof Map<?, ?> taskPayload)) {
                continue;
            }
            Long taskId = longValue(taskPayload.get("taskId"));
            Long actorGameSeatId = longValue(taskPayload.get("actorGameSeatId"));
            if (taskId == null || actorGameSeatId == null) {
                continue;
            }
            Map<String, Object> metadata = metadata(signal);
            metadata.put("taskId", taskId);
            metadata.put("actorGameSeatId", actorGameSeatId);
            metadata.put("roleCode", taskPayload.get("roleCode"));
            metadata.put("taskType", taskPayload.get("taskType"));
            taskScheduler.scheduleForGameSeat(signal.gameId(), actorGameSeatId,
                    ClocktowerAgentTriggerType.NIGHT_TASK_OPENED,
                    "nightTask:%s".formatted(taskId), metadata);
        }
    }

    private Map<String, Object> metadata(ClocktowerGameEventAppendedSignal signal) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("eventId", signal.eventId());
        metadata.put("eventSeq", signal.eventSeq());
        metadata.put("eventType", signal.eventType());
        metadata.put("phase", signal.phase());
        metadata.put("actorGameSeatId", signal.actorGameSeatId());
        metadata.put("targetGameSeatId", signal.targetGameSeatId());
        metadata.put("payload", signal.payload());
        if (signal.payload() != null) {
            metadata.putAll(signal.payload());
        }
        return metadata;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
