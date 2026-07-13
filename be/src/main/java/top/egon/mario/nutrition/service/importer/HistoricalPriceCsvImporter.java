package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CSV importer for family historical food prices.
 */
@Component
public class HistoricalPriceCsvImporter
        extends NutritionCsvImportTemplate<HistoricalPriceCsvImporter.PriceRow> {

    private final NutritionAccessService accessService;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionFoodPriceRecordRepository foodPriceRecordRepository;

    public HistoricalPriceCsvImporter(NutritionImportJobRepository importJobRepository,
                                      NutritionImportErrorRepository importErrorRepository,
                                      NutritionImportFailureRecorder failureRecorder,
                                      EntityManager entityManager,
                                      ObjectMapper objectMapper,
                                      NutritionAccessService accessService,
                                      NutritionStandardFoodRepository standardFoodRepository,
                                      NutritionFoodPriceRecordRepository foodPriceRecordRepository) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.accessService = accessService;
        this.standardFoodRepository = standardFoodRepository;
        this.foodPriceRecordRepository = foodPriceRecordRepository;
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.HISTORICAL_PRICE;
    }

    @Override
    protected Class<PriceRow> rowType() {
        return PriceRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        accessService.requireManageFamily(actorId(principal), requireFamilyId(request.familyId()));
    }

    @Override
    protected void authorizeRead(NutritionImportJobPo job, RbacPrincipal principal) {
        accessService.requireReadFamily(actorId(principal), requireFamilyId(job.getFamilyId()));
    }

    @Override
    protected void authorizeConfirm(NutritionImportJobPo job, RbacPrincipal principal) {
        accessService.requireManageFamily(actorId(principal), requireFamilyId(job.getFamilyId()));
    }

    @Override
    protected PriceRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        String rawFoodName = requireValue(row, issues, "raw_food_name");
        Long standardFoodId = longValue(row, issues, "standard_food_id", false);
        LocalDate priceDate = dateValue(row, issues, "price_date");
        BigDecimal totalPrice = decimalValue(row, issues, "total_price", true);
        BigDecimal specAmount = decimalValue(row, issues, "spec_amount", false);
        BigDecimal purchaseQuantity = decimalValue(row, issues, "purchase_quantity", false);
        BigDecimal normalizedUnitPrice = decimalValue(row, issues, "normalized_unit_price", false);
        if (issues.hasError()) {
            return null;
        }
        return new PriceRow(standardFoodId, rawFoodName, priceDate,
                trimToNull(value(row, "channel")), trimToNull(value(row, "brand")),
                specAmount, trimToNull(value(row, "spec_unit")), purchaseQuantity, totalPrice,
                normalizedUnitPrice, trimToNull(value(row, "note")));
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<PriceRow> validRows, RbacPrincipal principal) {
        Long familyId = requireFamilyId(job.getFamilyId());
        for (PriceRow row : validRows) {
            if (row.standardFoodId() != null) {
                standardFoodRepository.findByIdAndDeletedFalse(row.standardFoodId())
                        .filter(food -> NutritionStatus.ACTIVE == food.getStatus())
                        .orElseThrow(() -> new NutritionException(
                                "NUTRITION_STANDARD_FOOD_NOT_FOUND", "nutrition standard food not found"));
            }
            NutritionFoodPriceRecordPo price = new NutritionFoodPriceRecordPo();
            price.setFamilyId(familyId);
            price.setStandardFoodId(row.standardFoodId());
            price.setRawFoodName(row.rawFoodName());
            price.setPriceDate(row.priceDate());
            price.setChannel(row.channel());
            price.setBrand(row.brand());
            price.setSpecAmount(row.specAmount());
            price.setSpecUnit(row.specUnit());
            price.setPurchaseQuantity(row.purchaseQuantity());
            price.setTotalPrice(row.totalPrice());
            price.setNormalizedUnitPrice(row.normalizedUnitPrice());
            price.setSourceType("IMPORTED");
            price.setNote(row.note());
            foodPriceRecordRepository.save(price);
        }
    }

    private Long longValue(CsvRow row, IssueCollector issues, String columnName, boolean required) {
        String raw = value(row, columnName);
        if (!StringUtils.hasText(raw)) {
            if (required) {
                issues.error(columnName, "REQUIRED", columnName + " is required");
            }
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            issues.error(columnName, "INVALID_ID", columnName + " must be a positive integer");
            return null;
        }
    }

    private LocalDate dateValue(CsvRow row, IssueCollector issues, String columnName) {
        String raw = requireValue(row, issues, columnName);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            issues.error(columnName, "INVALID_DATE", columnName + " must use yyyy-MM-dd");
            return null;
        }
    }

    private Long requireFamilyId(Long familyId) {
        if (familyId == null || familyId <= 0) {
            throw new NutritionException(
                    "NUTRITION_IMPORT_FAMILY_REQUIRED", "historical price import requires familyId");
        }
        return familyId;
    }

    private Long actorId(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return principal.userId();
    }

    public record PriceRow(
            Long standardFoodId,
            String rawFoodName,
            LocalDate priceDate,
            String channel,
            String brand,
            BigDecimal specAmount,
            String specUnit,
            BigDecimal purchaseQuantity,
            BigDecimal totalPrice,
            BigDecimal normalizedUnitPrice,
            String note
    ) {
    }
}
