package top.egon.mario.rbac.activation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies typed OTT settings, safe defaults, and Mock startup protection.
 */
class RbacOttConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RbacOttConfiguration.class)
            .withPropertyValues(
                    "mario.security.ott.enabled=true",
                    "mario.security.ott.activation-token-ttl=PT24H",
                    "mario.security.ott.public-base-url=http://localhost:5173",
                    "mario.security.ott.delivery-mode=MOCK"
            );

    @Test
    void startsMockDeliveryOnlyWhenExplicitlyAllowed() {
        contextRunner.withPropertyValues("mario.security.ott.mock-delivery-allowed=true")
                .run(context -> assertThat(context).hasSingleBean(ActivationLinkDelivery.class));
    }

    @Test
    void rejectsMockDeliveryWhenAllowFlagIsFalse() {
        contextRunner.withPropertyValues("mario.security.ott.mock-delivery-allowed=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("mock delivery is not allowed");
                });
    }

    @Test
    void rejectsMockDeliveryInProductionEvenWhenAllowFlagIsTrue() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("mario.security.ott.mock-delivery-allowed=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("mock delivery is forbidden in prod");
                });
    }
}
