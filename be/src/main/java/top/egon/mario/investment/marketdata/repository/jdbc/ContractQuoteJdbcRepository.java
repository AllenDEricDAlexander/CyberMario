package top.egon.mario.investment.marketdata.repository.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteWrite;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC-only latest quote adapter with lexicographically monotonic batch upserts.
 */
@Repository
public class ContractQuoteJdbcRepository {

    private static final String COLUMNS = """
            source_id, instrument_id, last_price, mark_price, index_price, bid_price, ask_price,
            bid_quantity, ask_quantity, open_24h, high_24h, low_24h, base_volume_24h,
            quote_volume_24h, change_24h, funding_rate, next_funding_time, open_interest,
            source_time, received_at, version
            """;

    private static final String INSERT = """
            insert into investment_contract_quote_latest (
                source_id, instrument_id, last_price, mark_price, index_price, bid_price, ask_price,
                bid_quantity, ask_quantity, open_24h, high_24h, low_24h, base_volume_24h,
                quote_volume_24h, change_24h, funding_rate, next_funding_time, open_interest,
                source_time, received_at, version
            ) values (
                :sourceId, :instrumentId, :lastPrice, :markPrice, :indexPrice, :bidPrice, :askPrice,
                :bidQuantity, :askQuantity, :open24h, :high24h, :low24h, :baseVolume24h,
                :quoteVolume24h, :change24h, :fundingRate, :nextFundingTime, :openInterest,
                :sourceTime, :receivedAt, 0
            )
            """;

    private static final String UPDATE = """
            update investment_contract_quote_latest
            set last_price = :lastPrice, mark_price = :markPrice, index_price = :indexPrice,
                bid_price = :bidPrice, ask_price = :askPrice, bid_quantity = :bidQuantity,
                ask_quantity = :askQuantity, open_24h = :open24h, high_24h = :high24h,
                low_24h = :low24h, base_volume_24h = :baseVolume24h,
                quote_volume_24h = :quoteVolume24h, change_24h = :change24h,
                funding_rate = :fundingRate, next_funding_time = :nextFundingTime,
                open_interest = :openInterest, source_time = :sourceTime, received_at = :receivedAt,
                version = version + 1
            where source_id = :sourceId and instrument_id = :instrumentId
              and (source_time < :sourceTime
                or (source_time = :sourceTime and received_at < :receivedAt))
            """;

