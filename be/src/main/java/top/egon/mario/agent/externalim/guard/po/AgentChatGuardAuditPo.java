package top.egon.mario.agent.externalim.guard.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_chat_guard_audit")
public class AgentChatGuardAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_source", nullable = false, length = 32)
    private ChatSource chatSource;

    @Column(name = "memory_space_id", length = 96)
    private String memorySpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 32)
    private ExternalChatPlatform platform;

    @Column(name = "connector_id", length = 96)
    private String connectorId;

    @Column(name = "conversation_id", length = 192)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", length = 32)
    private ExternalConversationType conversationType;

    @Column(name = "audience_key", length = 256)
    private String audienceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private ChatGuardDecision decision;

    @Column(name = "confidence", precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "model_provider", length = 32)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "external_event_id", length = 192)
    private String externalEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
