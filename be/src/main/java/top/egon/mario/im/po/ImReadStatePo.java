package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_read_state")
public class ImReadStatePo extends BaseAuditablePo {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "conversation_member_id", nullable = false)
    private Long conversationMemberId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_seq", nullable = false)
    private Long lastReadMessageSeq = 0L;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";
}
