package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.po.enums.ImOutboxStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_outbox")
public class ImOutboxPo extends BaseAuditablePo {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private ImOutboxEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImOutboxStatus status = ImOutboxStatus.PENDING;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
