package top.egon.mario.investment.quant.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.quant.backtest.model.BacktestEvent;
import top.egon.mario.investment.quant.backtest.model.BacktestResult;
import top.egon.mario.investment.quant.backtest.model.BacktestTrade;
import top.egon.mario.investment.quant.po.InvestmentBacktestEventPo;
import top.egon.mario.investment.quant.po.InvestmentBacktestTradePo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestEventRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestTradeRepository;
import top.egon.mario.investment.quant.repository.jdbc.BacktestEquityJdbcRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class BacktestResultPersistenceService {

    private final InvestmentBacktestTradeRepository tradeRepository;
    private final InvestmentBacktestEventRepository eventRepository;
    private final BacktestEquityJdbcRepository equityRepository;
    private final BacktestEquityPointSelector equityPointSelector;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BacktestResultPersistenceService(InvestmentBacktestTradeRepository tradeRepository,
                                            InvestmentBacktestEventRepository eventRepository,
                                            BacktestEquityJdbcRepository equityRepository,
                                            BacktestEquityPointSelector equityPointSelector,
                                            ObjectMapper objectMapper, Clock clock) {
        this.tradeRepository = tradeRepository;
        this.eventRepository = eventRepository;
        this.equityRepository = equityRepository;
        this.equityPointSelector = equityPointSelector;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void persist(long runId, BacktestResult result) {
        if (tradeRepository.existsByRunId(runId) || eventRepository.existsByRunId(runId)
                || equityRepository.existsByRunId(runId)) {
            return;
        }
        Instant createdAt = clock.instant();
        tradeRepository.saveAll(result.trades().stream().map(value -> trade(runId, value, createdAt)).toList());
        eventRepository.saveAll(result.events().stream().map(value -> event(runId, value, createdAt)).toList());
        equityRepository.saveAll(runId, equityPointSelector.select(result.equityPoints()), createdAt);
    }

    private InvestmentBacktestTradePo trade(long runId, BacktestTrade value, Instant createdAt) {
        InvestmentBacktestTradePo po = new InvestmentBacktestTradePo();
        po.setRunId(runId);
        po.setInstrumentId(value.instrumentId());
        po.setPositionSide(value.positionSide().name());
        po.setEntryTime(value.entryTime());
        po.setExitTime(value.exitTime());
        po.setEntryPrice(value.entryPrice());
        po.setExitPrice(value.exitPrice());
        po.setQuantity(value.quantity());
        po.setLeverage(value.leverage());
        po.setGrossPnl(value.grossPnl());
        po.setFeeAmount(value.feeAmount());
        po.setFundingAmount(value.fundingAmount());
        po.setNetPnl(value.netPnl());
        po.setExitReason(value.exitReason());
        po.setCreatedAt(createdAt);
        return po;
    }

    private InvestmentBacktestEventPo event(long runId, BacktestEvent value, Instant createdAt) {
        InvestmentBacktestEventPo po = new InvestmentBacktestEventPo();
        po.setRunId(runId);
        po.setInstrumentId(value.instrumentId());
        po.setEventType(value.eventType());
        po.setEventTime(value.eventTime());
        po.setAmount(value.amount());
        po.setBalanceAfter(value.balanceAfter());
        po.setDetailsJson(json(value.details()));
        po.setSequenceNo(value.sequenceNo());
        po.setCreatedAt(createdAt);
        return po;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize backtest event", exception);
        }
    }
}
