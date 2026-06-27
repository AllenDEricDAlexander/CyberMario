package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.po.enums.ImOutboxStatus;
import top.egon.mario.im.repository.ImOutboxRepository;

import java.time.Instant;

@Service
public class OutboxService {

    private final ImOutboxRepository outboxRepository;

    public OutboxService(ImOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public ImOutboxPo createPending(Long conversationId, Long messageId, Long messageSeq,
                                    ImOutboxEventType eventType, Instant availableAt) {
        ImOutboxPo outbox = new ImOutboxPo();
        outbox.setConversationId(conversationId);
        outbox.setMessageId(messageId);
        outbox.setMessageSeq(messageSeq);
        outbox.setEventType(eventType);
        outbox.setStatus(ImOutboxStatus.PENDING);
        outbox.setAvailableAt(availableAt);
        outbox.setAttempts(0);
        outbox.setMetadataJson("{}");
        return outboxRepository.saveAndFlush(outbox);
    }
}
