package top.egon.mario.investment.quant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.strategy.fixture.TestEmaCrossStrategy;
import top.egon.mario.investment.quant.web.InvestmentStrategyController;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static top.egon.mario.investment.quant.strategy.fixture.TestMarketSubscriptionFixtures.subscriptions;

class InvestmentStrategyControllerTests {

    @Test
    void returnsAnExplicitEmptyCodeRegistry() {
        InvestmentStrategyController controller = controller(new InvestmentStrategyRegistry(
                List.of(), mock(InvestmentMarketSubscriptionRegistry.class)));

        StepVerifier.create(controller.list())
                .assertNext(response -> assertThat(response.data()).isEmpty())
                .verifyComplete();
    }

    @Test
    void exposesReadOnlyDescriptorsWithoutStrategyMutationRoutes() throws Exception {
        TestEmaCrossStrategy strategy = new TestEmaCrossStrategy();
        InvestmentMarketSubscriptionRegistry subscriptions = subscriptions(
                strategy.descriptor().requiredCapabilities(), strategy.descriptor().supportedIntervals());
        InvestmentStrategyController controller = controller(
                new InvestmentStrategyRegistry(List.of(strategy), subscriptions));

        StepVerifier.create(controller.list())
                .assertNext(response -> assertThat(response.data()).containsExactly(strategy.descriptor()))
                .verifyComplete();
        StepVerifier.create(controller.detail("TEST_EMA_CROSS"))
                .assertNext(response -> assertThat(response.data()).isEqualTo(strategy.descriptor()))
                .verifyComplete();

        Method list = InvestmentStrategyController.class.getMethod("list");
        Method detail = InvestmentStrategyController.class.getMethod("detail", String.class);
        assertThat(list.getAnnotation(GetMapping.class).value()).isEmpty();
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{strategyCode}");
        assertThat(InvestmentStrategyController.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) == null
                        && method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class) == null
                        && method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) == null);
    }

    private InvestmentStrategyController controller(InvestmentStrategyRegistry registry) {
        InvestmentStrategyController controller = new InvestmentStrategyController(registry);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        return controller;
    }
}
