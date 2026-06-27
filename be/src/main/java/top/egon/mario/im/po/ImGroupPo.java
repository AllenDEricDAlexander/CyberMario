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
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImSurfaceStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_group")
public class ImGroupPo extends BaseAuditablePo {

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "context_type", nullable = false, length = 64)
    private String contextType;

    @Column(name = "context_id")
    private Long contextId;

    @Column(name = "group_key", nullable = false, length = 128)
    private String groupKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 32)
    private ImJoinPolicy joinPolicy = ImJoinPolicy.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImSurfaceStatus status = ImSurfaceStatus.ACTIVE;

    @Column(name = "announcement", nullable = false)
    private String announcement = "";

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount = 0;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    public void setStatus(String status) {
        this.status = status == null ? null : ImSurfaceStatus.valueOf(status);
    }

    public void setStatus(ImSurfaceStatus status) {
        this.status = status;
    }
}
