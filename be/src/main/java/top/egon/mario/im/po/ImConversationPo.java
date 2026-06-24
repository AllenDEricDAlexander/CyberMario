package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_conversation")
public class ImConversationPo extends BaseAuditablePo {

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "context_type", nullable = false, length = 64)
    private String contextType;

    @Column(name = "context_id", nullable = false)
    private Long contextId;

    @Column(name = "scope_type", nullable = false, length = 64)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(name = "participant_key", nullable = false, length = 256)
    private String participantKey;

    @Column(name = "conversation_type", nullable = false, length = 32)
    private String conversationType;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq = 0L;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
