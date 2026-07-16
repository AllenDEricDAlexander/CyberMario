package top.egon.mario.investment.quant.repository.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class BacktestEquityJdbcRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BacktestEquityJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(long runId, List<BacktestEquityPoint> points, Instant createdAt) {
        SqlParameterSource[] batch = points.stream().map(point -> new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("pointTime", Timestamp.from(point.pointTime()))
                .addValue("walletBalance", point.walletBalance())
                .addValue("usedMargin", point.usedMargin())
                .addValue("unrealizedPnl", point.unrealizedPnl())
                .addValue("equity", point.equity())
                .addValue("drawdown", point.drawdown())
                .addValue("grossExposure", point.grossExposure())
                .addValue("createdAt", Timestamp.from(createdAt))).toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                insert into investment_backtest_equity_point(
                    run_id, point_time, wallet_balance, used_margin, unrealized_pnl,
                    equity, drawdown, gross_exposure, created_at
                ) values (
                    :runId, :pointTime, :walletBalance, :usedMargin, :unrealizedPnl,
                    :equity, :drawdown, :grossExposure, :createdAt
                )
                """, batch);
    }

    public List<BacktestEquityPoint> findByRunId(long runId) {
        return jdbcTemplate.query("""
                select point_time, wallet_balance, used_margin, unrealized_pnl,
                       equity, drawdown, gross_exposure
                from investment_backtest_equity_point
                where run_id = :runId
                order by point_time asc
                """, new MapSqlParameterSource("runId", runId), (resultSet, rowNumber) ->
                new BacktestEquityPoint(resultSet.getTimestamp("point_time").toInstant(),
                        resultSet.getBigDecimal("wallet_balance"), resultSet.getBigDecimal("used_margin"),
                        resultSet.getBigDecimal("unrealized_pnl"), resultSet.getBigDecimal("equity"),
                        resultSet.getBigDecimal("drawdown"), resultSet.getBigDecimal("gross_exposure"), false));
    }

    public boolean existsByRunId(long runId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from investment_backtest_equity_point where run_id = :runId
                """, new MapSqlParameterSource("runId", runId), Integer.class);
        return count != null && count > 0;
    }
}
