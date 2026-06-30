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
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "nutrition_food_price_record")
public class NutritionFoodPriceRecordPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "shopping_list_item_id")
    private Long shoppingListItemId;

    @Column(name = "standard_food_id")
    private Long standardFoodId;

    @Column(name = "raw_food_name", nullable = false, length = 128)
    private String rawFoodName;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "channel", length = 128)
    private String channel;

    @Column(name = "brand", length = 128)
    private String brand;

    @Column(name = "spec_amount", precision = 14, scale = 3)
    private BigDecimal specAmount;

    @Column(name = "spec_unit", length = 32)
    private String specUnit;

    @Column(name = "purchase_quantity", precision = 14, scale = 3)
    private BigDecimal purchaseQuantity;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "normalized_unit_price", precision = 14, scale = 4)
    private BigDecimal normalizedUnitPrice;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "note", length = 512)
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
