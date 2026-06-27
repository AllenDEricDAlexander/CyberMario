package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "im_dm_pair")
public class ImDmPairPo extends BaseAuditablePo {

    @Column(name = "user_lo_id", nullable = false)
    private Long userLoId;

    @Column(name = "user_hi_id", nullable = false)
    private Long userHiId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "frozen", nullable = false)
    private Boolean frozen = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