    private static final String POSTGRES_UPSERT = INSERT + """
            on conflict (source_id, instrument_id) do update set
                last_price = excluded.last_price, mark_price = excluded.mark_price,
                index_price = excluded.index_price, bid_price = excluded.bid_price,
                ask_price = excluded.ask_price, bid_quantity = excluded.bid_quantity,
                ask_quantity = excluded.ask_quantity, open_24h = excluded.open_24h,
                high_24h = excluded.high_24h, low_24h = excluded.low_24h,
                base_volume_24h = excluded.base_volume_24h,
                quote_volume_24h = excluded.quote_volume_24h,
                change_24h = excluded.change_24h, funding_rate = excluded.funding_rate,
                next_funding_time = excluded.next_funding_time,
                open_interest = excluded.open_interest, source_time = excluded.source_time,
                received_at = excluded.received_at,
                version = investment_contract_quote_latest.version + 1
            where (excluded.source_time, excluded.received_at)
                > (investment_contract_quote_latest.source_time,
                   investment_contract_quote_latest.received_at)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public ContractQuoteJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = isPostgres(dataSource);
    }

    @Transactional
    public int writeLatest(ContractQuoteWrite value) {
        return writeLatestBatch(List.of(value));
    }

    /**
     * Upserts a provider page using strict source-time then receive-time ordering.
     */
    @Transactional
    public int writeLatestBatch(List<ContractQuoteWrite> values) {
        List<ContractQuoteWrite> validated = validate(values);
        SqlParameterSource[] parameters = validated.stream().map(this::parameters)
                .toArray(SqlParameterSource[]::new);
        if (postgres) {
            return affected(jdbcTemplate.batchUpdate(POSTGRES_UPSERT, parameters));
        }

        int[] updates = jdbcTemplate.batchUpdate(UPDATE, parameters);
        List<SqlParameterSource> inserts = new ArrayList<>();
        int changed = 0;
        for (int index = 0; index < updates.length; index++) {
            if (updates[index] == 1 || updates[index] == Statement.SUCCESS_NO_INFO) {
                changed++;
            } else if (!exists(validated.get(index).sourceId(), validated.get(index).instrumentId())) {
                inserts.add(parameters[index]);
            }
        }
        return changed + affected(jdbcTemplate.batchUpdate(INSERT, inserts.toArray(SqlParameterSource[]::new)));
    }

    @Transactional(readOnly = true)
    public Optional<ContractQuoteRow> findLatest(long sourceId, long instrumentId) {
        if (sourceId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("sourceId and instrumentId must be positive");
        }
        return jdbcTemplate.query("select " + COLUMNS + """
                from investment_contract_quote_latest
                where source_id = :sourceId and instrument_id = :instrumentId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId), resultSet -> resultSet.next()
                ? Optional.of(mapRow(resultSet)) : Optional.empty());
    }

    private List<ContractQuoteWrite> validate(List<ContractQuoteWrite> values) {
        JdbcMarketDataSupport.requireSingleDimension(values);
        Set<QuoteKey> keys = new HashSet<>();
        for (ContractQuoteWrite value : values) {
            Objects.requireNonNull(value, "value");
            if (!keys.add(new QuoteKey(value.sourceId(), value.instrumentId()))) {
                throw new IllegalArgumentException("Duplicate latest quote key in batch: "
                        + value.sourceId() + ":" + value.instrumentId());
            }
        }
        return List.copyOf(values);
    }

    private boolean exists(long sourceId, long instrumentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from investment_contract_quote_latest
                where source_id = :sourceId and instrument_id = :instrumentId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId), Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource parameters(ContractQuoteWrite value) {
        return new MapSqlParameterSource()
                .addValue("sourceId", value.sourceId())
                .addValue("instrumentId", value.instrumentId())
                .addValue("lastPrice", value.lastPrice())
                .addValue("markPrice", value.markPrice())
                .addValue("indexPrice", value.indexPrice())
                .addValue("bidPrice", value.bidPrice())
                .addValue("askPrice", value.askPrice())
                .addValue("bidQuantity", value.bidQuantity())
                .addValue("askQuantity", value.askQuantity())
                .addValue("open24h", value.open24h())
                .addValue("high24h", value.high24h())
                .addValue("low24h", value.low24h())
                .addValue("baseVolume24h", value.baseVolume24h())
                .addValue("quoteVolume24h", value.quoteVolume24h())
                .addValue("change24h", value.change24h())
                .addValue("fundingRate", value.fundingRate())
                .addValue("nextFundingTime", JdbcMarketDataSupport.instantParameter(value.nextFundingTime()))
                .addValue("openInterest", value.openInterest())
                .addValue("sourceTime", JdbcMarketDataSupport.instantParameter(value.sourceTime()))
                .addValue("receivedAt", JdbcMarketDataSupport.instantParameter(value.receivedAt()));
    }

    private ContractQuoteRow mapRow(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ContractQuoteRow(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                resultSet.getBigDecimal("last_price"), resultSet.getBigDecimal("mark_price"),
                resultSet.getBigDecimal("index_price"), resultSet.getBigDecimal("bid_price"),
                resultSet.getBigDecimal("ask_price"), resultSet.getBigDecimal("bid_quantity"),
                resultSet.getBigDecimal("ask_quantity"), resultSet.getBigDecimal("open_24h"),
                resultSet.getBigDecimal("high_24h"), resultSet.getBigDecimal("low_24h"),
                resultSet.getBigDecimal("base_volume_24h"), resultSet.getBigDecimal("quote_volume_24h"),
                resultSet.getBigDecimal("change_24h"), resultSet.getBigDecimal("funding_rate"),
                JdbcMarketDataSupport.instant(resultSet, "next_funding_time"),
                resultSet.getBigDecimal("open_interest"),
                JdbcMarketDataSupport.instant(resultSet, "source_time"),
                JdbcMarketDataSupport.instant(resultSet, "received_at"), resultSet.getLong("version"));
    }

    private int affected(int[] counts) {
        int affected = 0;
        for (int count : counts) {
            if (count == 1 || count == Statement.SUCCESS_NO_INFO) {
                affected++;
            }
        }
        return affected;
    }

    private boolean isPostgres(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to identify market-data JDBC dialect", ex);
        }
    }

    private record QuoteKey(long sourceId, long instrumentId) {
    }
}
