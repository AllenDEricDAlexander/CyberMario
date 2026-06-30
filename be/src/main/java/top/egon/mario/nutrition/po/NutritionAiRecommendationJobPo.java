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
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_ai_recommendation_job")
public class NutritionAiRecommendationJobPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private NutritionAiTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionAiJobStatus status = NutritionAiJobStatus.PENDING;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_meal_types", nullable = false, columnDefinition = "jsonb")
    private String targetMealTypes = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private String inputSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", nullable = false, columnDefinition = "jsonb")
    private String outputSnapshot = "{}";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
