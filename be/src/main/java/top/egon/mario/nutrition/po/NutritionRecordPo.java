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
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_record")
public class NutritionRecordPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "meal_plan_id")
    private Long mealPlanId;

    @Column(name = "meal_confirmation_id")
    private Long mealConfirmationId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private NutritionMealType mealType;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "calories", precision = 12, scale = 3)
    private BigDecimal calories;

    @Column(name = "protein", precision = 12, scale = 3)
    private BigDecimal protein;

    @Column(name = "fat", precision = 12, scale = 3)
    private BigDecimal fat;

    @Column(name = "carbs", precision = 12, scale = 3)
    private BigDecimal carbs;

    @Column(name = "sugar", precision = 12, scale = 3)
    private BigDecimal sugar;

    @Column(name = "sodium", precision = 12, scale = 3)
    private BigDecimal sodium;

    @Column(name = "fiber", precision = 12, scale = 3)
    private BigDecimal fiber;

    @Column(name = "cholesterol", precision = 12, scale = 3)
    private BigDecimal cholesterol;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_tags", nullable = false, columnDefinition = "jsonb")
    private String riskTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "jsonb")
    private String calculationSnapshot = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
