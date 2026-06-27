package top.egon.mario.im.po;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
public class ImReadStatePo extends BaseAuditablePo {

    private Long conversationId;

    private Long conversationMemberId;

    private Long userId;

    private Long lastReadMessageSeq = 0L;

    private Instant lastReadAt;

    private String status = "ACTIVE";
}
