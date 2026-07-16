package top.egon.mario.investment.common.job;

import top.egon.mario.investment.common.model.InvestmentJobType;

/**
 * Strategy implemented by each durable Investment job type.
 *
 * <p>A lease loss prevents the runtime's final state transition but cannot cancel handler code or roll back remote
 * side effects already in flight. Implementations must therefore make side effects idempotent by job/business key or
 * protect domain writes with their own fencing/version check.</p>
 */
public interface InvestmentJobHandler {

    InvestmentJobType jobType();

    InvestmentJobHandlerResult execute(InvestmentJobClaim claim);
}
