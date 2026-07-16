package top.egon.mario.investment.common.job;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves code-registered job strategies without accepting runtime definitions.
 */
@Component
public class InvestmentJobHandlerRegistry {

    private final Map<InvestmentJobType, InvestmentJobHandler> handlers;

    public InvestmentJobHandlerRegistry(List<InvestmentJobHandler> handlers) {
        Map<InvestmentJobType, InvestmentJobHandler> registered = new EnumMap<>(InvestmentJobType.class);
        for (InvestmentJobHandler handler : handlers) {
            InvestmentJobHandler existing = registered.putIfAbsent(handler.jobType(), handler);
            if (existing != null) {
                throw new IllegalStateException("Duplicate Investment job handler for " + handler.jobType());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public InvestmentJobHandler require(InvestmentJobType jobType) {
        InvestmentJobHandler handler = handlers.get(jobType);
        if (handler == null) {
            throw new InvestmentJobNonRetryableException("JOB_HANDLER_NOT_FOUND",
                    "No Investment job handler registered for " + jobType);
        }
        return handler;
    }
}
