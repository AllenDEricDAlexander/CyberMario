package top.egon.mario.investment.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.trading.matching.PaperMatchJobHandler;
import top.egon.mario.investment.trading.matching.PaperMatchJobInput;
import top.egon.mario.investment.trading.matching.PaperMatchingMarketReader;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.service.PaperExecutionTransactionService;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperExecutionResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperMatchingJobHandlerTests {

    private static final Instant ELIGIBLE = Instant.parse("2026-07-17T00:01:00Z");
    private static final Instant NOW = Instant.parse("2026-07-17T00:03:10Z");

    @Mock private PaperOrderService orderService;
    @Mock private PaperMatchingMarketReader marketReader;
    @Mock private PaperExecutionTransactionService executionService;

    private ObjectMapper objectMapper;
    private PaperMatchJobHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new PaperMatchJobHandler(orderService, marketReader, executionService,
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void missingNPlusOneClosedBarDefersWithoutProducingAnExecution() throws Exception {
        PaperMatchJobInput input = input();
        when(orderService.findMatchCandidate(41L)).thenReturn(Optional.of(candidate(OrderType.MARKET, null, null)));
        when(marketReader.closedBars(1L, 501L, ELIGIBLE, NOW)).thenReturn(List.of());

        var result = handler.execute(claim(objectMapper.writeValueAsString(input)));

        assertThat(result.deferred()).isTrue();
        assertThat(result.nextAvailableAt()).isEqualTo(NOW.plusSeconds(60));
        verify(executionService, never()).execute(any(), any(), any());
    }

    @Test
    void gtcLimitThatHasNotCrossedDefersAndKeepsTheOrderPending() throws Exception {
        when(orderService.findMatchCandidate(41L)).thenReturn(Optional.of(
                candidate(OrderType.LIMIT, new BigDecimal("90"), null)));
        when(marketReader.closedBars(1L, 501L, ELIGIBLE, NOW)).thenReturn(List.of(
                bar("100", "105", "95", "101", ELIGIBLE),
                bar("102", "106", "94", "103", ELIGIBLE.plusSeconds(60))));

        var result = handler.execute(claim(objectMapper.writeValueAsString(input())));

        assertThat(result.deferred()).isTrue();
        verify(executionService, never()).execute(any(), any(), any());
    }

    @Test
    void marketOrderUsesTheFirstEligibleClosedBarAndDelegatesOneFinancialTransaction() throws Exception {
        when(orderService.findMatchCandidate(41L)).thenReturn(Optional.of(candidate(OrderType.MARKET, null, null)));
        FuturesBar first = bar("100", "105", "95", "101", ELIGIBLE);
        when(marketReader.closedBars(1L, 501L, ELIGIBLE, NOW)).thenReturn(List.of(
                first, bar("110", "115", "105", "111", ELIGIBLE.plusSeconds(60))));
        when(executionService.execute(any(), any(), org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(new PaperExecutionResult(41L, "FILLED", null, false));

        var result = handler.execute(claim(objectMapper.writeValueAsString(input())));

        assertThat(result.deferred()).isFalse();
        ArgumentCaptor<MatchResult> match = ArgumentCaptor.forClass(MatchResult.class);
        verify(executionService).execute(org.mockito.ArgumentMatchers.eq(input()), match.capture(),
                org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(match.getValue().marketBarOpenTime()).isEqualTo(first.openTime());
        assertThat(match.getValue().fillPrice()).isEqualByComparingTo("100.1");
    }

    @Test
    void expiredPendingOrderTerminatesWithoutReadingMarketBars() throws Exception {
        when(orderService.findMatchCandidate(41L)).thenReturn(Optional.of(
                candidate(OrderType.MARKET, null, NOW.minusSeconds(1))));
        when(executionService.expire(input(), NOW)).thenReturn("EXPIRED");

        var result = handler.execute(claim(objectMapper.writeValueAsString(input())));

        assertThat(result.deferred()).isFalse();
        assertThat(result.resultJson()).contains("EXPIRED");
        verify(marketReader, never()).closedBars(anyLong(), anyLong(), any(), any());
    }

    private static PaperOrderService.MatchCandidate candidate(
            OrderType orderType, BigDecimal limitPrice, Instant expiresAt) {
        return new PaperOrderService.MatchCandidate(
                41L, 11L, 21L, 501L, "PENDING_MATCH", orderType,
                PositionSide.LONG, PositionAction.OPEN, BigDecimal.ONE,
                limitPrice, ELIGIBLE, expiresAt);
    }

    private static PaperMatchJobInput input() {
        return new PaperMatchJobInput(
                41L, 11L, 21L, 501L, 1L, ELIGIBLE,
                new BigDecimal("0.1"), new BigDecimal("0.001"), BigDecimal.ONE,
                new BigDecimal("0.0002"), new BigDecimal("0.0006"),
                new BigDecimal("10"), new BigDecimal("0.005"));
    }

    private static FuturesBar bar(
            String open, String high, String low, String close, Instant openTime) {
        return new FuturesBar(openTime, openTime.plusSeconds(60),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low),
                new BigDecimal(close), true);
    }

    private static InvestmentJobClaim claim(String inputJson) {
        return new InvestmentJobClaim(
                91L, 11L, InvestmentJobType.PAPER_MATCH, inputJson, 0, 5,
                "worker", "token", NOW.minusSeconds(1), NOW.plusSeconds(30));
    }
}
