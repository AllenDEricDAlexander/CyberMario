package top.egon.mario.investment.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.util.Objects;

/** Durable AGENT_ANALYSIS adapter with bounded retry and same-key decision recovery. */
@Component
public class InvestmentAgentJobHandler implements InvestmentJobHandler {

    private final InvestmentAgentRunner runner;
    private final InvestmentAgentRunService runService;
    private final ObjectMapper objectMapper;

    public InvestmentAgentJobHandler(
            InvestmentAgentRunner runner,
            InvestmentAgentRunService runService,
            ObjectMapper objectMapper) {
        this.runner = runner;
        this.runService = runService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.AGENT_ANALYSIS;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        InvestmentAgentRunService.JobInput input = input(claim.inputJson());
        if (claim.workspaceId() == null
                || !Objects.equals(runService.requireRun(input.runId()).getWorkspaceId(), claim.workspaceId())) {
            throw permanent(input.runId(), "INVESTMENT_AGENT_SCOPE_INVALID",
                    "Agent job workspace does not match its run", null);
        }
        try {
            InvestmentAgentRunner.RunOutcome result = runner.run(input.runId());
            return InvestmentJobHandlerResult.completed(
                    "{\"runId\":" + result.runId() + ",\"decisionId\":" + result.decisionId() + "}");
        } catch (InvestmentException exception) {
            throw permanent(input.runId(), exception.getCode(), exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (claim.attempts() + 1 >= claim.maxAttempts()) {
                throw permanent(input.runId(), "INVESTMENT_AGENT_EXECUTION_FAILED",
                        exception.getMessage(), exception);
            }
            throw new InvestmentJobRetryableException(
                    "INVESTMENT_AGENT_EXECUTION_RETRY", safeMessage(exception), exception);
        }
    }

    private InvestmentAgentRunService.JobInput input(String value) {
        try {
            InvestmentAgentRunService.JobInput input = objectMapper.readValue(
                    value, InvestmentAgentRunService.JobInput.class);
            if (input.runId() <= 0) {
                throw new IllegalArgumentException("Agent runId must be positive");
            }
            return input;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvestmentJobNonRetryableException(
                    "INVESTMENT_AGENT_JOB_INPUT_INVALID", "Agent job input is invalid", exception);
        }
    }

    private InvestmentJobNonRetryableException permanent(
            long runId, String code, String message, RuntimeException cause) {
        runService.markFailed(runId, code, message);
        return cause == null
                ? new InvestmentJobNonRetryableException(code, safeMessage(message))
                : new InvestmentJobNonRetryableException(code, safeMessage(message), cause);
    }

    private static String safeMessage(Throwable exception) {
        return safeMessage(exception == null ? null : exception.getMessage());
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Investment Agent execution failed" : message;
    }
}
