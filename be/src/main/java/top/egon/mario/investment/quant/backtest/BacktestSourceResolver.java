package top.egon.mario.investment.quant.backtest;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the single server-managed source currently normalized for all requested instruments.
 */
@Component
public class BacktestSourceResolver {

    private final InvestmentContractSpecRepository repository;

    public BacktestSourceResolver(InvestmentContractSpecRepository repository) {
        this.repository = repository;
    }

    public long resolve(Set<Long> instrumentIds) {
        List<InvestmentContractSpecPo> specifications = repository.findAllById(instrumentIds);
        Set<Long> resolvedInstruments = specifications.stream()
                .map(InvestmentContractSpecPo::getInstrumentId).collect(Collectors.toUnmodifiableSet());
        if (!resolvedInstruments.equals(instrumentIds)) {
            throw new IllegalStateException("A requested instrument has no normalized contract specification");
        }
        Set<Long> sourceIds = specifications.stream().map(InvestmentContractSpecPo::getSourceId)
                .collect(Collectors.toUnmodifiableSet());
        if (sourceIds.size() != 1) {
            throw new IllegalStateException("Backtest instruments must share one server-managed data source");
        }
        long sourceId = sourceIds.iterator().next();
        if (sourceId <= 0) {
            throw new IllegalStateException("Backtest instruments must share one server-managed data source");
        }
        return sourceId;
    }
}
