package top.egon.mario.agent.externalim.runtime.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.ExternalChatBindingResolver;
import top.egon.mario.agent.externalim.memory.model.ResolvedExternalChatBinding;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.externalim.runtime.ExternalChatIngressService;
import top.egon.mario.agent.externalim.runtime.model.ExternalChatAcceptance;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultExternalChatIngressService implements ExternalChatIngressService {

    private static final int ERROR_MESSAGE_MAX_CHARS = 1000;

    private final ExternalChatEventRepository repository;
    private final ExternalChatBindingResolver bindingResolver;
    private final ObjectMapper objectMapper;

    public DefaultExternalChatIngressService(ExternalChatEventRepository repository,
                                             ExternalChatBindingResolver bindingResolver,
                                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.bindingResolver = bindingResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalChatAcceptance accept(ExternalChatMessage message, String traceId) {
        requireStableEvent(message);
        Optional<ExternalChatEventPo> duplicate = repository
                .findByPlatformAndConnectorIdAndExternalEventId(
                        message.platform(), message.connectorId(), message.eventId());
        if (duplicate.isPresent()) {
            ExternalChatEventPo existing = duplicate.orElseThrow();
            return acceptance(existing, true);
        }
        boolean humanText = humanText(message);
        ResolvedExternalChatBinding binding = null;
        ExternalChatException bindingError = null;
        if (humanText) {
            try {
                binding = bindingResolver.resolve(message);
            } catch (ExternalChatException error) {
                bindingError = error;
            }
        }
        ExternalChatEventPo event = event(message, traceId, humanText, binding, bindingError);
        try {
            return acceptance(repository.saveAndFlush(event), false);
        } catch (DataIntegrityViolationException race) {
            ExternalChatEventPo existing = repository
                    .findByPlatformAndConnectorIdAndExternalEventId(
                            message.platform(), message.connectorId(), message.eventId())
                    .orElseThrow(() -> race);
            return acceptance(existing, true);
        }
    }

    private ExternalChatEventPo event(ExternalChatMessage message, String traceId, boolean humanText,
                                      ResolvedExternalChatBinding binding,
                                      ExternalChatException bindingError) {
        Instant now = Instant.now();
        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setPlatform(message.platform());
        event.setConnectorId(message.connectorId());
        event.setExternalEventId(message.eventId());
        event.setExternalMessageId(message.messageId());
        event.setSpaceId(binding == null ? null : binding.memorySpaceId());
        event.setOwnerUserId(binding == null ? null : binding.ownerUserId());
        event.setNormalizedMessageJson(writeNormalized(message));
        boolean processable = humanText && binding != null;
        event.setProcessingStatus(processable
                ? ExternalChatProcessingStatus.RECEIVED
                : !humanText ? ExternalChatProcessingStatus.IGNORED : ExternalChatProcessingStatus.FAILED);
        event.setReplyStatus(ExternalChatReplyStatus.NOT_REQUIRED);
        event.setAvailableAt(now);
        event.setRequestId(UUID.randomUUID().toString());
        event.setTraceId(traceId);
        event.setReceivedAt(now);
        event.setErrorCode(bindingError == null ? null : bindingError.code());
        event.setErrorMessage(bindingError == null ? null : truncate(bindingError.getMessage()));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private ExternalChatAcceptance acceptance(ExternalChatEventPo event, boolean duplicate) {
        return new ExternalChatAcceptance(event.getId(), duplicate, event.getProcessingStatus());
    }

    private boolean humanText(ExternalChatMessage message) {
        return message.sender() != null
                && message.sender().type() == ExternalSenderType.HUMAN
                && message.messageType() == ExternalMessageType.TEXT
                && StringUtils.hasText(message.text());
    }

    private void requireStableEvent(ExternalChatMessage message) {
        if (message == null || message.platform() == null
                || !StringUtils.hasText(message.connectorId())
                || !StringUtils.hasText(message.eventId())) {
            throw new ExternalChatException("EXTERNAL_CHAT_EVENT_ID_REQUIRED",
                    "platform, connector and stable event id are required");
        }
    }

    private String writeNormalized(ExternalChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException error) {
            throw new ExternalChatException("EXTERNAL_CHAT_EVENT_JSON_INVALID",
                    "normalized external message cannot be serialized");
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_MAX_CHARS) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_MAX_CHARS);
    }
}
