package top.egon.mario.agent.model.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.po.ModelAuditPo;
import top.egon.mario.agent.model.repository.ModelAuditRepository;
import top.egon.mario.agent.model.service.ModelAuditService;
import top.egon.mario.agent.model.service.model.ModelAuditEvent;
import top.egon.mario.agent.model.service.model.ModelTokenUsage;
import top.egon.mario.common.utils.LogUtil;

import java.time.Instant;

/**
 * Persists model call audit records for usage reporting and troubleshooting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultModelAuditService implements ModelAuditService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;

    private final ModelAuditRepository modelAuditRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(ModelAuditEvent event) {
        ModelAuditPo audit = new ModelAuditPo();
        audit.setRequestId(event.requestId());
        audit.setTraceId(event.traceId());
        audit.setUserId(event.userId());
        audit.setSessionId(event.sessionId());
        audit.setThreadId(event.threadId());
        audit.setScenario(event.scenario());
        audit.setProvider(event.provider());
        audit.setModel(event.model());
        audit.setOptionsJson(optionsJson(event.options()));
        ModelTokenUsage usage = event.tokenUsage() == null ? ModelTokenUsage.unavailable() : event.tokenUsage();
        audit.setPromptTokens(usage.promptTokens());
        audit.setCompletionTokens(usage.completionTokens());
        audit.setTotalTokens(usage.totalTokens());
        audit.setTokenUsageSource(usage.source());
        audit.setStreaming(event.streaming());
        audit.setStatus(event.status());
        audit.setStartedAt(event.startedAt());
        audit.setFinishedAt(event.finishedAt());
        audit.setDurationMs(event.durationMs());
        audit.setErrorCode(event.errorCode());
        audit.setErrorMessage(truncate(event.errorMessage(), ERROR_MESSAGE_MAX_LENGTH));
        audit.setPromptChars(event.promptChars());
        audit.setCompletionChars(event.completionChars());
        audit.setIp(event.ip());
        audit.setUserAgent(event.userAgent());
        audit.setCreatedAt(Instant.now());
        modelAuditRepository.save(audit);
        LogUtil.debug(log).log("model audit saved, requestId={}, provider={}, model={}, status={}",
                event.requestId(), event.provider(), event.model(), event.status());
    }

    private String optionsJson(ModelOptions options) {
        if (options == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            LogUtil.warn(log).log("model options serialization failed", e);
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
