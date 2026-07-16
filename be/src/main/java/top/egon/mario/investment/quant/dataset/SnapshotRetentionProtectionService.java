package top.egon.mario.investment.quant.dataset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;

import java.time.Instant;

/**
 * Answers whether physical retention would make a database-backed snapshot unreplayable.
 */
@Service
public class SnapshotRetentionProtectionService {

    private final InvestmentDatasetSnapshotItemRepository itemRepository;

    public SnapshotRetentionProtectionService(InvestmentDatasetSnapshotItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public boolean isProtected(long sourceId, long instrumentId, PriceType priceType, BarInterval interval,
                               Instant fromInclusive, Instant toExclusive) {
        return itemRepository.existsProtectedIntradayRange(sourceId, instrumentId, priceType.name(), interval.name(),
                fromInclusive, toExclusive);
    }
}
