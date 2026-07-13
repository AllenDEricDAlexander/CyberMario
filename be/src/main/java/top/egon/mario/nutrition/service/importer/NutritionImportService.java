package top.egon.mario.nutrition.service.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.response.NutritionImportJobResponse;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service for nutrition import jobs.
 */
@Service
@Validated
public class NutritionImportService {

    private final NutritionImportJobRepository importJobRepository;
    private final Map<NutritionImportType, NutritionCsvImportTemplate<?>> importers;

    public NutritionImportService(NutritionImportJobRepository importJobRepository,
                                  List<NutritionCsvImportTemplate<?>> importers) {
        this.importJobRepository = importJobRepository;
        this.importers = new EnumMap<>(NutritionImportType.class);
        for (NutritionCsvImportTemplate<?> importer : importers) {
            NutritionCsvImportTemplate<?> previous = this.importers.put(importer.importType(), importer);
            if (previous != null) {
                throw new IllegalStateException("Duplicate nutrition importer for " + importer.importType());
            }
        }
        Set<NutritionImportType> missingTypes = EnumSet.allOf(NutritionImportType.class);
        missingTypes.removeAll(this.importers.keySet());
        if (!missingTypes.isEmpty()) {
            throw new IllegalStateException("Missing nutrition importers for " + missingTypes);
        }
    }

    @Transactional
    public NutritionImportJobResponse createImportJob(@Valid @NotNull CreateNutritionImportJobRequest request,
                                                      RbacPrincipal principal) {
        return importer(request.importType()).createImportJob(request, principal);
    }

    @Transactional(readOnly = true)
    public NutritionImportJobResponse getImportJob(@NotNull Long jobId, RbacPrincipal principal) {
        NutritionImportJobPo job = getJob(jobId);
        return importer(job.getImportType()).getImportJob(job, principal);
    }

    @Transactional
    public NutritionImportJobResponse confirmImportJob(@NotNull Long jobId, RbacPrincipal principal) {
        NutritionImportJobPo job = getLockedJob(jobId);
        return importer(job.getImportType()).confirmImportJob(job, principal);
    }

    private NutritionImportJobPo getJob(Long jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_IMPORT_JOB_NOT_FOUND", "nutrition import job not found"));
    }

    private NutritionImportJobPo getLockedJob(Long jobId) {
        return importJobRepository.findLockedById(jobId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_IMPORT_JOB_NOT_FOUND", "nutrition import job not found"));
    }

    private NutritionCsvImportTemplate<?> importer(NutritionImportType importType) {
        NutritionCsvImportTemplate<?> importer = importers.get(importType);
        if (importer == null) {
            throw new NutritionException("NUTRITION_IMPORT_TYPE_UNSUPPORTED",
                    "nutrition import type is not supported");
        }
        return importer;
    }
}
