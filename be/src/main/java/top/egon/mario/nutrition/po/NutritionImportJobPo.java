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
import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.po.enums.NutritionImportType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "nutrition_import_job")
public class NutritionImportJobPo extends BaseAuditablePo {

    @Column(name = "family_id")
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 64)
    private NutritionImportType importType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_key", length = 512)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionImportStatus status = NutritionImportStatus.UPLOADED;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "warning_rows", nullable = false)
    private int warningRows;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_snapshot", nullable = false, columnDefinition = "jsonb")
    private String previewSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
