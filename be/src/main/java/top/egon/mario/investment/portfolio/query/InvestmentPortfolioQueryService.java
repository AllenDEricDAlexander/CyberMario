package top.egon.mario.investment.portfolio.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.portfolio.web.dto.InvestmentEquityResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentFillMarkerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentLedgerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Owner-scoped portfolio projections implemented as bounded aggregate SQL. */
@Service
public class InvestmentPortfolioQueryService {

    private static final Duration MAX_MARKER_WINDOW = Duration.ofDays(31);
    private static final int MAX_MARKER_PAGE_SIZE = 500;

    private final InvestmentAccessService accessService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InvestmentPortfolioQueryService(
            InvestmentAccessService accessService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.accessService = accessService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Page<InvestmentFillMarkerResponse> fills(
            Long actorId, Long accountId, Long instrumentId,
            Instant from, Instant to, int page, int size) {
        accessService.requireAccountOwner(accountId, actorId);
        validateMarkers(instrumentId, from, to, page, size);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accountId", accountId).addValue("instrumentId", instrumentId)
                .addValue("from", from).addValue("to", to)
                .addValue("limit", size).addValue("offset", (long) page * size);
        List<InvestmentFillMarkerResponse> values = jdbcTemplate.query("""
                select fill.id, fill.instrument_id, fill.market_bar_open_time,
                       fill.filled_at as event_time, fill.side, fill.position_action,
                       paper_order.origin, fill.fill_price, fill.quantity
                from investment_paper_fill fill
                join investment_paper_order paper_order on paper_order.id = fill.order_id
                where paper_order.account_id = :accountId
                  and fill.instrument_id = :instrumentId
                  and fill.filled_at >= :from and fill.filled_at < :to
                order by fill.filled_at asc, fill.id asc
                limit :limit offset :offset
                """, parameters, (resultSet, rowNumber) -> {
            boolean liquidation = "LIQUIDATION".equals(resultSet.getString("origin"));
            return new InvestmentFillMarkerResponse(
                    resultSet.getLong("id"), resultSet.getLong("instrument_id"),
                    instant(resultSet, "market_bar_open_time"),
                    instant(resultSet, "event_time"), resultSet.getString("side"),
                    resultSet.getString("position_action"), resultSet.getString("origin"),
                    liquidation ? "LIQUIDATION_FILL" : "FILL",
                    text(resultSet.getBigDecimal("fill_price")), text(resultSet.getBigDecimal("quantity")),
                    liquidation);
        });
        Long total = jdbcTemplate.queryForObject("""
                select count(*) from investment_paper_fill fill
                join investment_paper_order paper_order on paper_order.id = fill.order_id
                where paper_order.account_id = :accountId
                  and fill.instrument_id = :instrumentId
                  and fill.filled_at >= :from and fill.filled_at < :to
                """, parameters, Long.class);
        return new PageImpl<>(values, PageRequest.of(page, size), total == null ? 0L : total);
    }

    @Transactional(readOnly = true)
    public List<InvestmentPositionResponse> positions(Long actorId, Long accountId) {
        accessService.requireAccountOwner(accountId, actorId);
        return jdbcTemplate.query("""
                select pos.*, quote.mark_price, contract.contract_multiplier
                from investment_position pos
                left join investment_contract_spec contract on contract.instrument_id = pos.instrument_id
                left join investment_contract_quote_latest quote
                  on quote.instrument_id = pos.instrument_id and quote.source_id = contract.source_id
                where pos.account_id = :accountId
                order by pos.instrument_id asc, pos.id asc
                """, new MapSqlParameterSource("accountId", accountId), (resultSet, rowNumber) -> {
            return mapPosition(resultSet);
        });
    }

    @Transactional(readOnly = true)
    public List<InvestmentPositionResponse> workspacePositions(long workspaceId, Instant cutoff) {
        return jdbcTemplate.query("""
                select pos.*,
                       (select bar.close_price
                        from investment_market_bar_intraday bar
                        where bar.source_id = contract.source_id
                          and bar.instrument_id = pos.instrument_id
                          and bar.price_type = 'MARK' and bar.interval_code = 'M1'
                          and bar.is_closed = true and bar.close_time <= :cutoff
                          and bar.valid_from <= :cutoff
                          and (bar.valid_to is null or bar.valid_to > :cutoff)
                        order by bar.close_time desc, bar.revision desc
                        limit 1) as mark_price,
                       contract.contract_multiplier
                from investment_position pos
                join investment_paper_account account on account.id = pos.account_id
                left join investment_contract_spec contract on contract.instrument_id = pos.instrument_id
                where account.workspace_id = :workspaceId and account.deleted = false
                  and pos.created_at <= :cutoff
                order by pos.account_id asc, pos.instrument_id asc, pos.id asc
                """, new MapSqlParameterSource("workspaceId", workspaceId).addValue("cutoff", cutoff),
                (resultSet, rowNumber) -> mapPosition(resultSet));
    }

    private InvestmentPositionResponse mapPosition(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        BigDecimal mark = resultSet.getBigDecimal("mark_price");
        BigDecimal multiplier = resultSet.getBigDecimal("contract_multiplier");
        BigDecimal unrealized = null;
        if (mark != null && multiplier != null) {
            BigDecimal change = "LONG".equals(resultSet.getString("position_side"))
                    ? mark.subtract(resultSet.getBigDecimal("entry_price"))
                    : resultSet.getBigDecimal("entry_price").subtract(mark);
            unrealized = change.multiply(resultSet.getBigDecimal("quantity")).multiply(multiplier);
        }
        return new InvestmentPositionResponse(
                resultSet.getLong("id"), resultSet.getLong("instrument_id"),
                resultSet.getString("position_side"), text(resultSet.getBigDecimal("quantity")),
                text(resultSet.getBigDecimal("entry_price")), text(resultSet.getBigDecimal("leverage")),
                text(mark), text(resultSet.getBigDecimal("liquidation_price")),
                text(resultSet.getBigDecimal("isolated_margin")),
                text(resultSet.getBigDecimal("maintenance_margin")),
                text(resultSet.getBigDecimal("realized_pnl")),
                text(resultSet.getBigDecimal("funding_pnl")), text(unrealized),
                instant(resultSet, "last_fill_at"),
                instant(resultSet, "last_margin_check_at"));
    }

    @Transactional(readOnly = true)
    public Page<InvestmentLedgerResponse> ledger(
            Long actorId, Long accountId, int page, int size) {
        accessService.requireAccountOwner(accountId, actorId);
        validatePage(page, size, 100);
        MapSqlParameterSource parameters = new MapSqlParameterSource("accountId", accountId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        List<InvestmentLedgerResponse> values = jdbcTemplate.query("""
                select id, sequence_no, event_type, amount, balance_after, instrument_id,
                       reference_type, reference_id, occurred_at
                from investment_margin_ledger where account_id = :accountId
                order by sequence_no desc, id desc limit :limit offset :offset
                """, parameters, (resultSet, rowNumber) -> new InvestmentLedgerResponse(
                resultSet.getLong("id"), resultSet.getLong("sequence_no"), resultSet.getString("event_type"),
                text(resultSet.getBigDecimal("amount")), text(resultSet.getBigDecimal("balance_after")),
                nullableLong(resultSet, "instrument_id"), resultSet.getString("reference_type"),
                resultSet.getString("reference_id"), instant(resultSet, "occurred_at")));
        return new PageImpl<>(values, PageRequest.of(page, size), count(
                "select count(*) from investment_margin_ledger where account_id = :accountId", parameters));
    }

    @Transactional(readOnly = true)
    public Page<InvestmentEquityResponse> equity(
            Long actorId, Long accountId, int page, int size) {
        accessService.requireAccountOwner(accountId, actorId);
        validatePage(page, size, 500);
        MapSqlParameterSource parameters = new MapSqlParameterSource("accountId", accountId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        List<InvestmentEquityResponse> values = jdbcTemplate.query("""
                select * from investment_account_snapshot where account_id = :accountId
                order by snapshot_time desc limit :limit offset :offset
                """, parameters, (resultSet, rowNumber) -> new InvestmentEquityResponse(
                instant(resultSet, "snapshot_time"),
                text(resultSet.getBigDecimal("wallet_balance")), text(resultSet.getBigDecimal("used_margin")),
                text(resultSet.getBigDecimal("maintenance_margin")),
                text(resultSet.getBigDecimal("unrealized_pnl")), text(resultSet.getBigDecimal("equity")),
                text(resultSet.getBigDecimal("available_balance")),
                text(resultSet.getBigDecimal("gross_exposure")), text(resultSet.getBigDecimal("total_return")),
                text(resultSet.getBigDecimal("drawdown")), resultSet.getLong("position_count")));
        return new PageImpl<>(values, PageRequest.of(page, size), count(
                "select count(*) from investment_account_snapshot where account_id = :accountId", parameters));
    }

    @Transactional(readOnly = true)
    public PortfolioWorkspaceSummary workspaceSummary(long workspaceId, Instant cutoff) {
        return jdbcTemplate.query("""
                select count(account.id) as account_count,
                       coalesce(sum(coalesce(snapshot.wallet_balance, account.wallet_balance)), 0)
                           as wallet_balance,
                       coalesce(sum(coalesce(snapshot.equity, account.wallet_balance)), 0) as equity,
                       coalesce(sum(coalesce(snapshot.available_balance, account.wallet_balance)), 0)
                           as available_balance,
                       coalesce(sum(snapshot.unrealized_pnl), 0) as unrealized_pnl,
                       coalesce(sum(snapshot.gross_exposure), 0) as gross_exposure,
                       coalesce(max(snapshot.drawdown), 0) as max_drawdown,
                       (select count(*) from investment_position pos
                        join investment_paper_account owner on owner.id = pos.account_id
                        where owner.workspace_id = :workspaceId and owner.deleted = false
                          and pos.created_at <= :cutoff) as position_count,
                       coalesce(sum(case when snapshot.drawdown > 0.1 then 1 else 0 end), 0) as warning_count
                from investment_paper_account account
                left join investment_account_snapshot snapshot
                  on snapshot.account_id = account.id
                 and snapshot.snapshot_time = (
                    select max(latest.snapshot_time) from investment_account_snapshot latest
                    where latest.account_id = account.id and latest.snapshot_time <= :cutoff)
                where account.workspace_id = :workspaceId and account.deleted = false
                  and account.created_at <= :cutoff
                """, new MapSqlParameterSource("workspaceId", workspaceId).addValue("cutoff", cutoff),
                resultSet -> {
                    resultSet.next();
                    return new PortfolioWorkspaceSummary(
                            workspaceId, cutoff, resultSet.getLong("account_count"),
                            resultSet.getLong("position_count"), resultSet.getBigDecimal("wallet_balance"),
                            resultSet.getBigDecimal("equity"), resultSet.getBigDecimal("available_balance"),
                            resultSet.getBigDecimal("unrealized_pnl"), resultSet.getBigDecimal("gross_exposure"),
                            resultSet.getBigDecimal("max_drawdown"), resultSet.getLong("warning_count"));
                });
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long value = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return value == null ? 0L : value;
    }

    private static void validateMarkers(Long instrumentId, Instant from, Instant to, int page, int size) {
        if (instrumentId == null || instrumentId <= 0 || from == null || to == null || !to.isAfter(from)
                || Duration.between(from, to).compareTo(MAX_MARKER_WINDOW) > 0) {
            throw invalid("Marker query requires instrumentId and a window no larger than 31 days");
        }
        validatePage(page, size, MAX_MARKER_PAGE_SIZE);
    }

    private static void validatePage(int page, int size, int maxSize) {
        if (page < 0 || size <= 0 || size > maxSize) {
            throw invalid("Portfolio page is outside the supported bounds");
        }
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String name) throws java.sql.SQLException {
        long value = resultSet.getLong(name);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(java.sql.ResultSet resultSet, String name) throws java.sql.SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static String text(BigDecimal value) {
        return value == null ? null : InvestmentDecimalCodec.format(value);
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }
}
