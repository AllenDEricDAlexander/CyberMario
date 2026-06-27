package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_conversation")
public class ImConversationPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 32)
    private ImConversationType conversationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_surface_type", nullable = false, length = 32)
    private ImSurfaceType ownerSurfaceType;

    @Column(name = "owner_surface_id", nullable = false)
    private Long ownerSurfaceId;

    @Column(name = "context_type", nullable = false, length = 64)
    private String contextType;

    @Column(name = "context_id")
    private Long contextId;

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq = 0L;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImConversationStatus status = ImConversationStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Transient
    private Long channelId;

    @Transient
    private Long groupId;

    @Transient
    private String scopeType;

    @Transient
    private Long scopeId;

    @Transient
    private String participantKey;

    public String getConversationType() {
        return conversationType == null ? null : conversationType.name();
    }

    public void setConversationType(String conversationType) {
        this.conversationType = conversationType == null ? null : ImConversationType.valueOf(conversationType);
    }

    public void setConversationType(ImConversationType conversationType) {
        this.conversationType = conversationType;
    }

    public ImConversationType getConversationTypeEnum() {
        return conversationType;
    }

    public void setStatus(String status) {
        this.status = status == null ? null : ImConversationStatus.valueOf(status);
    }

    public void setStatus(ImConversationStatus status) {
        this.status = status;
    }
}
