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
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_ai_recommendation")
public class NutritionAiRecommendationPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "ai_job_id", nullable = false)
    private Long aiJobId;

    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meal_types", nullable = false, columnDefinition = "jsonb")
    private String mealTypes = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private String inputSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", nullable = false, columnDefinition = "jsonb")
    private String outputSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_summary", nullable = false, columnDefinition = "jsonb")
    private String riskSummary = "{}";

    @Column(name = "cost_estimate", precision = 14, scale = 2)
    private BigDecimal costEstimate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
