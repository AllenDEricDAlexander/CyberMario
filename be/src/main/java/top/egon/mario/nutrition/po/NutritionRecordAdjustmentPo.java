package top.egon.mario.nutrition.po;

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
@Table(name = "nutrition_record_adjustment")
public class NutritionRecordAdjustmentPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "nutrition_record_id", nullable = false)
    private Long nutritionRecordId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "adjusted_by_user_id", nullable = false)
    private Long adjustedByUserId;

    @Column(name = "adjustment_type", nullable = false, length = 64)
    private String adjustmentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", nullable = false, columnDefinition = "jsonb")
    private String beforeSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", nullable = false, columnDefinition = "jsonb")
    private String afterSnapshot = "{}";

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "adjusted_at", nullable = false)
    private Instant adjustedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
