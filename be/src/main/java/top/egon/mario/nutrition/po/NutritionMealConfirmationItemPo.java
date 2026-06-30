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

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "nutrition_meal_confirmation_item")
public class NutritionMealConfirmationItemPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "confirmation_id", nullable = false)
    private Long confirmationId;

    @Column(name = "meal_plan_item_id", nullable = false)
    private Long mealPlanItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private NutritionMealType mealType;

    @Column(name = "selected", nullable = false)
    private boolean selected = true;

    @Column(name = "serving_count", nullable = false, precision = 12, scale = 3)
    private BigDecimal servingCount = BigDecimal.ONE;

    @Column(name = "risk_acknowledged", nullable = false)
    private boolean riskAcknowledged;

    @Column(name = "adjustment_note", length = 512)
    private String adjustmentNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
