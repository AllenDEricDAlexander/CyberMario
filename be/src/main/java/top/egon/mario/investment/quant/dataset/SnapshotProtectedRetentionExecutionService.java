package top.egon.mario.investment.quant.dataset;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.service.MarketDataRetentionCandidateService.RetentionCandidate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Deletes one bounded intraday batch only after checking each row against snapshot protection.
 */
@Service
public class SnapshotProtectedRetentionExecutionService {

    private static final int MAX_BATCH_SIZE = 2_000;

    private final InvestmentDataSourceRepository sourceRepository;
    private final InvestmentInstrumentSourceRepository instrumentSourceRepository;
    private final SnapshotRetentionProtectionService protectionService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SnapshotProtectedRetentionExecutionService(InvestmentDataSourceRepository sourceRepository,
                                                       InvestmentInstrumentSourceRepository instrumentSourceRepository,
                                                       SnapshotRetentionProtectionService protectionService,
                                                       NamedParameterJdbcTemplate jdbcTemplate) {
        this.sourceRepository = sourceRepository;
        this.instrumentSourceRepository = instrumentSourceRepository;
        this.protectionService = protectionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RetentionExecutionResult execute(RetentionCandidate candidate, int requestedBatchSize) {
        if (!candidate.physicalDeletionEnabled()) {
            return new RetentionExecutionResult(0, 0);
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        InvestmentDataSourcePo source = sourceRepository.findByCodeAndDeletedFalse(candidate.sourceCode())
                .orElseThrow(() -> new IllegalStateException("Retention source is not persisted"));
        InvestmentInstrumentSourcePo mapping = instrumentSourceRepository
                .findBySourceIdAndExternalProductTypeAndExternalSymbolAndDeletedFalse(
                        source.getId(), candidate.productType(), candidate.symbol())
                .orElseThrow(() -> new IllegalStateException("Retention instrument mapping is not persisted"));
        MapSqlParameterSource parameters = baseParameters(candidate, source.getId(), mapping.getInstrumentId())
                .addValue("limit", batchSize);
        List<RetentionRow> rows = jdbcTemplate.query("""
                select bar.open_time, bar.close_time, bar.revision
                from investment_market_bar_intraday bar
                where bar.source_id = :sourceId and bar.instrument_id = :instrumentId
                  and bar.price_type = :priceType and bar.interval_code = :intervalCode
                  and bar.open_time >= :fromInclusive and bar.close_time <= :toExclusive
                  and not exists (
                    select 1
                    from investment_dataset_snapshot_item item
                    join investment_dataset_snapshot snapshot on snapshot.id = item.snapshot_id
                    where snapshot.source_id = bar.source_id
                      and item.instrument_id = bar.instrument_id
                      and item.data_type = 'BAR_INTRADAY'
                      and item.price_type = bar.price_type
                      and item.interval_code = bar.interval_code
                      and item.first_time < bar.close_time
                      and item.last_time >= bar.open_time
                      and (snapshot.quality_status = 'PENDING'
                           or (snapshot.quality_status = 'VERIFIED'
                               and (snapshot.artifact_uri is null or snapshot.artifact_uri = '')))
                  )
                order by bar.open_time asc, bar.revision asc
                limit :limit
                """, parameters, (resultSet, rowNumber) -> new RetentionRow(
                resultSet.getTimestamp("open_time").toInstant(),
                resultSet.getTimestamp("close_time").toInstant(),
                resultSet.getLong("revision")));
        int deleted = 0;
        int protectedRows = 0;
        for (RetentionRow row : rows) {
            if (protectionService.isProtected(source.getId(), mapping.getInstrumentId(), candidate.priceType(),
                    candidate.interval(), row.openTime(), row.closeTime())) {
                protectedRows++;
                continue;
            }
            MapSqlParameterSource key = baseParameters(candidate, source.getId(), mapping.getInstrumentId())
                    .addValue("openTime", Timestamp.from(row.openTime()))
                    .addValue("revision", row.revision());
            deleted += jdbcTemplate.update("""
                    delete from investment_market_bar_intraday
                    where source_id = :sourceId and instrument_id = :instrumentId
                      and price_type = :priceType and interval_code = :intervalCode
                      and open_time = :openTime and revision = :revision
                    """, key);
        }
        return new RetentionExecutionResult(deleted, protectedRows);
    }

    private static MapSqlParameterSource baseParameters(RetentionCandidate candidate,
                                                        long sourceId, long instrumentId) {
        return new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId)
                .addValue("priceType", candidate.priceType().name())
                .addValue("intervalCode", candidate.interval().name())
                .addValue("fromInclusive", Timestamp.from(candidate.fromInclusive()))
                .addValue("toExclusive", Timestamp.from(candidate.toExclusive()));
    }

    public record RetentionExecutionResult(int deletedRows, int protectedRows) {
    }

    private record RetentionRow(Instant openTime, Instant closeTime, long revision) {
    }
}
