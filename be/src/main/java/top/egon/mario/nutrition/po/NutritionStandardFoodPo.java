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

@Getter
@Setter
@Entity
@Table(name = "nutrition_standard_food")
public class NutritionStandardFoodPo extends BaseAuditablePo {

    @Column(name = "name_cn", nullable = false, length = 128)
    private String nameCn;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases", nullable = false, columnDefinition = "jsonb")
    private String aliases = "[]";

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "external_source", length = 64)
    private String externalSource;

    @Column(name = "external_food_id", length = 128)
    private String externalFoodId;

    @Column(name = "calories_per_100g", precision = 12, scale = 3)
    private BigDecimal caloriesPer100g;

    @Column(name = "protein_per_100g", precision = 12, scale = 3)
    private BigDecimal proteinPer100g;

    @Column(name = "fat_per_100g", precision = 12, scale = 3)
    private BigDecimal fatPer100g;

    @Column(name = "carbs_per_100g", precision = 12, scale = 3)
    private BigDecimal carbsPer100g;

    @Column(name = "sugar_per_100g", precision = 12, scale = 3)
    private BigDecimal sugarPer100g;

    @Column(name = "sodium_per_100g", precision = 12, scale = 3)
    private BigDecimal sodiumPer100g;

    @Column(name = "fiber_per_100g", precision = 12, scale = 3)
    private BigDecimal fiberPer100g;

    @Column(name = "cholesterol_per_100g", precision = 12, scale = 3)
    private BigDecimal cholesterolPer100g;

    @Column(name = "purine_level", length = 32)
    private String purineLevel;

    @Column(name = "gi_value", precision = 8, scale = 3)
    private BigDecimal giValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergen_tags", nullable = false, columnDefinition = "jsonb")
    private String allergenTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suitable_tags", nullable = false, columnDefinition = "jsonb")
    private String suitableTags = "[]";

    @Column(name = "data_quality", nullable = false, length = 32)
    private String dataQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
