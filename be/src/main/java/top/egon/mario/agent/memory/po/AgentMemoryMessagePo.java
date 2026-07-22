package top.egon.mario.agent.memory.po;

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
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;

import java.time.Instant;

/**
 * Normalized persisted chat message for memory replay and audit.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_memory_message")
public class AgentMemoryMessagePo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "turn_no", nullable = false)
    private int turnNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private AgentMemoryMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private AgentMemoryMessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_chars")
    private Integer contentChars;

    @Column(name = "source_refs_json", columnDefinition = "TEXT")
    private String sourceRefsJson;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false, length = 32)
    private AgentMemoryMessageStatus messageStatus = AgentMemoryMessageStatus.SUCCEEDED;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_domain", nullable = false, length = 32)
    private AgentMemoryDomain memoryDomain = AgentMemoryDomain.WEB_PRIVATE;

    @Column(name = "memory_space_id", length = 96)
    private String memorySpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_platform", length = 32)
    private ExternalChatPlatform sourcePlatform;

    @Column(name = "source_connector_id", length = 96)
    private String sourceConnectorId;

    @Column(name = "source_conversation_id", length = 192)
    private String sourceConversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_conversation_type", length = 32)
    private ExternalConversationType sourceConversationType;

    @Column(name = "audience_key", length = 256)
    private String audienceKey;

    @Column(name = "external_event_id", length = 192)
    private String externalEventId;

    @Column(name = "external_message_id", length = 192)
    private String externalMessageId;

    @Column(name = "external_sender_id", length = 192)
    private String externalSenderId;

    @Column(name = "external_sender_display_name", length = 256)
    private String externalSenderDisplayName;

    @Column(name = "observed_only", nullable = false)
    private boolean observedOnly;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
