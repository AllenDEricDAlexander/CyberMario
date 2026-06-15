package top.egon.mario.agent.po;

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
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;

import java.time.Instant;

/**
 * Persisted message item for an audited agent conversation turn.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_conversation_message_audit")
public class AgentConversationMessageAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_audit_id", nullable = false)
    private Long conversationAuditId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private AgentConversationRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private AgentConversationMessageType messageType;

    @Column(name = "content")
    private String content;

    @Column(name = "content_chars")
    private Integer contentChars;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
