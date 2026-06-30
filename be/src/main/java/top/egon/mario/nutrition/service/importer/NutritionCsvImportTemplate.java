package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.response.NutritionImportErrorResponse;
import top.egon.mario.nutrition.dto.response.NutritionImportJobResponse;
import top.egon.mario.nutrition.po.NutritionImportErrorPo;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Template Method for nutrition CSV import parse, validate, preview and confirm lifecycle.
 */
public abstract class NutritionCsvImportTemplate<T> {

    private static final String SEVERITY_ERROR = "ERROR";
    private static final String SEVERITY_WARNING = "WARNING";

    private final NutritionImportJobRepository importJobRepository;
    private final NutritionImportErrorRepository importErrorRepository;
    private final NutritionImportFailureRecorder failureRecorder;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    protected NutritionCsvImportTemplate(NutritionImportJobRepository importJobRepository,
                                         NutritionImportErrorRepository importErrorRepository,
                                         NutritionImportFailureRecorder failureRecorder,
                                         EntityManager entityManager,
                                         ObjectMapper objectMapper) {
        this.importJobRepository = importJobRepository;
        this.importErrorRepository = importErrorRepository;
        this.failureRecorder = failureRecorder;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public final NutritionImportType importType() {
        return supportedImportType();
    }

    public final NutritionImportJobResponse createImportJob(CreateNutritionImportJobRequest request,
                                                            RbacPrincipal principal) {
        authorizeCreate(request, principal);
        NutritionImportJobPo job = new NutritionImportJobPo();
        job.setImportType(supportedImportType());
        job.setFamilyId(request.familyId());
        job.setFileName(request.fileName().trim());
        job.setStatus(NutritionImportStatus.PARSING);
        job.setStartedAt(Instant.now());
        NutritionImportJobPo savedJob = importJobRepository.save(job);

        CsvPreview<T> preview = previewRows(parseRows(request.csvContent()));
        savedJob.setStatus(preview.fatal() ? NutritionImportStatus.FAILED : NutritionImportStatus.PREVIEW_READY);
        savedJob.setTotalRows(preview.totalRows());
        savedJob.setSuccessRows(preview.validRows().size());
        savedJob.setFailedRows(preview.failedRows());
        savedJob.setWarningRows(preview.warningRows());
        savedJob.setErrorSummary(preview.errorSummary());
        savedJob.setPreviewSnapshot(writeJson(preview.validRows()));
        savedJob.setCompletedAt(Instant.now());
        NutritionImportJobPo previewJob = importJobRepository.save(savedJob);
        saveIssues(previewJob.getId(), preview.issues());
        return toResponse(previewJob);
    }

    public final NutritionImportJobResponse getImportJob(NutritionImportJobPo job, RbacPrincipal principal) {
        authorizeRead(job, principal);
        return toResponse(job);
    }

    public final NutritionImportJobResponse confirmImportJob(NutritionImportJobPo job, RbacPrincipal principal) {
        authorizeConfirm(job, principal);
        if (NutritionImportStatus.COMPLETED == job.getStatus()) {
            return toResponse(job);
        }
        if (NutritionImportStatus.PREVIEW_READY != job.getStatus()) {
            throw new NutritionException("NUTRITION_IMPORT_STATUS_INVALID",
                    "nutrition import job is not ready to confirm");
        }
        try {
            List<T> validRows = readPreviewRows(job.getPreviewSnapshot());
            job.setStatus(NutritionImportStatus.IMPORTING);
            importJobRepository.save(job);
            persistRows(job, validRows, principal);
            flushRows();
            job.setStatus(NutritionImportStatus.COMPLETED);
            job.setConfirmedAt(Instant.now());
            job.setCompletedAt(Instant.now());
            return toResponse(importJobRepository.save(job));
        } catch (RuntimeException ex) {
            recordConfirmFailureAfterRollback(job.getId());
            throw ex;
        }
    }

    protected abstract NutritionImportType supportedImportType();

    protected abstract Class<T> rowType();

    protected abstract void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal);

    protected abstract void authorizeRead(NutritionImportJobPo job, RbacPrincipal principal);

    protected abstract void authorizeConfirm(NutritionImportJobPo job, RbacPrincipal principal);

    protected abstract T mapRow(CsvRow row, ImportContext context, IssueCollector issues);

