package top.egon.mario.investment.agent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.service.InvestmentAgentDecisionExecutionService;
import top.egon.mario.investment.agent.service.InvestmentAgentRunService;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.PaperTradingFacade;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentAgentDecisionExecutionRecoveryTests {

    @Test
    void retriesFacadeWithTheSameKeyAfterCrashBetweenFacadeCommitAndIntentLink() {
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentAgentRunService runService = mock(InvestmentAgentRunService.class);
        InvestmentAgentDecisionRepository decisionRepository = mock(InvestmentAgentDecisionRepository.class);
        PaperTradingFacade facade = mock(PaperTradingFacade.class);
        InvestmentAgentDecisionExecutionService service = new InvestmentAgentDecisionExecutionService(
                runService, decisionRepository, mock(InvestmentPositionRepository.class), facade,
                Clock.fixed(cutoff.plusSeconds(30), ZoneOffset.UTC));
        InvestmentAgentDecisionPo pending = pending(cutoff);
        InvestmentAgentDecisionPo submitted = pending(cutoff);
        submitted.setIntentId(71L);
        submitted.setExecutionStatus(InvestmentAgentExecutionStatus.SUBMITTED);
        when(runService.requireDecision(51L)).thenReturn(pending);
        when(runService.input(41L)).thenReturn(new InvestmentAgentRunInput(
                5L, 7L, 31L, InvestmentAgentRunType.AUTO_TRADE, List.of(11L), cutoff));
        PaperTradeResult result = new PaperTradeResult(
                71L, "ACCEPTED", List.of(),
                new PaperOrderSummary(81L, "PENDING_MATCH", cutoff, null), null);
        when(facade.submitIntent(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        when(runService.linkIntent(51L, 71L))
                .thenThrow(new IllegalStateException("synthetic crash after facade commit"))
                .thenReturn(submitted);

        assertThatThrownBy(() -> service.execute(51L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic crash");
        InvestmentAgentDecisionExecutionService.ExecutionOutcome recovered = service.execute(51L);

        assertThat(recovered.intentId()).isEqualTo(71L);
        assertThat(recovered.executionStatus()).isEqualTo(InvestmentAgentExecutionStatus.SUBMITTED);
        ArgumentCaptor<PaperTradeCommand> commands = ArgumentCaptor.forClass(PaperTradeCommand.class);
        verify(facade, times(2)).submitIntent(commands.capture());
        assertThat(commands.getAllValues()).extracting(PaperTradeCommand::idempotencyKey)
                .containsExactly("agent-exec:stable", "agent-exec:stable");
        assertThat(commands.getAllValues()).extracting(PaperTradeCommand::accountId)
                .containsOnly(31L);
        verify(runService, times(2)).linkIntent(51L, 71L);
    }

    @Test
    void riskRejectedIntentIsLinkedOnceWithoutInventingAnOrder() {
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentAgentRunService runService = mock(InvestmentAgentRunService.class);
        PaperTradingFacade facade = mock(PaperTradingFacade.class);
        InvestmentAgentDecisionExecutionService service = new InvestmentAgentDecisionExecutionService(
                runService, mock(InvestmentAgentDecisionRepository.class),
                mock(InvestmentPositionRepository.class), facade,
                Clock.fixed(cutoff.plusSeconds(30), ZoneOffset.UTC));
        InvestmentAgentDecisionPo pending = pending(cutoff);
        InvestmentAgentDecisionPo submitted = pending(cutoff);
        submitted.setIntentId(71L);
        submitted.setExecutionStatus(InvestmentAgentExecutionStatus.SUBMITTED);
        when(runService.requireDecision(51L)).thenReturn(pending);
        when(runService.input(41L)).thenReturn(new InvestmentAgentRunInput(
                5L, 7L, 31L, InvestmentAgentRunType.AUTO_TRADE, List.of(11L), cutoff));
        when(facade.submitIntent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaperTradeResult(71L, "RISK_REJECTED", List.of(), null, null));
        when(runService.linkIntent(51L, 71L)).thenReturn(submitted);

        var outcome = service.execute(51L);

        assertThat(outcome.intentId()).isEqualTo(71L);
        assertThat(outcome.tradeResult().intentStatus()).isEqualTo("RISK_REJECTED");
        assertThat(outcome.tradeResult().order()).isNull();
        verify(facade).submitIntent(org.mockito.ArgumentMatchers.any());
        verify(runService).linkIntent(51L, 71L);
    }

    @Test
    void rejectsARecoveredDecisionOutsideItsPersistedInstrumentScope() {
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentAgentRunService runService = mock(InvestmentAgentRunService.class);
        PaperTradingFacade facade = mock(PaperTradingFacade.class);
        InvestmentAgentDecisionExecutionService service = new InvestmentAgentDecisionExecutionService(
                runService, mock(InvestmentAgentDecisionRepository.class),
                mock(InvestmentPositionRepository.class), facade,
                Clock.fixed(cutoff.plusSeconds(30), ZoneOffset.UTC));
        when(runService.requireDecision(51L)).thenReturn(pending(cutoff));
        when(runService.input(41L)).thenReturn(new InvestmentAgentRunInput(
                5L, 7L, 31L, InvestmentAgentRunType.AUTO_TRADE, List.of(12L), cutoff));

        assertThatThrownBy(() -> service.execute(51L))
                .isInstanceOfSatisfying(InvestmentException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(InvestmentErrorCode.FORBIDDEN.code()));
        verify(facade, never()).submitIntent(org.mockito.ArgumentMatchers.any(PaperTradeCommand.class));
    }

    private static InvestmentAgentDecisionPo pending(Instant cutoff) {
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setId(51L);
        decision.setRunId(41L);
        decision.setInstrumentId(11L);
        decision.setAction(InvestmentAgentAction.OPEN_LONG);
        decision.setExecutionStatus(InvestmentAgentExecutionStatus.PENDING);
        decision.setExecutionIdempotencyKey("agent-exec:stable");
        decision.setOrderType(OrderType.MARKET);
        decision.setRequestedQuantity(BigDecimal.ONE);
        decision.setRequestedNotional(new BigDecimal("100"));
        decision.setRequestedLeverage(new BigDecimal("2"));
        decision.setThesis("trend");
        decision.setDataAsOf(cutoff);
        return decision;
    }
}
