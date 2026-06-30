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
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_meal_plan")
public class NutritionMealPlanPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "ai_recommendation_id")
    private Long aiRecommendationId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionMealPlanStatus status = NutritionMealPlanStatus.DRAFT_AI;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "confirmation_cutoff_at")
    private Instant confirmationCutoffAt;

    @Column(name = "confirmed_member_count", nullable = false)
    private int confirmedMemberCount;

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", nullable = false, columnDefinition = "jsonb")
    private String nutritionSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
