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
import top.egon.mario.im.po.enums.ImGovernanceStatus;

@Getter
@Setter
@Entity
@Table(name = "im_dm_block")
public class ImDmBlockPo extends BaseAuditablePo {

    @Column(name = "blocker_user_id", nullable = false)
    private Long blockerUserId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImGovernanceStatus status = ImGovernanceStatus.ACTIVE;

    @Column(name = "reason", length = 512)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
