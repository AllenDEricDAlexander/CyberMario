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
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "nutrition_recipe")
public class NutritionRecipePo extends BaseAuditablePo {

    @Column(name = "family_id")
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private NutritionRecipeSourceType sourceType;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(name = "serving_count", nullable = false)
    private int servingCount = 1;

    @Column(name = "cooking_minutes")
    private Integer cookingMinutes;

    @Column(name = "difficulty_level", length = 32)
    private String difficultyLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suitable_tags", nullable = false, columnDefinition = "jsonb")
    private String suitableTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergen_tags", nullable = false, columnDefinition = "jsonb")
    private String allergenTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", nullable = false, columnDefinition = "jsonb")
    private String nutritionSnapshot = "{}";

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
