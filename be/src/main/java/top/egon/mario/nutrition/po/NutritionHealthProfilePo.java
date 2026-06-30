package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "nutrition_health_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nutrition_health_profile_member", columnNames = {"member_profile_id"})
})
public class NutritionHealthProfilePo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "activity_level", length = 32)
    private String activityLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diet_goals", nullable = false, columnDefinition = "jsonb")
    private String dietGoals = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergy_tags", nullable = false, columnDefinition = "jsonb")
    private String allergyTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dislike_tags", nullable = false, columnDefinition = "jsonb")
    private String dislikeTags = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restriction_tags", nullable = false, columnDefinition = "jsonb")
    private String restrictionTags = "[]";

    @Column(name = "target_calories", precision = 10, scale = 2)
    private BigDecimal targetCalories;

    @Column(name = "target_protein", precision = 10, scale = 2)
    private BigDecimal targetProtein;

    @Column(name = "target_fat", precision = 10, scale = 2)
    private BigDecimal targetFat;

    @Column(name = "target_carbs", precision = 10, scale = 2)
    private BigDecimal targetCarbs;

    @Column(name = "target_sodium", precision = 10, scale = 2)
    private BigDecimal targetSodium;

    @Column(name = "target_sugar", precision = 10, scale = 2)
    private BigDecimal targetSugar;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visibility_config", nullable = false, columnDefinition = "jsonb")
    private String visibilityConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
