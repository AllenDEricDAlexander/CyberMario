package top.egon.mario.investment.quant.dataset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotRepository;

import java.util.List;

/**
 * Keeps the immutable manifest and its item hashes in one short write transaction.
 */
@Service
class DatasetSnapshotPersistenceService {

    private final InvestmentDatasetSnapshotRepository snapshotRepository;
    private final InvestmentDatasetSnapshotItemRepository itemRepository;

    DatasetSnapshotPersistenceService(InvestmentDatasetSnapshotRepository snapshotRepository,
                                      InvestmentDatasetSnapshotItemRepository itemRepository) {
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public InvestmentDatasetSnapshotService.DatasetSnapshot persist(
            InvestmentDatasetSnapshotPo snapshot, List<InvestmentDatasetSnapshotItemPo> items) {
        InvestmentDatasetSnapshotPo saved = snapshotRepository.saveAndFlush(snapshot);
        items.forEach(item -> item.setSnapshotId(saved.getId()));
        List<InvestmentDatasetSnapshotItemPo> savedItems = itemRepository.saveAllAndFlush(items);
        return new InvestmentDatasetSnapshotService.DatasetSnapshot(saved, List.copyOf(savedItems));
    }
}
