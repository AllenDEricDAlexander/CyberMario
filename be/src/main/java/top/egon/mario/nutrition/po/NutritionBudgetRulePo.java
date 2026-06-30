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
@Table(name = "nutrition_budget_rule")
public class NutritionBudgetRulePo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "period_type", nullable = false, length = 32)
    private String periodType;

    @Column(name = "amount_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountLimit;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency = "CNY";

    @Column(name = "warning_threshold", precision = 8, scale = 4)
    private BigDecimal warningThreshold;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
