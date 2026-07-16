package top.egon.mario.im.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.enums.ImOutboxStatus;
import top.egon.mario.im.repository.ImOutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "im.realtime.dispatcher", name = "enabled", havingValue = "true")
public class OutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 512;

    private final ImOutboxRepository outboxRepository;
    private final RealtimeRouter realtimeRouter;
    private final boolean postgresqlClaims;
    private final int maxAttempts;
    private final Duration retryBaseDelay;

    public OutboxDispatcher(ImOutboxRepository outboxRepository, RealtimeRouter realtimeRouter,
                            DataSourceProperties dataSourceProperties,
                            @Value("${im.realtime.dispatcher.max-attempts:3}") int maxAttempts,
                            @Value("${im.realtime.dispatcher.retry-base-delay-ms:1000}") long retryBaseDelayMillis) {
        this.outboxRepository = outboxRepository;
        this.realtimeRouter = realtimeRouter;
        this.postgresqlClaims = postgresqlClaims(dataSourceProperties);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseDelay = Duration.ofMillis(Math.max(1L, retryBaseDelayMillis));
    }

    @Transactional
    public int dispatchBatch(int limit) {
        if (limit <= 0) {
            return 0;
        }
        Instant now = Instant.now();
        if (postgresqlClaims) {
            return dispatchBatch(limit, now);
        }
        // H2 does not model PostgreSQL SKIP LOCKED behavior; keep same-JVM fallback claims serialized.
        synchronized (this) {
            return dispatchBatch(limit, now);
        }
    }

    private int dispatchBatch(int limit, Instant now) {
        List<ImOutboxPo> rows = claimPendingRows(limit, now);
        int dispatched = 0;
        for (ImOutboxPo row : rows) {
            if (dispatch(row, now)) {
                dispatched++;
            }
        }
        outboxRepository.flush();
        return dispatched;
    }

    private List<ImOutboxPo> claimPendingRows(int limit, Instant now) {
        if (postgresqlClaims) {
            return outboxRepository.claimPendingForDispatchPostgreSql(now, limit);
        }
        return outboxRepository.claimPendingForDispatch(now, PageRequest.of(0, limit));
    }

    private boolean dispatch(ImOutboxPo row, Instant now) {
        try {
            realtimeRouter.deliverToConversation(row.getConversationId(), frame(row));
            row.setStatus(ImOutboxStatus.DISPATCHED);
            row.setLastError(null);
            outboxRepository.save(row);
            return true;
        } catch (RuntimeException ex) {
            recordFailure(row, now, ex);
            return false;
        }
    }

    private Map<String, Object> frame(ImOutboxPo row) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("outboxId", row.getId());
        frame.put("eventType", row.getEventType().name());
        frame.put("conversationId", row.getConversationId());
        frame.put("messageId", row.getMessageId());
        frame.put("messageSeq", row.getMessageSeq());
        return frame;
    }

    private void recordFailure(ImOutboxPo row, Instant now, RuntimeException ex) {
        int attempts = attempts(row) + 1;
        row.setAttempts(attempts);
        row.setLastError(lastError(ex));
        if (attempts >= maxAttempts) {
            row.setStatus(ImOutboxStatus.FAILED);
        } else {
            row.setStatus(ImOutboxStatus.PENDING);
            row.setAvailableAt(now.plus(retryDelay(attempts)));
        }
        outboxRepository.save(row);
    }

    private Duration retryDelay(int attempts) {
        return retryBaseDelay.multipliedBy(attempts);
    }

    private int attempts(ImOutboxPo row) {
        return row.getAttempts() == null ? 0 : row.getAttempts();
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
