package top.egon.mario.investment.research.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Versioned, owner-scoped research artifact whose market cutoff never changes after creation.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_research_report")
public class InvestmentResearchReportPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "instrument_id", updatable = false)
    private Long instrumentId;

    @Column(name = "report_type", nullable = false, length = 64, updatable = false)
    private String reportType;

    @Column(name = "source_type", nullable = false, length = 32, updatable = false)
    private String sourceType;

    @Column(name = "source_reference_id", updatable = false)
    private Long sourceReferenceId;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "summary", nullable = false)
    private String summary = "";

    @Column(name = "content_markdown", nullable = false)
    private String contentMarkdown = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", nullable = false, columnDefinition = "jsonb")
    private String metricsJson = "{}";

    @Column(name = "data_as_of", nullable = false, updatable = false)
    private Instant dataAsOf;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "report_version", nullable = false, updatable = false)
    private Long reportVersion = 1L;
}
