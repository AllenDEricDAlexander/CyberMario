package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.po.NutritionHealthTagPo;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Locale;

/**
 * Shared CSV row lifecycle for platform health-tag dictionaries.
 */
public abstract class AbstractHealthTagCsvImporter
        extends NutritionCsvImportTemplate<AbstractHealthTagCsvImporter.TagRow> {

    private final NutritionHealthTagRepository healthTagRepository;

    protected AbstractHealthTagCsvImporter(NutritionImportJobRepository importJobRepository,
                                           NutritionImportErrorRepository importErrorRepository,
                                           NutritionImportFailureRecorder failureRecorder,
                                           EntityManager entityManager,
                                           ObjectMapper objectMapper,
                                           NutritionHealthTagRepository healthTagRepository) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.healthTagRepository = healthTagRepository;
    }

    protected abstract String tagType();

    @Override
    protected Class<TagRow> rowType() {
        return TagRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected void authorizeRead(NutritionImportJobPo job, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected void authorizeConfirm(NutritionImportJobPo job, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected TagRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        String tagCode = requireValue(row, issues, "tag_code");
        String name = requireValue(row, issues, "name");
        String description = trimToNull(value(row, "description"));
        Integer sortOrder = integerValue(row, issues, "sort_order", false);
        if (issues.hasError()) {
            return null;
        }
        return new TagRow(tagCode.toUpperCase(Locale.ROOT), name, description,
                sortOrder == null ? 0 : sortOrder);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<TagRow> validRows, RbacPrincipal principal) {
        for (TagRow row : validRows) {
            NutritionHealthTagPo tag = healthTagRepository
                    .findByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndDeletedFalse(tagType(), row.tagCode())
                    .orElseGet(NutritionHealthTagPo::new);
            tag.setTagType(tagType());
            tag.setTagCode(row.tagCode());
            tag.setName(row.name());
            tag.setDescription(row.description());
            tag.setSortOrder(row.sortOrder());
            tag.setStatus(NutritionStatus.ACTIVE);
            tag.setDeleted(false);
            healthTagRepository.save(tag);
        }
    }

    private Integer integerValue(CsvRow row, IssueCollector issues, String columnName, boolean required) {
        String raw = value(row, columnName);
        if (raw == null || raw.isBlank()) {
            if (required) {
                issues.error(columnName, "REQUIRED", columnName + " is required");
            }
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            issues.error(columnName, "INVALID_INTEGER", columnName + " must be an integer");
            return null;
        }
    }

    public record TagRow(String tagCode, String name, String description, Integer sortOrder) {
    }
}
