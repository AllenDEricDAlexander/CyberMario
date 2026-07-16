package top.egon.mario.investment;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.investment.common.job.repository.InvestmentJobRepository;
import top.egon.mario.investment.marketdata.job.InvestmentMarketJobPlanner;
import top.egon.mario.investment.marketdata.provider.MarketDataProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.web.InvestmentMarketController;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.web.InvestmentStrategyController;
import top.egon.mario.investment.trading.job.PaperMaintenanceJobPlanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves the production module has a supported no-provider, no-subscription and no-strategy state.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.investment.job.runner.enabled=false",
        "mario.investment.market-data-planner-enabled=false",
        "mario.investment.paper-maintenance-planner-enabled=false"
})
class InvestmentProductionEmptyContextTests {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ProviderRegistry providerRegistry;
    @Autowired
    private InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    @Autowired
    private InvestmentStrategyRegistry strategyRegistry;
    @Autowired
    private InvestmentMarketJobPlanner marketPlanner;
    @Autowired
    private PaperMaintenanceJobPlanner paperPlanner;
    @Autowired
    private InvestmentJobRepository jobRepository;
    @Autowired
    private InvestmentMarketController marketController;
    @Autowired
    private InvestmentStrategyController strategyController;

    @MockitoBean
    private ChatModel chatModel;
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void emptyProductionRegistriesReturnEmptyApisAndScheduleNoWork() {
        reset(chatModel, redisTemplate);
        long jobsBefore = jobRepository.count();

        var marketResponse = marketController.instruments(1, 20, null, "SYMBOL_ASC").block();
        var strategyResponse = strategyController.list().block();

        assertThat(applicationContext.getBeansOfType(MarketDataProvider.class)).isEmpty();
        assertThat(providerRegistry.providers()).isEmpty();
        assertThat(subscriptionRegistry.subscriptions()).isEmpty();
        assertThat(strategyRegistry.descriptors()).isEmpty();
        assertThat(marketResponse).isNotNull();
        assertThat(marketResponse.data().records()).isEmpty();
        assertThat(marketResponse.data().total()).isZero();
        assertThat(strategyResponse).isNotNull();
        assertThat(strategyResponse.data()).isEmpty();
        assertThat(marketPlanner.tick()).isZero();
        assertThat(paperPlanner.tick()).isZero();
        assertThat(jobRepository.count()).isEqualTo(jobsBefore);
        assertThat(marketPlanner.isRunning()).isFalse();
        assertThat(paperPlanner.isRunning()).isFalse();
        assertThat(applicationContext.containsBean("investmentJobRunner")).isFalse();
        verifyNoInteractions(chatModel, redisTemplate);
    }
}
