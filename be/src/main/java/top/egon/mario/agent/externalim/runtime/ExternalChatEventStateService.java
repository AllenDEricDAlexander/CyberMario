package top.egon.mario.agent.externalim.runtime;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ExternalChatEventStateService {

    private static final int ERROR_MESSAGE_MAX_CHARS = 1000;

    private final ExternalChatEventRepository repository;

    public ExternalChatEventStateService(ExternalChatEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean claim(Long eventId, String workerId) {
        if (eventId == null || !StringUtils.hasText(workerId)) {
            return false;
        }
        Instant now = Instant.now();
        return repository.claimReady(eventId, workerId, now,
                ExternalChatProcessingStatus.RECEIVED,
                ExternalChatProcessingStatus.RUNNING) == 1;
    }

    @Transactional
    public int recoverStale(Instant lockedBefore, int limit, int maxAttempts) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeMaxAttempts = Math.max(1, maxAttempts);
        List<ExternalChatEventPo> stale = repository
                .findByProcessingStatusAndLockedAtLessThanOrderByLockedAtAscIdAsc(
                        ExternalChatProcessingStatus.RUNNING, lockedBefore,
                        PageRequest.of(0, safeLimit));
        Instant now = Instant.now();
        for (ExternalChatEventPo event : stale) {
            event.setAttempts(event.getAttempts() + 1);
            event.setErrorCode("EXTERNAL_CHAT_EVENT_STALE");
            event.setErrorMessage("stale external chat event claim recovered");
            event.setUpdatedAt(now);
            if (event.getAttempts() >= safeMaxAttempts) {
                event.setProcessingStatus(ExternalChatProcessingStatus.FAILED);
                event.setReplyStatus(ExternalChatReplyStatus.FAILED);
                event.setProcessedAt(now);
            } else {
                event.setProcessingStatus(ExternalChatProcessingStatus.RECEIVED);
                event.setReplyStatus(event.getAssistantMessageId() == null
                        ? ExternalChatReplyStatus.NOT_REQUIRED
                        : ExternalChatReplyStatus.RETRY_PENDING);
                event.setAvailableAt(now);
            }
            clearClaim(event);
            repository.save(event);
        }
        return stale.size();
    }

    @Transactional
    public void markCandidate(Long eventId, String workerId, Long assistantMessageId) {
        ExternalChatEventPo event = requireClaim(eventId, workerId);
        event.setAssistantMessageId(assistantMessageId);
        event.setReplyStatus(ExternalChatReplyStatus.PENDING);
        event.setUpdatedAt(Instant.now());
        repository.save(event);
    }

    @Transactional
    public void markIgnored(Long eventId, String workerId) {
        ExternalChatEventPo event = requireClaim(eventId, workerId);
        Instant now = Instant.now();
        event.setProcessingStatus(ExternalChatProcessingStatus.IGNORED);
        event.setReplyStatus(ExternalChatReplyStatus.NOT_REQUIRED);
        event.setProcessedAt(now);
        event.setUpdatedAt(now);
        clearClaim(event);
        repository.save(event);
    }

    @Transactional
    public void markSent(Long eventId, String workerId, String platformMessageId) {
        ExternalChatEventPo event = requireClaim(eventId, workerId);
        Instant now = Instant.now();
        event.setProcessingStatus(ExternalChatProcessingStatus.SUCCEEDED);
        event.setReplyStatus(ExternalChatReplyStatus.SENT);
        event.setMetadataJson(platformMessageMetadata(platformMessageId));
        event.setProcessedAt(now);
        event.setUpdatedAt(now);
        clearClaim(event);
        repository.save(event);
    }

    @Transactional
    public void retryReply(Long eventId, String workerId, String code, String message,
                           Instant availableAt, int maxAttempts) {
        ExternalChatEventPo event = requireClaim(eventId, workerId);
        Instant now = Instant.now();
        event.setAttempts(event.getAttempts() + 1);
        event.setErrorCode(code);
        event.setErrorMessage(truncate(message));
        event.setUpdatedAt(now);
        if (event.getAttempts() >= Math.max(1, maxAttempts)) {
            event.setProcessingStatus(ExternalChatProcessingStatus.FAILED);
            event.setReplyStatus(ExternalChatReplyStatus.FAILED);
            event.setProcessedAt(now);
        } else {
            event.setProcessingStatus(ExternalChatProcessingStatus.RECEIVED);
            event.setReplyStatus(ExternalChatReplyStatus.RETRY_PENDING);
            event.setAvailableAt(availableAt == null ? now : availableAt);
        }
        clearClaim(event);
        repository.save(event);
    }

    @Transactional
    public void fail(Long eventId, String workerId, String code, String message) {
        ExternalChatEventPo event = requireClaim(eventId, workerId);
        Instant now = Instant.now();
        event.setProcessingStatus(ExternalChatProcessingStatus.FAILED);
        event.setReplyStatus(ExternalChatReplyStatus.FAILED);
        event.setErrorCode(code);
        event.setErrorMessage(truncate(message));
        event.setProcessedAt(now);
        event.setUpdatedAt(now);
        clearClaim(event);
        repository.save(event);
    }

    private ExternalChatEventPo requireClaim(Long eventId, String workerId) {
        return repository.findById(eventId)
                .filter(event -> event.getProcessingStatus() == ExternalChatProcessingStatus.RUNNING)
                .filter(event -> workerId != null && workerId.equals(event.getLockedBy()))
                .orElseThrow(() -> new ExternalChatException("EXTERNAL_CHAT_EVENT_CLAIM_LOST",
                        "external chat event claim is no longer valid"));
    }

    private void clearClaim(ExternalChatEventPo event) {
        event.setLockedAt(null);
        event.setLockedBy(null);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_MAX_CHARS) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_MAX_CHARS);
    }

    private String platformMessageMetadata(String platformMessageId) {
        if (!StringUtils.hasText(platformMessageId)) {
            return null;
        }
        String escaped = platformMessageId.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"platformMessageId\":\"" + escaped + "\"}";
    }
}
