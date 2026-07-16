package top.egon.mario.investment.research.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.InvestmentJobType;

/**
 * Durable REPORT_BUILD handler; slow generation stays outside database transactions.
 */
@Component
public class InvestmentReportBuildJobHandler implements InvestmentJobHandler {

    private final InvestmentReportService reportService;
    private final InvestmentResearchReportGeneratorRegistry generatorRegistry;
    private final ObjectMapper objectMapper;

    public InvestmentReportBuildJobHandler(InvestmentReportService reportService,
                                           InvestmentResearchReportGeneratorRegistry generatorRegistry,
                                           ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.generatorRegistry = generatorRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.REPORT_BUILD;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        InvestmentReportBuildInput input = input(claim.inputJson());
        try {
            PreparedResearchReport prepared = reportService.prepareBuild(input.reportId(), input.reportVersion());
            if (prepared.ready()) {
                return completed(input.reportId(), input.reportVersion());
            }
            if (claim.workspaceId() == null || claim.workspaceId() != prepared.context().workspaceId()) {
                throw new InvestmentJobNonRetryableException(
                        "REPORT_OWNER_MISMATCH", "Report job workspace does not own the report");
            }
            GeneratedResearchReport generated = generatorRegistry
                    .require(prepared.context().input().reportType()).generate(prepared.context());
            reportService.completeBuild(prepared.context(), generated);
            return completed(input.reportId(), input.reportVersion());
        } catch (InvestmentJobNonRetryableException exception) {
            reportService.failBuild(input.reportId(), exception.getMessage());
            throw exception;
        } catch (InvestmentException exception) {
            if (nonRetryable(exception.getErrorCode())) {
                reportService.failBuild(input.reportId(), exception.getMessage());
                throw new InvestmentJobNonRetryableException(
                        exception.getCode(), exception.getMessage(), exception);
            }
            return retry(claim, input.reportId(), exception);
        } catch (RuntimeException exception) {
            return retry(claim, input.reportId(), exception);
        }
    }

    private InvestmentJobHandlerResult retry(InvestmentJobClaim claim, long reportId, RuntimeException exception) {
        if (claim.attempts() + 1 >= claim.maxAttempts()) {
            reportService.failBuild(reportId, message(exception));
        }
        throw new InvestmentJobRetryableException("REPORT_BUILD_FAILED", message(exception), exception);
    }

    private InvestmentReportBuildInput input(String value) {
        try {
            InvestmentReportBuildInput input = objectMapper.readValue(value, InvestmentReportBuildInput.class);
            if (input.reportId() <= 0 || input.reportVersion() <= 0) {
                throw new IllegalArgumentException("Report job input ids must be positive");
            }
            return input;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvestmentJobNonRetryableException(
                    "REPORT_INPUT_INVALID", "Report job input is invalid", exception);
        }
    }

    private InvestmentJobHandlerResult completed(long reportId, long reportVersion) {
        return InvestmentJobHandlerResult.completed(
                "{\"reportId\":" + reportId + ",\"reportVersion\":" + reportVersion + "}");
    }

    private boolean nonRetryable(InvestmentErrorCode errorCode) {
        return errorCode == InvestmentErrorCode.INVALID_REQUEST
                || errorCode == InvestmentErrorCode.NOT_FOUND
                || errorCode == InvestmentErrorCode.FORBIDDEN
                || errorCode == InvestmentErrorCode.CAPABILITY_UNAVAILABLE
                || errorCode == InvestmentErrorCode.SUBSCRIPTION_REJECTED;
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
