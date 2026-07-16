package top.egon.mario.investment.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.job.PaperMaintenanceJobPlanner;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperMaintenanceJobPlannerTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:05:00Z");
    private static final Instant FUNDING = Instant.parse("2026-07-17T00:00:00Z");

    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentInstrumentSourceRepository sourceRepository;
    @Mock private InvestmentPaperOrderRepository orderRepository;
    @Mock private FundingRateJdbcRepository fundingRepository;
    @Mock private InvestmentJobEnqueueService enqueueService;

    private PaperMaintenanceJobPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new PaperMaintenanceJobPlanner(
                true, Duration.ofSeconds(30), positionRepository, accountRepository,
                sourceRepository, orderRepository, fundingRepository, enqueueService,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void restartTickCreatesTheFirstMarginAndDueFundingJobsOnlyForOpenPositions() {
        InvestmentPositionPo position = position();
        InvestmentPaperAccountPo account = account();
        InvestmentInstrumentSourcePo source = source();
        when(positionRepository.findAll()).thenReturn(List.of(position));
        when(accountRepository.findAllById(List.of(21L))).thenReturn(List.of(account));
        when(sourceRepository.findFirstByInstrumentIdAndSourceStatusAndDeletedFalseOrderByIdAsc(501L, "ACTIVE"))
                .thenReturn(Optional.of(source));
        when(fundingRepository.findCurrent(1L, 501L, position.getCreatedAt(), NOW.plusNanos(1), 0, 1000))
                .thenReturn(List.of(new FundingRateRow(
                        1L, 501L, FUNDING, new BigDecimal("0.001"), FUNDING, 1L,
                        FUNDING, null, "hash")));

        assertThat(planner.tick()).isEqualTo(2);

        ArgumentCaptor<InvestmentJobEnqueueCommand> command =
                ArgumentCaptor.forClass(InvestmentJobEnqueueCommand.class);
        verify(enqueueService, times(2)).enqueueOrWake(command.capture());
        assertThat(command.getAllValues()).extracting(InvestmentJobEnqueueCommand::jobType)
                .containsExactly(InvestmentJobType.PAPER_MARGIN_CHECK, InvestmentJobType.PAPER_FUNDING_SETTLE);
        assertThat(command.getAllValues().get(1).idempotencyKey())
                .isEqualTo("funding:21:71:501:" + FUNDING.toEpochMilli());
    }

    @Test
    void accountsWithoutAnOpenPositionProduceNoMaintenanceTask() {
        when(positionRepository.findAll()).thenReturn(List.of());
        when(accountRepository.findAllById(List.of())).thenReturn(List.of());

        assertThat(planner.tick()).isZero();

        verify(enqueueService, never()).enqueueOrWake(any());
    }

    @Test
    void repeatedMarketCommitUsesTheSameMarginKeyAndWakesPendingMatches() {
        InvestmentPositionPo position = position();
        InvestmentPaperAccountPo account = account();
        InvestmentPaperOrderPo order = new InvestmentPaperOrderPo();
        order.setId(41L);
        when(positionRepository.findByInstrumentIdOrderByAccountIdAscInstrumentIdAsc(501L))
                .thenReturn(List.of(position));
        when(accountRepository.findAllById(List.of(21L))).thenReturn(List.of(account));
        when(orderRepository.findByInstrumentIdAndStatusAndDeletedFalseOrderByIdAsc(501L, "PENDING_MATCH"))
                .thenReturn(List.of(order));
        when(enqueueService.wakePending("paper-match:41", NOW)).thenReturn(true);
        InvestmentMarketDataCommittedEvent event =
                new InvestmentMarketDataCommittedEvent(1L, 501L, "BAR", 1, NOW);

        planner.onMarketDataCommitted(event);
        planner.onMarketDataCommitted(event);

        ArgumentCaptor<InvestmentJobEnqueueCommand> command =
                ArgumentCaptor.forClass(InvestmentJobEnqueueCommand.class);
        verify(enqueueService, times(2)).enqueueOrWake(command.capture());
        assertThat(command.getAllValues()).extracting(InvestmentJobEnqueueCommand::idempotencyKey)
                .containsOnly("paper-margin:21:501:" + NOW.getEpochSecond() / 60L);
        verify(enqueueService, times(2)).wakePending("paper-match:41", NOW);
    }

    private static InvestmentPositionPo position() {
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setId(71L);
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setCreatedAt(NOW.minus(Duration.ofHours(12)));
        return position;
    }

    private static InvestmentPaperAccountPo account() {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        return account;
    }

    private static InvestmentInstrumentSourcePo source() {
        InvestmentInstrumentSourcePo source = new InvestmentInstrumentSourcePo();
        source.setId(1L);
        source.setSourceId(1L);
        source.setInstrumentId(501L);
        return source;
    }
}
