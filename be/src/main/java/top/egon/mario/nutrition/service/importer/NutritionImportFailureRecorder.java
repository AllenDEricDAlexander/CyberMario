package top.egon.mario.nutrition.service.importer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;

import java.time.Instant;

/**
 * Records import confirmation failures outside the rolled-back confirm transaction.
 */
@Component
public class NutritionImportFailureRecorder {

    private static final String CONFIRM_FAILED_SUMMARY = "nutrition import confirm failed";

    private final NutritionImportJobRepository importJobRepository;
    private final TransactionTemplate transactionTemplate;

    public NutritionImportFailureRecorder(NutritionImportJobRepository importJobRepository,
                                          PlatformTransactionManager transactionManager) {
        this.importJobRepository = importJobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordConfirmFailure(Long jobId) {
        transactionTemplate.executeWithoutResult(status -> importJobRepository.findById(jobId).ifPresent(job -> {
            if (NutritionImportStatus.COMPLETED == job.getStatus()) {
                return;
            }
            job.setStatus(NutritionImportStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorSummary(CONFIRM_FAILED_SUMMARY);
            importJobRepository.saveAndFlush(job);
        }));
    }
}
