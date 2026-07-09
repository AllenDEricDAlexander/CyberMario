package top.egon.mario.clocktower.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentRuntime.ClocktowerAgentRuntimeResult;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.runtime.repository.ClocktowerAgentTaskRepository;
import top.egon.mario.clocktower.common.ClocktowerException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ClocktowerAgentTaskWorker {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final ClocktowerAgentTaskRepository taskRepository;
    private final ClocktowerAgentRuntime runtime;
    private final ClocktowerAgentWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final boolean postgresqlClaims;

    public ClocktowerAgentTaskWorker(ClocktowerAgentTaskRepository taskRepository,
                                     ClocktowerAgentRuntime runtime,
                                     ClocktowerAgentWorkerProperties properties,
                                     ObjectMapper objectMapper,
                                     DataSourceProperties dataSourceProperties) {
        this.taskRepository = taskRepository;
        this.runtime = runtime;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.postgresqlClaims = postgresqlClaims(dataSourceProperties);
    }

    @Transactional
    public int processBatch(String workerId, int limit) {
        if (limit <= 0) {
            return 0;
        }
        Instant now = Instant.now();
        if (postgresqlClaims) {
            return processBatch(workerId, limit, now);
        }
        // H2 does not model PostgreSQL SKIP LOCKED behavior; keep same-JVM fallback claims serialized.
        synchronized (this) {
            return processBatch(workerId, limit, now);
        }
    }

    private int processBatch(String workerId, int limit, Instant now) {
        List<ClocktowerAgentTaskPo> tasks = claimPendingRows(limit, now);
        for (ClocktowerAgentTaskPo task : tasks) {
            processTask(task, workerId, now);
        }
        taskRepository.flush();
        return tasks.size();
    }

    private List<ClocktowerAgentTaskPo> claimPendingRows(int limit, Instant now) {
        if (postgresqlClaims) {
            return taskRepository.claimPendingForWorkerPostgreSql(now, limit);
        }
        return taskRepository.claimPendingForWorker(ClocktowerAgentTaskStatus.PENDING, now,
                PageRequest.of(0, limit));
    }

    private void processTask(ClocktowerAgentTaskPo task, String workerId, Instant now) {
        task.setStatus(ClocktowerAgentTaskStatus.RUNNING);
        task.setLockedAt(now);
        task.setLockedBy(workerId);
        taskRepository.save(task);
        try {
            ClocktowerAgentRuntimeResult result = runtime.handle(task);
            task.setStatus(result.status());
            task.setResultJson(writeJson(result.result()));
            task.setLastError(null);
            taskRepository.save(task);
        } catch (RuntimeException ex) {
            recordFailure(task, now, ex);
        }
    }

    private void recordFailure(ClocktowerAgentTaskPo task, Instant now, RuntimeException ex) {
        int attempts = task.getAttempts() + 1;
        task.setAttempts(attempts);
        task.setLastError(lastError(ex));
        task.setResultJson(writeJson(Map.of("failed", true, "error", task.getLastError())));
        if (attempts >= properties.getWorker().maxAttempts()) {
            task.setStatus(ClocktowerAgentTaskStatus.FAILED);
        } else {
            task.setStatus(ClocktowerAgentTaskStatus.PENDING);
            task.setAvailableAt(now.plus(properties.getWorker().retryDelay()));
        }
        taskRepository.save(task);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_TASK_JSON_INVALID");
        }
    }

    private String lastError(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private boolean postgresqlClaims(DataSourceProperties dataSourceProperties) {
        String url = dataSourceProperties == null ? null : dataSourceProperties.getUrl();
        return StringUtils.hasText(url) && url.startsWith("jdbc:postgresql:");
    }
}
