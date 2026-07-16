package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "nutrition_family", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nutrition_family_owner_name", columnNames = {"owner_user_id", "name"})
})
public class NutritionFamilyPo extends BaseAuditablePo {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "owner_member_profile_id")
    private Long ownerMemberProfileId;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency = "CNY";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_meal_types", nullable = false, columnDefinition = "jsonb")
    private String defaultMealTypes = "[]";

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    @Column(name = "ai_generate_time")
    private LocalTime aiGenerateTime;

    @Column(name = "health_alert_enabled", nullable = false)
    private boolean healthAlertEnabled = true;

    @Column(name = "budget_enabled", nullable = false)
    private boolean budgetEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
