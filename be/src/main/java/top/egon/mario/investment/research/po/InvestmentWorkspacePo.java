package top.egon.mario.investment.research.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

/**
 * Private Investment workspace owned by exactly one user.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_workspace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_workspace_owner_name", columnNames = {"owner_user_id", "name"})
})
public class InvestmentWorkspacePo extends BaseAuditablePo {

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 32)
    private String baseCurrency = "USDT";

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_json", nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";
}
