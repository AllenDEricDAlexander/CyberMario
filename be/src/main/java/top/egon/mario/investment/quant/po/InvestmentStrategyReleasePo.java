package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable audit snapshot of one deployed Java strategy version.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_strategy_release")
public class InvestmentStrategyReleasePo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_code", nullable = false, length = 128, updatable = false)
    private String strategyCode;

    @Column(name = "strategy_version", nullable = false, length = 64, updatable = false)
    private String strategyVersion;

    @Column(name = "display_name", nullable = false, length = 256, updatable = false)
    private String displayName;

    @Column(name = "description", nullable = false, length = 2000, updatable = false)
    private String description;

    @Column(name = "implementation_class", nullable = false, length = 512, updatable = false)
    private String implementationClass;

    @Column(name = "engine_type", nullable = false, length = 32, updatable = false)
    private String engineType;

    @Column(name = "build_revision", nullable = false, length = 128, updatable = false)
    private String buildRevision;

    @Column(name = "source_hash", nullable = false, length = 64, updatable = false)
    private String sourceHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_capabilities_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String requiredCapabilitiesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "descriptor_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String descriptorSnapshotJson;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;
}