    protected abstract void persistRows(NutritionImportJobPo job, List<T> validRows, RbacPrincipal principal);

    protected void flushRows() {
        entityManager.flush();
    }

    private void recordConfirmFailureAfterRollback(Long jobId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            failureRecorder.recordConfirmFailure(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    failureRecorder.recordConfirmFailure(jobId);
                }
            }
        });
    }

    protected final String value(CsvRow row, String columnName) {
        return row.value(columnName);
    }

    protected final String firstValue(CsvRow row, String... columnNames) {
        for (String columnName : columnNames) {
            String value = row.value(columnName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    protected final String requireValue(CsvRow row, IssueCollector issues, String columnName) {
        String value = row.value(columnName);
        if (!StringUtils.hasText(value)) {
            issues.error(columnName, "REQUIRED", columnName + " is required");
            return null;
        }
        return value.trim();
    }

    protected final String requireFirstValue(CsvRow row, IssueCollector issues, String errorColumnName,
                                             String... columnNames) {
        String value = firstValue(row, columnNames);
        if (!StringUtils.hasText(value)) {
            issues.error(errorColumnName, "REQUIRED", errorColumnName + " is required");
            return null;
        }
        return value.trim();
    }

    protected final BigDecimal decimalValue(CsvRow row, IssueCollector issues, String columnName, boolean required) {
        String value = row.value(columnName);
        if (!StringUtils.hasText(value)) {
            if (required) {
                issues.error(columnName, "REQUIRED", columnName + " is required");
            }
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            issues.error(columnName, "INVALID_DECIMAL", columnName + " must be a decimal number");
            return null;
        }
    }

    protected final String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    protected final ObjectMapper objectMapper() {
        return objectMapper;
    }

    protected final NutritionImportJobResponse toResponse(NutritionImportJobPo job) {
        List<NutritionImportErrorResponse> errors = importErrorRepository
                .findByImportJobIdOrderByRowNoAsc(job.getId())
                .stream()
                .map(error -> new NutritionImportErrorResponse(error.getId(), error.getRowNo(),
                        error.getColumnName(), error.getErrorCode(), error.getErrorMessage(), severity(error)))
                .toList();
        return new NutritionImportJobResponse(job.getId(), job.getFamilyId(), job.getImportType(),
                job.getFileName(), job.getStatus(), job.getTotalRows(), job.getSuccessRows(),
                job.getFailedRows(), job.getWarningRows(), job.getErrorSummary(), errors,
                job.getCreatedAt(), job.getCompletedAt(), job.getConfirmedAt());
    }

    private CsvPreview<T> previewRows(List<CsvRow> rows) {
        if (rows.isEmpty()) {
            RowIssue issue = new RowIssue(1, null, "EMPTY_CSV", "CSV contains no data rows",
                    Map.of(), SEVERITY_ERROR);
            return new CsvPreview<>(0, List.of(), List.of(issue), 1, 0,
                    "CSV contains no data rows", true);
        }
        ImportContext context = new ImportContext();
        List<RowIssue> issues = new ArrayList<>();
        List<T> validRows = new ArrayList<>();
        Set<Integer> errorRows = new LinkedHashSet<>();
        Set<Integer> warningRows = new LinkedHashSet<>();
        for (CsvRow row : rows) {
            IssueCollector collector = new IssueCollector(row);
            T mapped = mapRow(row, context, collector);
            issues.addAll(collector.issues());
            collector.issues().stream()
                    .filter(RowIssue::error)
                    .map(RowIssue::rowNo)
                    .forEach(errorRows::add);
            collector.issues().stream()
                    .filter(RowIssue::warning)
                    .map(RowIssue::rowNo)
                    .forEach(warningRows::add);
            if (!collector.hasError() && mapped != null) {
                validRows.add(mapped);
            }
        }
        String summary = errorRows.isEmpty() ? null : "CSV contains " + errorRows.size() + " failed row(s)";
        return new CsvPreview<>(rows.size(), validRows, issues, errorRows.size(), warningRows.size(), summary,
                false);
    }

    private void saveIssues(Long jobId, List<RowIssue> issues) {
        for (RowIssue issue : issues) {
            NutritionImportErrorPo error = new NutritionImportErrorPo();
            error.setImportJobId(jobId);
            error.setRowNo(issue.rowNo());
            error.setColumnName(issue.columnName());
            error.setErrorCode(issue.errorCode());
            error.setErrorMessage(issue.errorMessage());
            error.setRawRowSnapshot(writeJson(issue.rawRow()));
            error.setMetadataJson(writeJson(Map.of("severity", issue.severity())));
            importErrorRepository.save(error);
        }
    }

    private List<T> readPreviewRows(String previewSnapshot) {
        if (!StringUtils.hasText(previewSnapshot) || "{}".equals(previewSnapshot)) {
            return List.of();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, rowType());
            return objectMapper.readValue(previewSnapshot, type);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_IMPORT_PREVIEW_INVALID",
                    "nutrition import preview snapshot is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_IMPORT_JSON_INVALID", "nutrition import JSON value is invalid");
        }
    }

    private String severity(NutritionImportErrorPo error) {
        if (!StringUtils.hasText(error.getMetadataJson())) {
            return SEVERITY_ERROR;
        }
        try {
            JsonNode root = objectMapper.readTree(error.getMetadataJson());
            String severity = root.path("severity").asText(SEVERITY_ERROR);
            return StringUtils.hasText(severity) ? severity : SEVERITY_ERROR;
        } catch (JsonProcessingException e) {
            return SEVERITY_ERROR;
        }
    }

    private List<CsvRow> parseRows(String csvContent) {
        if (!StringUtils.hasText(csvContent)) {
            return List.of();
        }
        String[] lines = csvContent.strip().split("\\R");
        if (lines.length == 0 || !StringUtils.hasText(lines[0])) {
            return List.of();
        }
        List<String> headers = parseLine(lines[0]);
        List<CsvRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!StringUtils.hasText(lines[i])) {
                continue;
            }
            List<String> values = parseLine(lines[i]);
            Map<String, String> valuesByHeader = new LinkedHashMap<>();
            Map<String, String> rawValues = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String header = headers.get(columnIndex).trim();
                String value = columnIndex < values.size() ? values.get(columnIndex).trim() : "";
                valuesByHeader.put(normalizeHeader(header), value);
                rawValues.put(header, value);
            }
            rows.add(new CsvRow(i + 1, valuesByHeader, rawValues));
        }
        return rows;
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String normalizeHeader(String header) {
        return header.toLowerCase(Locale.ROOT);
    }

    protected record CsvRow(int rowNo, Map<String, String> valuesByHeader, Map<String, String> rawValues) {

        String value(String columnName) {
            return valuesByHeader.get(columnName.toLowerCase(Locale.ROOT));
        }
    }

    protected static class ImportContext {

        private final Map<String, Object> values = new HashMap<>();

        @SuppressWarnings("unchecked")
        <V> V computeIfAbsent(String key, java.util.function.Supplier<V> supplier) {
            return (V) values.computeIfAbsent(key, ignored -> supplier.get());
        }
    }

    protected static class IssueCollector {

        private final CsvRow row;
        private final List<RowIssue> issues = new ArrayList<>();

        IssueCollector(CsvRow row) {
            this.row = row;
        }

        public void error(String columnName, String errorCode, String errorMessage) {
            issues.add(new RowIssue(row.rowNo(), columnName, errorCode, errorMessage,
                    row.rawValues(), SEVERITY_ERROR));
        }

        public void warning(String columnName, String errorCode, String errorMessage) {
            issues.add(new RowIssue(row.rowNo(), columnName, errorCode, errorMessage,
                    row.rawValues(), SEVERITY_WARNING));
        }

        boolean hasError() {
            return issues.stream().anyMatch(RowIssue::error);
        }

        List<RowIssue> issues() {
            return issues;
        }
    }

    private record CsvPreview<T>(
            int totalRows,
            List<T> validRows,
            List<RowIssue> issues,
            int failedRows,
            int warningRows,
            String errorSummary,
            boolean fatal
    ) {
    }

    private record RowIssue(
            int rowNo,
            String columnName,
            String errorCode,
            String errorMessage,
            Map<String, String> rawRow,
            String severity
    ) {

        boolean error() {
            return SEVERITY_ERROR.equals(severity);
        }

        boolean warning() {
            return SEVERITY_WARNING.equals(severity);
        }
    }
}
