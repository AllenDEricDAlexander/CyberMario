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
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_conversation_member")
public class ImConversationMemberPo extends BaseAuditablePo {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_seq", nullable = false)
    private Long lastReadSeq = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 32)
    private ImDeliveryMode deliveryMode = ImDeliveryMode.INBOX;

    @Column(name = "muted", nullable = false)
    private Boolean muted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImMembershipStatus status = ImMembershipStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Transient
    private ImMembershipRole memberRole = ImMembershipRole.MEMBER;

    @Transient
    private Instant joinedAt;

    @Transient
    private Instant lastActiveAt;

    public Long getLastReadMessageSeq() {
        return lastReadSeq;
    }

    public void setLastReadMessageSeq(Long lastReadMessageSeq) {
        this.lastReadSeq = lastReadMessageSeq;
    }

    public void setStatus(String status) {
        this.status = status == null ? null : ImMembershipStatus.valueOf(status);
    }

    public void setStatus(ImMembershipStatus status) {
        this.status = status;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole == null ? null : ImMembershipRole.valueOf(memberRole);
    }

    public void setMemberRole(ImMembershipRole memberRole) {
        this.memberRole = memberRole;
    }
}
