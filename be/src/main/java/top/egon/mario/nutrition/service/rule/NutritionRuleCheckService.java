package top.egon.mario.nutrition.service.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.service.NutritionException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Runs deterministic nutrition risk rules and records the resulting risk rows.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionRuleCheckService {

    private final List<NutritionRuleChecker> ruleCheckers;
    private final NutritionRiskCheckResultRepository riskCheckResultRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<RuleCheckResult> check(@NotNull RuleCheckRequest request) {
        archiveActiveUnresolvedResults(request);
        List<RuleCheckResult> results = ruleCheckers.stream()
                .sorted(Comparator.comparingInt(NutritionRuleChecker::order))
                .flatMap(checker -> checker.check(request).stream())
                .toList();
        results.forEach(result -> riskCheckResultRepository.save(toPo(request, result)));
        return results;
    }

    private void archiveActiveUnresolvedResults(RuleCheckRequest request) {
        List<NutritionRiskCheckResultPo> previousResults = riskCheckResultRepository
                .findByFamilyIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        request.familyId(), request.sourceType(), request.sourceId(), NutritionStatus.ACTIVE);
        previousResults.forEach(result -> {
            result.setResolved(true);
            result.setStatus(NutritionStatus.ARCHIVED);
        });
        riskCheckResultRepository.saveAll(previousResults);
    }

    private NutritionRiskCheckResultPo toPo(RuleCheckRequest request, RuleCheckResult result) {
        NutritionRiskCheckResultPo po = new NutritionRiskCheckResultPo();
        po.setFamilyId(request.familyId());
        po.setMemberProfileId(result.memberProfileId());
        po.setSourceType(request.sourceType());
        po.setSourceId(request.sourceId());
        po.setRuleCode(result.ruleCode());
        po.setRiskLevel(result.riskLevel());
        po.setRiskMessage(result.riskMessage());
        po.setRiskSnapshot(writeRiskSnapshot(result));
        po.setResolved(false);
        po.setStatus(NutritionStatus.ACTIVE);
        return po;
    }

    private String writeRiskSnapshot(RuleCheckResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "blocking", result.blocking(),
                    "requiresConfirmation", result.requiresConfirmation(),
                    "evidence", result.evidence()));
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition JSON value is invalid");
        }
    }
}
