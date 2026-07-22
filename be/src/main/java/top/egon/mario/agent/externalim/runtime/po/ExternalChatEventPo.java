package top.egon.mario.agent.externalim.runtime.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_external_chat_event")
public class ExternalChatEventPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private ExternalChatPlatform platform;

    @Column(name = "connector_id", nullable = false, length = 96)
    private String connectorId;

    @Column(name = "external_event_id", nullable = false, length = 192)
    private String externalEventId;

    @Column(name = "external_message_id", length = 192)
    private String externalMessageId;

    @Column(name = "space_id", length = 96)
    private String spaceId;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "normalized_message_json", nullable = false, columnDefinition = "TEXT")
    private String normalizedMessageJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private ExternalChatProcessingStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "guard_decision", length = 32)
    private ChatGuardDecision guardDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_status", nullable = false, length = 32)
    private ExternalChatReplyStatus replyStatus;

    @Column(name = "assistant_message_id")
    private Long assistantMessageId;

    @Column(name = "reply_version", nullable = false)
    private int replyVersion = 1;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
