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
import top.egon.mario.im.po.enums.ImMessageStatus;
import top.egon.mario.im.po.enums.ImMessageType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_message")
public class ImMessagePo extends BaseAuditablePo {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "message_seq", nullable = false)
    private Long messageSeq;

    @Column(name = "client_msg_id", length = 128)
    private String clientMsgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private ImMessageType messageType = ImMessageType.TEXT;

    @Column(name = "content", nullable = false)
    private String content = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImMessageStatus status = ImMessageStatus.VISIBLE;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Transient
    private Long senderMemberId;

    public String getMessageType() {
        return messageType == null ? null : messageType.name();
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType == null ? null : ImMessageType.valueOf(messageType);
    }

    public void setMessageType(ImMessageType messageType) {
        this.messageType = messageType;
    }

    public ImMessageType getMessageTypeEnum() {
        return messageType;
    }

    public void setStatus(String status) {
        this.status = status == null ? null : ImMessageStatus.valueOf(status);
    }

    public void setStatus(ImMessageStatus status) {
        this.status = status;
    }
}
