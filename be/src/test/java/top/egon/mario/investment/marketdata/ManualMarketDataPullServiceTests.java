package top.egon.mario.investment.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.service.ManualMarketDataPullService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.web.dto.ManualMarketDataPullRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualMarketDataPullServiceTests {

    private final Instant now = Instant.parse("2026-07-19T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private InvestmentMarketSubscriptionRegistry registry;
    private InvestmentJobEnqueueService enqueueService;
    private ManualMarketDataPullService service;

    @BeforeEach
    void setUp() {
        registry = mock(InvestmentMarketSubscriptionRegistry.class);
        enqueueService = mock(InvestmentJobEnqueueService.class);
        when(enqueueService.enqueue(any())).thenReturn(42L);
        service = new ManualMarketDataPullService(registry, enqueueService, objectMapper,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void enqueuesAUniqueManualBackfillWithoutCallingProvider() throws Exception {
        Instant start = now.minus(Duration.ofDays(7));
        var response = service.enqueue(new ManualMarketDataPullRequest("btcusdt",
                DataCapability.MARKET_CANDLE, BarInterval.H4, start, now));

        assertThat(response.jobId()).isEqualTo(42L);
        assertThat(response.jobType()).isEqualTo("BAR_BACKFILL");
        assertThat(response.status()).isEqualTo("PENDING");
        ArgumentCaptor<InvestmentJobEnqueueCommand> captor =
                ArgumentCaptor.forClass(InvestmentJobEnqueueCommand.class);
        verify(enqueueService).enqueue(captor.capture());
        InvestmentJobEnqueueCommand command = captor.getValue();
        assertThat(command.idempotencyKey()).startsWith("manual-market:");
        assertThat(command.jobType()).isEqualTo(InvestmentJobType.BAR_BACKFILL);
        MarketDataJobInput input = objectMapper.readValue(command.inputJson(), MarketDataJobInput.class);
        assertThat(input.sourceCode()).isEqualTo("BITGET");
        assertThat(input.productType()).isEqualTo(ProductType.USDT_FUTURES);
        assertThat(input.symbol()).isEqualTo("BTCUSDT");
        assertThat(input.priceType()).isEqualTo(PriceType.MARKET);
        assertThat(input.interval()).isEqualTo(BarInterval.H4);
        assertThat(input.pageSize()).isEqualTo(100);
        assertThat(input.triggerSource()).isEqualTo("MANUAL");
        verify(registry).requireCandle("BITGET", ProductType.USDT_FUTURES, "BTCUSDT",
                BarInterval.H4, PriceType.MARKET);
    }

    @Test
    void validatesFundingShapeFutureAndMaximumWindowBeforeEnqueue() {
        assertInvalid(new ManualMarketDataPullRequest("SOLUSDT", DataCapability.FUNDING_RATE,
                BarInterval.M1, now.minusSeconds(1), now));
        assertInvalid(new ManualMarketDataPullRequest("SOLUSDT", DataCapability.FUNDING_RATE,
                null, now.minusSeconds(1), now.plusSeconds(1)));
        assertInvalid(new ManualMarketDataPullRequest("SOLUSDT", DataCapability.FUNDING_RATE,
                null, now.minus(Duration.ofDays(731)), now));
        assertInvalid(new ManualMarketDataPullRequest("ETHUSDT", DataCapability.FUNDING_RATE,
                null, now.minusSeconds(1), now));
        assertInvalid(new ManualMarketDataPullRequest("BTCUSDT", DataCapability.CURRENT_FUNDING_RATE,
                null, now.minusSeconds(1), now));
        verify(enqueueService, never()).enqueue(any());
    }

    private void assertInvalid(ManualMarketDataPullRequest request) {
        assertThatThrownBy(() -> service.enqueue(request))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
    }
}
