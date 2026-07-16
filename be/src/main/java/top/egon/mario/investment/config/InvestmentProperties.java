package top.egon.mario.investment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalized Investment module feature switches.
 */
@ConfigurationProperties(prefix = "mario.investment")
public record InvestmentProperties(
        @DefaultValue("false") boolean marketDataPlannerEnabled
) {
}
