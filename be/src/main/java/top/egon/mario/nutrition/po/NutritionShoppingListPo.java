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
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_shopping_list")
public class NutritionShoppingListPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "meal_plan_id")
    private Long mealPlanId;

    @Column(name = "list_date", nullable = false)
    private LocalDate listDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionShoppingListStatus status = NutritionShoppingListStatus.DRAFT;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generated_snapshot", nullable = false, columnDefinition = "jsonb")
    private String generatedSnapshot = "{}";

    @Column(name = "estimated_total_price", precision = 14, scale = 2)
    private BigDecimal estimatedTotalPrice;

    @Column(name = "actual_total_price", precision = 14, scale = 2)
    private BigDecimal actualTotalPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
