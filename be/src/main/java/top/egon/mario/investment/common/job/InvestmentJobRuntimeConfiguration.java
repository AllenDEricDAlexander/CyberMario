package top.egon.mario.investment.common.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/**
 * Defines replaceable time and fencing-token sources for the durable job runtime.
 */
@Configuration(proxyBeanMethods = false)
class InvestmentJobRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock investmentJobClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(InvestmentJobClaimTokenSource.class)
    InvestmentJobClaimTokenSource investmentJobClaimTokenSource() {
        return () -> UUID.randomUUID().toString();
    }
}
