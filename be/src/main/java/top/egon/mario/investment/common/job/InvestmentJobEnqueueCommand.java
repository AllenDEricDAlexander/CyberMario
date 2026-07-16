package top.egon.mario.investment.common.job;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.time.Instant;

/**
 * Immutable input for idempotent durable-job creation.
 */
public record InvestmentJobEnqueueCommand(
        Long workspaceId,
        InvestmentJobType jobType,
        int priority,
        Instant availableAt,
        int maxAttempts,
        String idempotencyKey,
        String inputJson
) {

    public InvestmentJobEnqueueCommand {
        Assert.notNull(jobType, "jobType must not be null");
        Assert.notNull(availableAt, "availableAt must not be null");
        Assert.isTrue(maxAttempts > 0, "maxAttempts must be greater than zero");
        Assert.isTrue(StringUtils.hasText(idempotencyKey), "idempotencyKey must not be blank");
        Assert.isTrue(idempotencyKey.length() <= 256, "idempotencyKey must not exceed 256 characters");
        Assert.isTrue(StringUtils.hasText(inputJson), "inputJson must not be blank");
    }
}
