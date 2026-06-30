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
@Table(name = "nutrition_shopping_list_item")
public class NutritionShoppingListItemPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "shopping_list_id", nullable = false)
    private Long shoppingListId;

    @Column(name = "standard_food_id")
    private Long standardFoodId;

    @Column(name = "raw_food_name", nullable = false, length = 128)
    private String rawFoodName;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "planned_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal plannedAmount;

    @Column(name = "planned_unit", nullable = false, length = 32)
    private String plannedUnit;

    @Column(name = "purchased_amount", precision = 14, scale = 3)
    private BigDecimal purchasedAmount;

    @Column(name = "purchased_unit", length = 32)
    private String purchasedUnit;

    @Column(name = "channel", length = 128)
    private String channel;

    @Column(name = "brand", length = 128)
    private String brand;

    @Column(name = "spec_amount", precision = 14, scale = 3)
    private BigDecimal specAmount;

    @Column(name = "spec_unit", length = 32)
    private String specUnit;

    @Column(name = "total_price", precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "normalized_unit_price", precision = 14, scale = 4)
    private BigDecimal normalizedUnitPrice;

    @Column(name = "item_status", nullable = false, length = 32)
    private String itemStatus;

    @Column(name = "note", length = 512)
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
