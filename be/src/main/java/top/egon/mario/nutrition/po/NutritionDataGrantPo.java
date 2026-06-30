package top.egon.mario.nutrition.po;

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
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "nutrition_data_grant")
public class NutritionDataGrantPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "member_profile_id")
    private Long memberProfileId;

    @Column(name = "grantee_type", nullable = false, length = 32)
    private String granteeType;

    @Column(name = "grantee_id", nullable = false)
    private Long granteeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 64)
    private NutritionGrantDataScope dataScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false, length = 32)
    private NutritionGrantPermissionLevel permissionLevel = NutritionGrantPermissionLevel.READ;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
