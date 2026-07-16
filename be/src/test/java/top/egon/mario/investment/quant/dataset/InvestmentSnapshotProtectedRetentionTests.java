package top.egon.mario.investment.quant.dataset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.service.MarketDataRetentionCandidateService.RetentionCandidate;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotItemRepository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class InvestmentSnapshotProtectedRetentionTests {

    private static final Instant OPEN = Instant.parse("2020-01-01T00:00:00Z");

    private InvestmentDatasetSnapshotItemRepository itemRepository;
    private SnapshotRetentionProtectionService protectionService;
    private InvestmentDataSourceRepository sourceRepository;
    private InvestmentInstrumentSourceRepository instrumentSourceRepository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcTemplate actualJdbcTemplate;

    @Autowired
    private SnapshotRetentionProtectionService actualProtectionService;

    @Autowired
    private SnapshotProtectedRetentionExecutionService actualExecutionService;

    @BeforeEach
    void setUp() {
        itemRepository = mock(InvestmentDatasetSnapshotItemRepository.class);
        protectionService = new SnapshotRetentionProtectionService(itemRepository);
        sourceRepository = mock(InvestmentDataSourceRepository.class);
        instrumentSourceRepository = mock(InvestmentInstrumentSourceRepository.class);
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    }

    @Test
    void pendingDatabaseSnapshotProtectsRangeWhileArchivedOrExpiredStateDoesNot() {
        when(itemRepository.existsProtectedIntradayRange(
                3L, 11L, "MARK", "M1", OPEN, OPEN.plusSeconds(60)))
                .thenReturn(true, false, false);

        assertThat(protectionService.isProtected(
                3L, 11L, PriceType.MARK, BarInterval.M1, OPEN, OPEN.plusSeconds(60))).isTrue();
        assertThat(protectionService.isProtected(
                3L, 11L, PriceType.MARK, BarInterval.M1, OPEN, OPEN.plusSeconds(60))).isFalse();
        assertThat(protectionService.isProtected(
                3L, 11L, PriceType.MARK, BarInterval.M1, OPEN, OPEN.plusSeconds(60))).isFalse();
    }

    @Test
    void skipsReferencedRowsAndDeletesOnlyUnprotectedRowsInABoundedBatch() throws Exception {
        SnapshotRetentionProtectionService protection = mock(SnapshotRetentionProtectionService.class);
        SnapshotProtectedRetentionExecutionService service = new SnapshotProtectedRetentionExecutionService(
                sourceRepository, instrumentSourceRepository, protection, jdbcTemplate);
        seedMapping();
        mockOneRetentionRow();
        when(protection.isProtected(3L, 11L, PriceType.MARK, BarInterval.M1,
                OPEN, OPEN.plusSeconds(60))).thenReturn(true, false);

        var protectedResult = service.execute(candidate(true), 100);
        var deletedResult = service.execute(candidate(true), 100);

        assertThat(protectedResult.protectedRows()).isEqualTo(1);
        assertThat(protectedResult.deletedRows()).isZero();
        assertThat(deletedResult.protectedRows()).isZero();
        assertThat(deletedResult.deletedRows()).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void disabledCandidateCannotReachCatalogOrDeletion() {
        SnapshotProtectedRetentionExecutionService service = new SnapshotProtectedRetentionExecutionService(
                sourceRepository, instrumentSourceRepository, protectionService, jdbcTemplate);

        assertThat(service.execute(candidate(false), 100).deletedRows()).isZero();
        verify(sourceRepository, never()).findByCodeAndDeletedFalse(anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void verifiedArtifactReleasesActualDatabaseRangeForPhysicalDeletion() {
        long[] ids = seedActualDatabaseRange();
        long sourceId = ids[0];
        long instrumentId = ids[1];
        long snapshotId = ids[2];

        assertThat(actualProtectionService.isProtected(sourceId, instrumentId,
                PriceType.MARK, BarInterval.M1, OPEN, OPEN.plusSeconds(60))).isTrue();
        assertThat(actualExecutionService.execute(candidate(true), 100).deletedRows()).isZero();
        assertThat(actualJdbcTemplate.queryForObject(
                "select count(*) from investment_market_bar_intraday where instrument_id = ?",
                Integer.class, instrumentId)).isEqualTo(1);

        actualJdbcTemplate.update("""
                update investment_dataset_snapshot
                set quality_status = 'VERIFIED', artifact_uri = 's3://verified/dataset.json'
                where id = ?
                """, snapshotId);

        assertThat(actualProtectionService.isProtected(sourceId, instrumentId,
                PriceType.MARK, BarInterval.M1, OPEN, OPEN.plusSeconds(60))).isFalse();
        assertThat(actualExecutionService.execute(candidate(true), 100).deletedRows()).isEqualTo(1);
        assertThat(actualJdbcTemplate.queryForObject(
                "select count(*) from investment_market_bar_intraday where instrument_id = ?",
                Integer.class, instrumentId)).isZero();
    }

    private void seedMapping() {
        InvestmentDataSourcePo source = new InvestmentDataSourcePo();
        source.setId(3L);
        InvestmentInstrumentSourcePo mapping = new InvestmentInstrumentSourcePo();
        mapping.setInstrumentId(11L);
        when(sourceRepository.findByCodeAndDeletedFalse("TEST")).thenReturn(Optional.of(source));
        when(instrumentSourceRepository
                .findBySourceIdAndExternalProductTypeAndExternalSymbolAndDeletedFalse(
                        3L, "USDT_FUTURES", "BTCUSDT"))
                .thenReturn(Optional.of(mapping));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockOneRetentionRow() throws Exception {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(2);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getTimestamp("open_time")).thenReturn(Timestamp.from(OPEN));
                    when(resultSet.getTimestamp("close_time")).thenReturn(Timestamp.from(OPEN.plusSeconds(60)));
                    when(resultSet.getLong("revision")).thenReturn(1L);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
    }

    private RetentionCandidate candidate(boolean enabled) {
        return new RetentionCandidate("TEST", "USDT_FUTURES", "BTCUSDT", PriceType.MARK, BarInterval.M1,
                Instant.EPOCH, Instant.parse("2024-01-01T00:00:00Z"), enabled);
    }

    private long[] seedActualDatabaseRange() {
        String suffix = Long.toString(System.nanoTime());
        actualJdbcTemplate.update(
                "insert into investment_venue(code, name, status) values (?, 'Retention Test', 'ACTIVE')",
                "RETENTION_" + suffix);
        long venueId = actualJdbcTemplate.queryForObject(
                "select id from investment_venue where code = ?", Long.class, "RETENTION_" + suffix);
        actualJdbcTemplate.update("""
                insert into investment_data_source(
                    venue_id, code, provider_type, api_family, product_type, rate_limit_per_second, status
                ) values (?, 'TEST', 'TEST', 'V1', 'USDT_FUTURES', 10, 'ACTIVE')
                """, venueId);
        long sourceId = actualJdbcTemplate.queryForObject(
                "select id from investment_data_source where code = 'TEST'", Long.class);
        actualJdbcTemplate.update("""
                insert into investment_instrument(
                    venue_id, market_type, product_type, contract_type, symbol,
                    base_asset, quote_asset, settlement_asset, margin_asset, status
                ) values (?, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', 'BTCUSDT',
                    'BTC', 'USDT', 'USDT', 'USDT', 'ACTIVE')
                """, venueId);
        long instrumentId = actualJdbcTemplate.queryForObject(
                "select id from investment_instrument where venue_id = ? and symbol = 'BTCUSDT'",
                Long.class, venueId);
        actualJdbcTemplate.update("""
                insert into investment_instrument_source(
                    instrument_id, source_id, external_symbol, external_product_type, source_status
                ) values (?, ?, 'BTCUSDT', 'USDT_FUTURES', 'ACTIVE')
                """, instrumentId, sourceId);
        actualJdbcTemplate.update("""
                insert into investment_workspace(owner_user_id, name)
                values (9001, ?)
                """, "Retention " + suffix);
        long workspaceId = actualJdbcTemplate.queryForObject(
                "select id from investment_workspace where name = ?", Long.class, "Retention " + suffix);
        actualJdbcTemplate.update("""
                insert into investment_market_bar_intraday(
                    source_id, instrument_id, price_type, interval_code, open_time, close_time,
                    open_price, high_price, low_price, close_price, base_volume, quote_volume,
                    is_closed, ingested_at, revision, revision_slot, valid_from, checksum
                ) values (?, ?, 'MARK', 'M1', ?, ?, 100, 110, 90, 105, 1, 105,
                    true, ?, 1, 0, ?, 'retention-bar')
                """, sourceId, instrumentId, Timestamp.from(OPEN), Timestamp.from(OPEN.plusSeconds(60)),
                Timestamp.from(OPEN.plusSeconds(61)), Timestamp.from(OPEN.minusSeconds(1)));
        String hash = "a".repeat(64);
        actualJdbcTemplate.update("""
                insert into investment_dataset_snapshot(
                    workspace_id, source_id, start_time, end_time, data_as_of,
                    contract_spec_hash, position_tier_hash, funding_data_hash, dataset_hash,
                    quality_status, created_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 9001)
                """, workspaceId, sourceId, Timestamp.from(OPEN), Timestamp.from(OPEN.plusSeconds(60)),
                Timestamp.from(OPEN.plusSeconds(120)), hash, hash, hash, hash);
        long snapshotId = actualJdbcTemplate.queryForObject(
                "select id from investment_dataset_snapshot where workspace_id = ?", Long.class, workspaceId);
        actualJdbcTemplate.update("""
                insert into investment_dataset_snapshot_item(
                    snapshot_id, instrument_id, data_type, price_type, interval_code,
                    first_time, last_time, row_count, max_revision, data_hash
                ) values (?, ?, 'BAR_INTRADAY', 'MARK', 'M1', ?, ?, 1, 1, ?)
                """, snapshotId, instrumentId, Timestamp.from(OPEN), Timestamp.from(OPEN), hash);
        return new long[]{sourceId, instrumentId, snapshotId};
    }
}
