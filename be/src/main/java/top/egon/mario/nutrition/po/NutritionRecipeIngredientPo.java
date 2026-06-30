package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "nutrition_recipe_ingredient")
public class NutritionRecipeIngredientPo extends BaseAuditablePo {

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "standard_food_id")
    private Long standardFoodId;

    @Column(name = "raw_food_name", nullable = false, length = 128)
    private String rawFoodName;

    @Column(name = "amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal amount;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    @Column(name = "mapping_status", nullable = false, length = 32)
    private String mappingStatus;

    @Column(name = "optional", nullable = false)
    private boolean optional;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", nullable = false, columnDefinition = "jsonb")
    private String nutritionSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
