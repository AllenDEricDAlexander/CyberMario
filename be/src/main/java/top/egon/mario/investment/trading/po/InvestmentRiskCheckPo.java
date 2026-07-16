package top.egon.mario.investment.trading.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_risk_check", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_risk_check_rule", columnNames = {"intent_id", "rule_code"})
})
public class InvestmentRiskCheckPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "intent_id", nullable = false, updatable = false)
    private Long intentId;
    @Column(name = "rule_code", nullable = false, length = 128, updatable = false)
    private String ruleCode;
    @Column(name = "passed", nullable = false, updatable = false)
    private boolean passed;
    @Column(name = "observed_value", precision = 38, scale = 18, updatable = false)
    private BigDecimal observedValue;
    @Column(name = "limit_value", precision = 38, scale = 18, updatable = false)
    private BigDecimal limitValue;
    @Column(name = "message", length = 2000, updatable = false)
    private String message;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String detailsJson = "{}";
    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
