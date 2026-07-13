package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;

/**
 * CSV importer for diet-goal tags.
 */
@Component
public class DietGoalCsvImporter extends AbstractHealthTagCsvImporter {

    public DietGoalCsvImporter(NutritionImportJobRepository importJobRepository,
                               NutritionImportErrorRepository importErrorRepository,
                               NutritionImportFailureRecorder failureRecorder,
                               EntityManager entityManager,
                               ObjectMapper objectMapper,
                               NutritionHealthTagRepository healthTagRepository) {
        super(importJobRepository, importErrorRepository, failureRecorder,
                entityManager, objectMapper, healthTagRepository);
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.DIET_GOAL;
    }

    @Override
    protected String tagType() {
        return "DIET_GOAL";
    }
}
