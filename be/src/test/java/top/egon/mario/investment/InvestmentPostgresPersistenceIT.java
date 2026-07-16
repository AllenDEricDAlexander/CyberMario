package top.egon.mario.investment;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentPostgresPersistenceIT {

    private InvestmentPostgresTestSupport database;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrateDisposablePostgres() {
        database = InvestmentPostgresTestSupport.create("investment_persistence");
        jdbc = database.jdbc();
    }

    @BeforeEach
    void resetData() {
        database.resetInvestmentData();
    }

    @AfterAll
    void dropDisposableSchema() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void correctedBarAndFundingKeepBothRevisionsAndResolveByDataAsOf() {
        Fixture fixture = seedFixture();
        Instant firstValidFrom = Instant.parse("2026-07-16T01:00:00Z");
        Instant correctedAt = Instant.parse("2026-07-16T02:00:00Z");
        Instant openTime = Instant.parse("2026-07-16T00:00:00Z");
        Instant fundingTime = Instant.parse("2026-07-16T08:00:00Z");

        writeBarRevision(fixture, openTime, firstValidFrom, "100", "bar-v1");
        writeBarRevision(fixture, openTime, firstValidFrom.plusSeconds(30), "100", "bar-v1");
        writeBarRevision(fixture, openTime, correctedAt, "101", "bar-v2");

        writeFundingRevision(fixture, fundingTime, firstValidFrom, "0.000100000000", "funding-v1");
        writeFundingRevision(fixture, fundingTime, firstValidFrom.plusSeconds(30),
                "0.000100000000", "funding-v1");
        writeFundingRevision(fixture, fundingTime, correctedAt,
                "0.000200000000", "funding-v2");

        assertThat(count("investment_market_bar_intraday")).isEqualTo(2);
        assertThat(count("investment_funding_rate")).isEqualTo(2);
        assertThat(barCloseAsOf(fixture, openTime, firstValidFrom.plusSeconds(60))).isEqualByComparingTo("100");
        assertThat(barCloseAsOf(fixture, openTime, correctedAt.plusSeconds(60))).isEqualByComparingTo("101");
        assertThat(fundingRateAsOf(fixture, fundingTime, firstValidFrom.plusSeconds(60)))
                .isEqualByComparingTo("0.000100000000");
        assertThat(fundingRateAsOf(fixture, fundingTime, correctedAt.plusSeconds(60)))
                .isEqualByComparingTo("0.000200000000");
    }

    @Test
    void concurrentOnConflictWritesCreateOneJobAndOneLatestQuote() throws Exception {
        Fixture fixture = seedFixture();
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> jobs = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return jdbc.update("""
                                insert into investment_job
                                    (workspace_id, job_type, status, idempotency_key, input_json, result_json)
                                values (?, 'MARKET_INGEST', 'PENDING', ?, '{}'::jsonb, '{}'::jsonb)
                                on conflict (idempotency_key) do nothing
                                """, fixture.workspaceId(), "job-concurrent");
                    }))
                    .toList();
            List<Future<Integer>> quotes = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return jdbc.update("""
                                insert into investment_contract_quote_latest
                                    (source_id, instrument_id, last_price, source_time, received_at, version)
                                values (?, ?, ?, now(), now(), 0)
                                on conflict (source_id, instrument_id) do update
                                set last_price = excluded.last_price,
                                    source_time = excluded.source_time,
                                    received_at = excluded.received_at,
                                    version = investment_contract_quote_latest.version + 1
                                """, fixture.sourceId(), fixture.instrumentId(), 100 + index);
                    }))
                    .toList();

            start.countDown();
            assertThat(awaitUpdates(jobs)).isEqualTo(1);
            assertThat(awaitUpdates(quotes)).isEqualTo(workers);
        } finally {
            shutdown(executor);
        }

        assertThat(jdbc.queryForObject("select count(*) from investment_job where idempotency_key = ?",
                Integer.class, "job-concurrent")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from investment_contract_quote_latest
                where source_id = ? and instrument_id = ?
                """, Integer.class, fixture.sourceId(), fixture.instrumentId())).isEqualTo(1);
    }

    @Test
    void skipLockedClaimsDoNotOverlapAndRecoveredLeaseFencesStaleToken() throws Exception {
        Fixture fixture = seedFixture();
        jdbc.update("""
                insert into investment_job
                    (workspace_id, job_type, status, available_at, idempotency_key, input_json, result_json)
                values (?, 'REPORT_BUILD', 'PENDING', now() - interval '1 minute', 'claim-a', '{}'::jsonb, '{}'::jsonb),
                       (?, 'REPORT_BUILD', 'PENDING', now() - interval '1 minute', 'claim-b', '{}'::jsonb, '{}'::jsonb)
                """, fixture.workspaceId(), fixture.workspaceId());

        Long first;
        Long second;
        try (Connection txA = database.dataSource().getConnection();
             Connection txB = database.dataSource().getConnection()) {
            txA.setAutoCommit(false);
            txB.setAutoCommit(false);
            first = claimPending(txA, "worker-a", "token-a");
            second = claimPending(txB, "worker-b", "token-b");
            assertThat(first).isNotNull();
            assertThat(second).isNotNull().isNotEqualTo(first);
            txA.commit();
            txB.commit();
        }

        jdbc.update("""
                update investment_job
                set lease_expires_at = now() - interval '1 minute', heartbeat_at = now() - interval '2 minutes'
                where id = ?
                """, first);
        int recovered = jdbc.update("""
                update investment_job
                set locked_at = now(), locked_by = 'worker-recovery', claim_token = 'token-recovery',
                    lease_expires_at = now() + interval '1 minute', heartbeat_at = now(), attempts = attempts + 1
                where id = ? and status = 'RUNNING' and claim_token = 'token-a' and lease_expires_at <= now()
                """, first);
        int staleHeartbeat = jdbc.update("""
                update investment_job
                set heartbeat_at = now(), lease_expires_at = now() + interval '1 minute'
                where id = ? and status = 'RUNNING' and locked_by = 'worker-a' and claim_token = 'token-a'
                """, first);
        int recoveredHeartbeat = jdbc.update("""
                update investment_job
                set heartbeat_at = now(), lease_expires_at = now() + interval '1 minute'
                where id = ? and status = 'RUNNING'
                  and locked_by = 'worker-recovery' and claim_token = 'token-recovery'
                """, first);

        assertThat(recovered).isEqualTo(1);
        assertThat(staleHeartbeat).isZero();
        assertThat(recoveredHeartbeat).isEqualTo(1);
    }

    @Test
    void concurrentOverLimitIntentsSerializeToOnePendingOrderAndOneRiskRejection() throws Exception {
        Fixture fixture = seedFixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> submitLimitedIntent(fixture, "concurrent-a", start));
            Future<String> second = executor.submit(() -> submitLimitedIntent(fixture, "concurrent-b", start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ACCEPTED", "RISK_REJECTED");
        } finally {
            shutdown(executor);
        }

        assertThat(jdbc.queryForObject("select count(*) from investment_paper_order where status = 'PENDING_MATCH'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_trade_intent where status = 'RISK_REJECTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_job where job_type = 'PAPER_MATCH'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void retriedIntentFillFundingAndLedgerWritesLeaveOneFinancialEffect() {
        Fixture fixture = seedFixture();
        Instant now = Instant.parse("2026-07-16T03:00:00Z");
        Long intentId = upsertIntent(fixture, "retry-intent", "USER", "manual-retry", "ACCEPTED", now);
        assertThat(upsertIntent(fixture, "retry-intent", "USER", "manual-retry", "ACCEPTED", now))
                .isEqualTo(intentId);
        Long orderId = insertOrder(fixture, intentId, "retry-order", "USER", now);
        insertOrder(fixture, intentId, "retry-order", "USER", now);

        insertFill(orderId, fixture.instrumentId(), now);
        insertFill(orderId, fixture.instrumentId(), now);
        insertLedger(fixture, "retry-ledger", orderId.toString(), now);
        insertLedger(fixture, "retry-ledger", orderId.toString(), now);
        insertFundingFact(fixture, now.plusSeconds(3600), "retry-funding");
        insertFundingFact(fixture, now.plusSeconds(3600), "retry-funding");

        assertThat(jdbc.queryForObject("select count(*) from investment_trade_intent where idempotency_key = ?",
                Integer.class, "retry-intent")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_paper_fill where order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_margin_ledger where idempotency_key = ?",
                Integer.class, "retry-ledger")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_funding_rate where checksum = ?",
                Integer.class, "retry-funding")).isEqualTo(1);
    }

    @Test
    void agentCrashAfterFacadeCommitRecoversOneIntentOrderAndFinancialEffect() {
        Fixture fixture = seedFixture();
        Instant now = Instant.parse("2026-07-16T04:00:00Z");
        Long auditId = jdbc.queryForObject("""
                insert into agent_run_audit
                    (thread_id, user_id, status, started_at, created_at)
                values ('investment-postgres-agent', 1001, 'RUNNING', ?, ?)
                returning id
                """, Long.class, timestamp(now), timestamp(now));
        Long runId = jdbc.queryForObject("""
                insert into investment_agent_run
                    (workspace_id, account_id, agent_preset_code, generic_agent_run_audit_id,
                     run_type, status, data_as_of, input_snapshot_json, started_at, idempotency_key)
                values (?, ?, 'PAPER_FUTURES_V1', ?, 'AUTO_TRADE', 'RUNNING', ?, '{}'::jsonb, ?, ?)
                returning id
                """, Long.class, fixture.workspaceId(), fixture.accountId(), auditId,
                timestamp(now.minusSeconds(1)), timestamp(now), "agent-run-recovery");
        Long decisionId = jdbc.queryForObject("""
                insert into investment_agent_decision
                    (run_id, instrument_id, action, confidence, horizon, thesis, risks_json, invalidation_json,
                     requested_quantity, requested_notional, requested_leverage, order_type,
                     execution_status, execution_idempotency_key, data_as_of, status, created_at)
                values (?, ?, 'OPEN_LONG', 0.8, '1H', 'fixture decision', '[]'::jsonb, '[]'::jsonb,
                        1, 100, 2, 'MARKET', 'PENDING', ?, ?, 'VALIDATED', ?)
                returning id
                """, Long.class, runId, fixture.instrumentId(), "agent-execution-recovery",
                timestamp(now.minusSeconds(1)), timestamp(now));

        Long committedIntent = commitAgentFacadeEffect(fixture, decisionId, now);
        Long recoveredIntent = commitAgentFacadeEffect(fixture, decisionId, now);
        int linked = jdbc.update("""
                update investment_agent_decision
                set intent_id = ?, execution_status = 'SUBMITTED'
                where id = ? and execution_status = 'PENDING' and intent_id is null
                """, recoveredIntent, decisionId);

        assertThat(recoveredIntent).isEqualTo(committedIntent);
        assertThat(linked).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_trade_intent where idempotency_key = ?",
                Integer.class, "agent-execution-recovery")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_paper_order where intent_id = ?",
                Integer.class, committedIntent)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_paper_fill",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from investment_margin_ledger where idempotency_key = ?",
                Integer.class, "agent-ledger-" + decisionId)).isEqualTo(1);
    }

    @Test
    void compositeForeignKeysAndUniqueKeysRejectOwnerScopeMismatches() {
        Fixture fixture = seedFixture();
        Long otherWorkspace = insertWorkspace(2002, "other-workspace");
        Instant now = Instant.parse("2026-07-16T05:00:00Z");

        assertThatThrownBy(() -> jdbc.update("""
                insert into investment_trade_intent
                    (workspace_id, account_id, instrument_id, source_type, idempotency_key,
                     position_action, side, order_type, quantity, requested_notional, leverage,
                     data_as_of, status)
                values (?, ?, ?, 'USER', 'scope-mismatch', 'OPEN', 'BUY', 'MARKET', 1, 100, 2, ?, 'RECEIVED')
                """, otherWorkspace, fixture.accountId(), fixture.instrumentId(), timestamp(now)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertWorkspace(1001, "fixture-workspace"))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long intentId = upsertIntent(fixture, "scope-valid", "USER", "scope-valid", "ACCEPTED", now);
        Long otherInstrument = insertInstrument(fixture.venueId(), "ETHUSDT");
        assertThatThrownBy(() -> jdbc.update("""
                insert into investment_paper_order
                    (workspace_id, account_id, intent_id, client_order_id, instrument_id, origin,
                     position_action, side, order_type, time_in_force, quantity, remaining_quantity,
                     leverage, status, submitted_at)
                values (?, ?, ?, 'scope-order', ?, 'USER', 'OPEN', 'BUY', 'MARKET', 'GTC', 1, 1, 2,
                        'PENDING_MATCH', ?)
                """, fixture.workspaceId(), fixture.accountId(), intentId, otherInstrument, timestamp(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture seedFixture() {
        Long venueId = jdbc.queryForObject("""
                insert into investment_venue (code, name, status, metadata_json)
                values ('BITGET_FIXTURE', 'Bitget fixture', 'ACTIVE', '{}'::jsonb)
                returning id
                """, Long.class);
        Long sourceId = jdbc.queryForObject("""
                insert into investment_data_source
                    (venue_id, code, provider_type, api_family, product_type,
                     capabilities_json, rate_limit_per_second, status, settings_json)
                values (?, 'BITGET_FIXTURE', 'FIXTURE', 'BITGET', 'USDT_FUTURES',
                        '["BARS","FUNDING"]'::jsonb, 10, 'ACTIVE', '{}'::jsonb)
                returning id
                """, Long.class, venueId);
        Long instrumentId = insertInstrument(venueId, "BTCUSDT");
        Long workspaceId = insertWorkspace(1001, "fixture-workspace");
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        Long accountId = jdbc.queryForObject("""
                insert into investment_paper_account
                    (workspace_id, name, margin_asset, initial_equity, wallet_balance,
                     margin_mode, position_mode, trading_enabled, agent_auto_trade_enabled,
                     status, opened_at)
                values (?, 'paper-main', 'USDT', 10000, 10000, 'ISOLATED', 'ONE_WAY',
                        true, true, 'ACTIVE', ?)
                returning id
                """, Long.class, workspaceId, timestamp(now));
        jdbc.update("""
                insert into investment_risk_profile
                    (account_id, max_leverage, max_order_notional, max_position_notional,
                     max_gross_exposure_notional, max_open_positions, max_daily_loss_amount,
                     max_drawdown_ratio, max_orders_per_hour, cooldown_seconds,
                     max_market_data_age_seconds, max_slippage_bps, settings_json)
                values (?, 5, 1000, 1000, 1000, 1, 500, 0.2, 100, 0, 300, 100, '{}'::jsonb)
                """, accountId);
        return new Fixture(venueId, sourceId, instrumentId, workspaceId, accountId);
    }

    private Long insertInstrument(Long venueId, String symbol) {
        return jdbc.queryForObject("""
                insert into investment_instrument
                    (venue_id, market_type, product_type, contract_type, symbol,
                     base_asset, quote_asset, settlement_asset, margin_asset, status)
                values (?, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', ?,
                        ?, 'USDT', 'USDT', 'USDT', 'ACTIVE')
                returning id
                """, Long.class, venueId, symbol, symbol.replace("USDT", ""));
    }

    private Long insertWorkspace(long ownerUserId, String name) {
        return jdbc.queryForObject("""
                insert into investment_workspace (owner_user_id, name, base_currency, timezone, status, settings_json)
                values (?, ?, 'USDT', 'UTC', 'ACTIVE', '{}'::jsonb)
                returning id
                """, Long.class, ownerUserId, name);
    }

    private void writeBarRevision(Fixture fixture, Instant openTime, Instant validFrom,
                                  String closePrice, String checksum) {
        var current = jdbc.query("""
                select revision, checksum
                from investment_market_bar_intraday
                where source_id = ? and instrument_id = ? and price_type = 'MARK'
                  and interval_code = 'H1' and open_time = ? and revision_slot = 0
                for update
                """, (rs, rowNum) -> new Revision(rs.getLong("revision"), rs.getString("checksum")),
                fixture.sourceId(), fixture.instrumentId(), timestamp(openTime));
        if (!current.isEmpty() && current.getFirst().checksum().equals(checksum)) {
            return;
        }
        long revision = current.isEmpty() ? 1 : current.getFirst().revision() + 1;
        if (!current.isEmpty()) {
            jdbc.update("""
                    update investment_market_bar_intraday
                    set revision_slot = revision, valid_to = ?
                    where source_id = ? and instrument_id = ? and price_type = 'MARK'
                      and interval_code = 'H1' and open_time = ? and revision_slot = 0
                    """, timestamp(validFrom), fixture.sourceId(), fixture.instrumentId(), timestamp(openTime));
        }
        jdbc.update("""
                insert into investment_market_bar_intraday
                    (source_id, instrument_id, price_type, interval_code, open_time, close_time,
                     open_price, high_price, low_price, close_price, base_volume, quote_volume,
                     is_closed, ingested_at, revision, revision_slot, valid_from, checksum)
                values (?, ?, 'MARK', 'H1', ?, ?, 100, 110, 90, ?, 10, 1000,
                        true, ?, ?, 0, ?, ?)
                """, fixture.sourceId(), fixture.instrumentId(), timestamp(openTime),
                timestamp(openTime.plusSeconds(3600)), closePrice, timestamp(validFrom), revision,
                timestamp(validFrom), checksum);
    }

    private void writeFundingRevision(Fixture fixture, Instant fundingTime, Instant validFrom,
                                      String rate, String checksum) {
        var current = jdbc.query("""
                select revision, checksum
                from investment_funding_rate
                where source_id = ? and instrument_id = ? and funding_time = ? and revision_slot = 0
                for update
                """, (rs, rowNum) -> new Revision(rs.getLong("revision"), rs.getString("checksum")),
                fixture.sourceId(), fixture.instrumentId(), timestamp(fundingTime));
        if (!current.isEmpty() && current.getFirst().checksum().equals(checksum)) {
            return;
        }
        long revision = current.isEmpty() ? 1 : current.getFirst().revision() + 1;
        if (!current.isEmpty()) {
            jdbc.update("""
                    update investment_funding_rate
                    set revision_slot = revision, valid_to = ?
                    where source_id = ? and instrument_id = ? and funding_time = ? and revision_slot = 0
                    """, timestamp(validFrom), fixture.sourceId(), fixture.instrumentId(), timestamp(fundingTime));
        }
        jdbc.update("""
                insert into investment_funding_rate
                    (source_id, instrument_id, funding_time, funding_rate, ingested_at,
                     revision, revision_slot, valid_from, checksum)
                values (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, fixture.sourceId(), fixture.instrumentId(), timestamp(fundingTime), rate,
                timestamp(validFrom), revision, timestamp(validFrom), checksum);
    }

    private java.math.BigDecimal barCloseAsOf(Fixture fixture, Instant openTime, Instant dataAsOf) {
        return jdbc.queryForObject("""
                select close_price
                from investment_market_bar_intraday
                where source_id = ? and instrument_id = ? and price_type = 'MARK'
                  and interval_code = 'H1' and open_time = ?
                  and valid_from <= ? and (valid_to is null or ? < valid_to)
                order by revision desc limit 1
                """, java.math.BigDecimal.class, fixture.sourceId(), fixture.instrumentId(), timestamp(openTime),
                timestamp(dataAsOf), timestamp(dataAsOf));
    }

    private java.math.BigDecimal fundingRateAsOf(Fixture fixture, Instant fundingTime, Instant dataAsOf) {
        return jdbc.queryForObject("""
                select funding_rate
                from investment_funding_rate
                where source_id = ? and instrument_id = ? and funding_time = ?
                  and valid_from <= ? and (valid_to is null or ? < valid_to)
                order by revision desc limit 1
                """, java.math.BigDecimal.class, fixture.sourceId(), fixture.instrumentId(), timestamp(fundingTime),
                timestamp(dataAsOf), timestamp(dataAsOf));
    }

    private Long claimPending(Connection connection, String workerId, String token) throws SQLException {
        Long id;
        try (PreparedStatement select = connection.prepareStatement("""
                select id from investment_job
                where status = 'PENDING' and available_at <= now()
                order by available_at, priority, id
                limit 1 for update skip locked
                """)) {
            try (ResultSet resultSet = select.executeQuery()) {
                id = resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
        if (id == null) {
            return null;
        }
        try (PreparedStatement update = connection.prepareStatement("""
                update investment_job
                set status = 'RUNNING', locked_at = now(), locked_by = ?, claim_token = ?,
                    lease_expires_at = now() + interval '1 minute', heartbeat_at = now(), started_at = now()
                where id = ? and status = 'PENDING'
                """)) {
            update.setString(1, workerId);
            update.setString(2, token);
            update.setLong(3, id);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
        return id;
    }

    private String submitLimitedIntent(Fixture fixture, String key, CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (Statement timeout = connection.createStatement()) {
                timeout.execute("set local lock_timeout = '5s'");
            }
            try (PreparedStatement accountLock = connection.prepareStatement(
                    "select id from investment_paper_account where id = ? for update")) {
                accountLock.setLong(1, fixture.accountId());
                accountLock.executeQuery().close();
            }
            int openOrders;
            try (PreparedStatement count = connection.prepareStatement("""
                    select count(*) from investment_paper_order
                    where account_id = ? and status = 'PENDING_MATCH'
                    """)) {
                count.setLong(1, fixture.accountId());
                try (ResultSet resultSet = count.executeQuery()) {
                    resultSet.next();
                    openOrders = resultSet.getInt(1);
                }
            }
            String status = openOrders == 0 ? "ACCEPTED" : "RISK_REJECTED";
            long intentId;
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into investment_trade_intent
                        (workspace_id, account_id, instrument_id, source_type, source_reference_id,
                         idempotency_key, position_action, side, order_type, quantity, requested_notional,
                         leverage, data_as_of, status, risk_checked_at, accepted_at)
                    values (?, ?, ?, 'USER', ?, ?, 'OPEN', 'BUY', 'MARKET', 1, 100, 2,
                            now(), ?, now(), case when ? = 'ACCEPTED' then now() else null end)
                    returning id
                    """)) {
                insert.setLong(1, fixture.workspaceId());
                insert.setLong(2, fixture.accountId());
                insert.setLong(3, fixture.instrumentId());
                insert.setString(4, key);
                insert.setString(5, key);
                insert.setString(6, status);
                insert.setString(7, status);
                try (ResultSet resultSet = insert.executeQuery()) {
                    resultSet.next();
                    intentId = resultSet.getLong(1);
                }
            }
            if ("ACCEPTED".equals(status)) {
                try (PreparedStatement order = connection.prepareStatement("""
                        insert into investment_paper_order
                            (workspace_id, account_id, intent_id, client_order_id, instrument_id, origin,
                             position_action, side, order_type, time_in_force, quantity, remaining_quantity,
                             leverage, status, submitted_at)
                        values (?, ?, ?, ?, ?, 'USER', 'OPEN', 'BUY', 'MARKET', 'GTC', 1, 1, 2,
                                'PENDING_MATCH', now())
                        """)) {
                    order.setLong(1, fixture.workspaceId());
                    order.setLong(2, fixture.accountId());
                    order.setLong(3, intentId);
                    order.setString(4, "order-" + key);
                    order.setLong(5, fixture.instrumentId());
                    order.executeUpdate();
                }
                try (PreparedStatement job = connection.prepareStatement("""
                        insert into investment_job
                            (workspace_id, job_type, status, idempotency_key, input_json, result_json)
                        values (?, 'PAPER_MATCH', 'PENDING', ?, '{}'::jsonb, '{}'::jsonb)
                        """)) {
                    job.setLong(1, fixture.workspaceId());
                    job.setString(2, "match-" + key);
                    job.executeUpdate();
                }
            }
            try (PreparedStatement riskCheck = connection.prepareStatement("""
                    insert into investment_risk_check
                        (intent_id, rule_code, passed, observed_value, limit_value, message,
                         details_json, checked_at)
                    values (?, 'MAX_OPEN_ORDERS', ?, ?, 1, ?, '{}'::jsonb, now())
                    """)) {
                riskCheck.setLong(1, intentId);
                riskCheck.setBoolean(2, "ACCEPTED".equals(status));
                riskCheck.setInt(3, openOrders);
                riskCheck.setString(4, "ACCEPTED".equals(status) ? "within limit" : "open order limit reached");
                riskCheck.executeUpdate();
            }
            connection.commit();
            return status;
        }
    }

    private Long upsertIntent(Fixture fixture, String idempotencyKey, String sourceType,
                              String sourceReference, String status, Instant now) {
        return jdbc.queryForObject("""
                with inserted as (
                    insert into investment_trade_intent
                        (workspace_id, account_id, instrument_id, source_type, source_reference_id,
                         idempotency_key, position_action, side, order_type, quantity, requested_notional,
                         leverage, data_as_of, status, risk_checked_at, accepted_at)
                    values (?, ?, ?, ?, ?, ?, 'OPEN', 'BUY', 'MARKET', 1, 100, 2, ?, ?, ?, ?)
                    on conflict (idempotency_key) do nothing
                    returning id
                )
                select id from inserted
                union all
                select id from investment_trade_intent where idempotency_key = ?
                limit 1
                """, Long.class, fixture.workspaceId(), fixture.accountId(), fixture.instrumentId(), sourceType,
                sourceReference, idempotencyKey, timestamp(now), status, timestamp(now),
                "ACCEPTED".equals(status) ? timestamp(now) : null, idempotencyKey);
    }

    private Long insertOrder(Fixture fixture, Long intentId, String clientOrderId, String origin, Instant now) {
        return jdbc.queryForObject("""
                with inserted as (
                    insert into investment_paper_order
                        (workspace_id, account_id, intent_id, client_order_id, instrument_id, origin,
                         position_action, side, order_type, time_in_force, quantity, remaining_quantity,
                         leverage, status, submitted_at)
                    values (?, ?, ?, ?, ?, ?, 'OPEN', 'BUY', 'MARKET', 'GTC', 1, 1, 2,
                            'PENDING_MATCH', ?)
                    on conflict (client_order_id) do nothing
                    returning id
                )
                select id from inserted
                union all
                select id from investment_paper_order where client_order_id = ?
                limit 1
                """, Long.class, fixture.workspaceId(), fixture.accountId(), intentId, clientOrderId,
                fixture.instrumentId(), origin, timestamp(now), clientOrderId);
    }

    private void insertFill(Long orderId, Long instrumentId, Instant now) {
        jdbc.update("""
                insert into investment_paper_fill
                    (order_id, fill_no, instrument_id, position_action, side, fill_price, quantity,
                     notional, fee_rate, fee_amount, fee_asset, liquidity, filled_at)
                values (?, 1, ?, 'OPEN', 'BUY', 100, 1, 100, 0.001, 0.1, 'USDT', 'TAKER', ?)
                on conflict (order_id, fill_no) do nothing
                """, orderId, instrumentId, timestamp(now));
    }

    private void insertLedger(Fixture fixture, String idempotencyKey, String referenceId, Instant now) {
        jdbc.update("""
                insert into investment_margin_ledger
                    (account_id, sequence_no, event_type, asset, amount, balance_after, instrument_id,
                     reference_type, reference_id, idempotency_key, occurred_at, details_json)
                values (?, 1, 'MARGIN_RESERVE', 'USDT', -50, 9950, ?, 'PAPER_ORDER', ?, ?, ?, '{}'::jsonb)
                on conflict (idempotency_key) do nothing
                """, fixture.accountId(), fixture.instrumentId(), referenceId, idempotencyKey, timestamp(now));
    }

    private void insertFundingFact(Fixture fixture, Instant fundingTime, String checksum) {
        jdbc.update("""
                insert into investment_funding_rate
                    (source_id, instrument_id, funding_time, funding_rate, ingested_at,
                     revision, revision_slot, valid_from, checksum)
                values (?, ?, ?, 0.0001, ?, 1, 0, ?, ?)
                on conflict (source_id, instrument_id, funding_time, revision) do nothing
                """, fixture.sourceId(), fixture.instrumentId(), timestamp(fundingTime), timestamp(fundingTime),
                timestamp(fundingTime), checksum);
    }

    private Long commitAgentFacadeEffect(Fixture fixture, Long decisionId, Instant now) {
        Long intentId = upsertIntent(fixture, "agent-execution-recovery", "AGENT",
                decisionId.toString(), "ACCEPTED", now);
        Long orderId = insertOrder(fixture, intentId, "agent-order-" + decisionId, "AGENT", now);
        insertFill(orderId, fixture.instrumentId(), now);
        insertLedger(fixture, "agent-ledger-" + decisionId, orderId.toString(), now);
        return intentId;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static int awaitUpdates(List<Future<Integer>> futures) throws Exception {
        int total = 0;
        for (Future<Integer> future : futures) {
            total += future.get(10, TimeUnit.SECONDS);
        }
        return total;
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record Fixture(Long venueId, Long sourceId, Long instrumentId, Long workspaceId, Long accountId) {
    }

    private record Revision(long revision, String checksum) {
    }
}
