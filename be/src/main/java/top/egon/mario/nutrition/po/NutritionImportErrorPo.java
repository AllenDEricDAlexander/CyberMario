package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "nutrition_import_error")
public class NutritionImportErrorPo extends BaseAuditablePo {

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "column_name", length = 128)
    private String columnName;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "error_message", nullable = false, length = 1024)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_row_snapshot", nullable = false, columnDefinition = "jsonb")
    private String rawRowSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
