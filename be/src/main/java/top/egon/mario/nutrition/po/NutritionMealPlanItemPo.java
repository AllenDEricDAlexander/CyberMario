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

@Getter
@Setter
@Entity
@Table(name = "nutrition_meal_plan_item")
public class NutritionMealPlanItemPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "meal_plan_id", nullable = false)
    private Long mealPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private NutritionMealType mealType;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "dish_name", nullable = false, length = 128)
    private String dishName;

    @Column(name = "serving_count", nullable = false, precision = 12, scale = 3)
    private BigDecimal servingCount = BigDecimal.ONE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", nullable = false, columnDefinition = "jsonb")
    private String nutritionSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_snapshot", nullable = false, columnDefinition = "jsonb")
    private String costSnapshot = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
