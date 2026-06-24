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
@Table(name = "im_message")
public class ImMessagePo extends BaseAuditablePo {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_member_id")
    private Long senderMemberId;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq;

    @Column(name = "message_type", nullable = false, length = 32)
    private String messageType;

    @Column(name = "content", nullable = false)
    private String content = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "VISIBLE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
