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
@Table(name = "nutrition_extra_food_record")
public class NutritionExtraFoodRecordPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private NutritionMealType mealType;

    @Column(name = "food_name", nullable = false, length = 128)
    private String foodName;

    @Column(name = "standard_food_id")
    private Long standardFoodId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal amount;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", nullable = false, columnDefinition = "jsonb")
    private String nutritionSnapshot = "{}";

    @Column(name = "note", length = 512)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
