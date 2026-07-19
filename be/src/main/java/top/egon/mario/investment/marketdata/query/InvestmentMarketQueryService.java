package top.egon.mario.investment.marketdata.query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentContractSpecResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentFreshnessResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentFundingRateResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentDetailResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentSummaryResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentMarketOverviewResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPositionTierResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentQuoteResponse;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only market facade using bounded projection queries and code-declared subscriptions.
 */
@Service
public class InvestmentMarketQueryService {

    public static final int MAX_CANDLE_POINTS = 2_000;
    private static final int MAX_MARKET_PAGE_SIZE = 100;
    private static final int MAX_SERIES_PAGE_SIZE = 200;
    private static final Duration QUOTE_FRESHNESS = Duration.ofMinutes(1);
    private static final Duration MAX_INTRADAY_SPAN = Duration.ofDays(31);
    private static final Duration MAX_H4_SPAN = Duration.ofDays(120);
    private static final Duration MAX_DAILY_SPAN = Duration.ofDays(3_660);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final ContractQuoteJdbcRepository quoteRepository;
    private final MarketBarJdbcRepository barRepository;
    private final FundingRateJdbcRepository fundingRateRepository;
    private final Clock clock;

    @Autowired
    public InvestmentMarketQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                        InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                        ContractQuoteJdbcRepository quoteRepository,
                                        MarketBarJdbcRepository barRepository,
                                        FundingRateJdbcRepository fundingRateRepository) {
        this(jdbcTemplate, subscriptionRegistry, quoteRepository, barRepository, fundingRateRepository,
                Clock.systemUTC());
    }

    public InvestmentMarketQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                        InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                        ContractQuoteJdbcRepository quoteRepository,
                                        MarketBarJdbcRepository barRepository,
                                        FundingRateJdbcRepository fundingRateRepository,
                                        Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry, "subscriptionRegistry");
        this.quoteRepository = Objects.requireNonNull(quoteRepository, "quoteRepository");
        this.barRepository = Objects.requireNonNull(barRepository, "barRepository");
        this.fundingRateRepository = Objects.requireNonNull(fundingRateRepository, "fundingRateRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Lists only internally mapped instruments enabled by the Java subscription registry.
     */
    @Transactional(readOnly = true)
    public PageResult<InvestmentInstrumentSummaryResponse> listInstruments(int page, int size,
                                                                           String status, String sort) {
        PageWindow window = pageWindow(page, size, MAX_MARKET_PAGE_SIZE);
        String normalizedStatus = instrumentStatus(status);
        String orderBy = instrumentOrder(sort);
        Map<Long, ResolvedSubscription> subscribed = subscribedMappings();
        if (subscribed.isEmpty()) {
            return emptyPage(window);
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("instrumentIds", subscribed.keySet())
                .addValue("status", normalizedStatus)
                .addValue("offset", window.offset())
                .addValue("limit", window.size());
        String statusPredicate = normalizedStatus == null ? "" : "and i.status = :status";
        long total = requiredCount("""
                select count(*)
                from investment_instrument i
                where i.deleted = false and i.id in (:instrumentIds)
                  %s
                """.formatted(statusPredicate), parameters);
        if (total == 0) {
            return emptyPage(window);
        }
        List<InstrumentProjection> instruments = jdbcTemplate.query("""
                select i.id, v.code as venue_code, i.symbol, i.base_asset, i.quote_asset, i.status,
                       i.settlement_asset, i.margin_asset, i.product_type, i.contract_type, i.launch_time
                from investment_instrument i
                join investment_venue v on v.id = i.venue_id and v.deleted = false
                where i.deleted = false and i.id in (:instrumentIds)
                  %s
                order by %s
                limit :limit offset :offset
                """.formatted(statusPredicate, orderBy), parameters, this::mapInstrument);
        Map<QuoteKey, ContractQuoteRow> quotes = quotes(instruments, subscribed);
        Instant cutoff = clock.instant();
        Map<QuoteKey, ContractQuoteRow> visibleQuotes = quotes.entrySet().stream()
                .filter(entry -> visible(entry.getValue().sourceTime(), entry.getValue().receivedAt(), cutoff))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        List<InvestmentInstrumentSummaryResponse> records = instruments.stream()
                .map(instrument -> summary(instrument, subscribed.get(instrument.id()), visibleQuotes, cutoff))
                .toList();
        return new PageResult<>(records, window.page(), window.size(), total, totalPages(total, window.size()));
    }

    @Transactional(readOnly = true)
    public InvestmentInstrumentDetailResponse instrument(long instrumentId) {
        ResolvedSubscription resolved = requireSubscribedMapping(instrumentId);
        InstrumentProjection instrument = findInstrument(instrumentId);
        ContractQuoteRow quote = quoteRepository.findLatest(resolved.sourceId(), instrumentId).orElse(null);
        ContractSpecProjection contractSpec = findContractSpec(instrumentId);
        Instant cutoff = clock.instant();
        if (quote != null && !visible(quote.sourceTime(), quote.receivedAt(), cutoff)) {
            quote = null;
        }
        if (contractSpec != null && !visible(contractSpec.ingestedAt(), cutoff)) {
            contractSpec = null;
        }
        return new InvestmentInstrumentDetailResponse(
                instrument.id(), instrument.venueCode(), instrument.symbol(), instrument.baseAsset(),
                instrument.quoteAsset(), instrument.settlementAsset(), instrument.marginAsset(),
                instrument.productType(), instrument.contractType(), instrument.status(), instrument.launchTime(),
                names(resolved.subscription().capabilities()), names(resolved.subscription().priceTypes()),
                names(resolved.subscription().intervals()), cutoff,
                freshness(quote == null ? null : quote.sourceTime(), cutoff), contractSpec != null,
                contractSpec == null ? null : contractSpec.response());
    }

    @Transactional(readOnly = true)
    public InvestmentQuoteResponse quote(long instrumentId) {
        ResolvedSubscription resolved = requireCapability(instrumentId, DataCapability.LATEST_TICKER);
        ContractQuoteRow quote = quoteRepository.findLatest(resolved.sourceId(), instrumentId)
                .orElseThrow(() -> unavailable("Latest quote is not available for instrument " + instrumentId));
        Instant cutoff = clock.instant();
        if (!visible(quote.sourceTime(), quote.receivedAt(), cutoff)) {
            throw unavailable("Latest quote is newer than the response cutoff for instrument " + instrumentId);
        }
        return quoteResponse(quote, cutoff);
    }

    @Transactional(readOnly = true)
    public List<InvestmentCandleResponse> candles(long instrumentId, PriceType priceType, BarInterval interval,
                                                   Instant fromInclusive, Instant toExclusive,
                                                   Instant dataAsOf, int limit) {
        validateCandleWindow(priceType, interval, fromInclusive, toExclusive, dataAsOf, limit);
        ResolvedSubscription resolved = requireSubscribedMapping(instrumentId);
        subscriptionRegistry.requireCandle(resolved.sourceCode(), resolved.productType(), resolved.externalSymbol(),
                interval, priceType);
        if (interval == BarInterval.D1) {
            requireUtcMidnight(fromInclusive, "from");
            requireUtcMidnight(toExclusive, "to");
            LocalDate fromDate = LocalDate.ofInstant(fromInclusive, ZoneOffset.UTC);
            LocalDate toDate = LocalDate.ofInstant(toExclusive, ZoneOffset.UTC);
            List<MarketBarDailyRow> rows = dataAsOf == null
                    ? barRepository.findCurrentDaily(resolved.sourceId(), instrumentId, priceType,
                    fromDate, toDate, 0, limit)
                    : barRepository.findDailyAsOf(resolved.sourceId(), instrumentId, priceType,
                    fromDate, toDate, dataAsOf, 0, limit);
            Instant cutoff = dataAsOf == null ? clock.instant() : dataAsOf;
            return rows.stream().filter(row -> visible(row.ingestedAt(), cutoff))
                    .sorted(Comparator.comparing(MarketBarDailyRow::barDate))
                    .map(row -> candle(row, cutoff)).toList();
        }
        List<MarketBarIntradayRow> rows = dataAsOf == null
                ? barRepository.findCurrentIntraday(resolved.sourceId(), instrumentId, priceType, interval,
                fromInclusive, toExclusive, 0, limit)
                : barRepository.findIntradayAsOf(resolved.sourceId(), instrumentId, priceType, interval,
                fromInclusive, toExclusive, dataAsOf, 0, limit);
        Instant cutoff = dataAsOf == null ? clock.instant() : dataAsOf;
        return rows.stream().filter(row -> visible(row.ingestedAt(), cutoff))
                .sorted(Comparator.comparing(MarketBarIntradayRow::openTime))
                .map(row -> candle(row, cutoff)).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<InvestmentFundingRateResponse> fundingRates(long instrumentId,
                                                                  Instant fromInclusive, Instant toExclusive,
                                                                  Instant dataAsOf, int page, int size) {
        PageWindow window = pageWindow(page, size, MAX_SERIES_PAGE_SIZE);
        validateTimeWindow(fromInclusive, toExclusive, Duration.ofDays(1_830));
        validateCutoff(dataAsOf);
        ResolvedSubscription resolved = requireCapability(instrumentId, DataCapability.FUNDING_RATE);
        List<FundingRateRow> rows = dataAsOf == null
                ? fundingRateRepository.findCurrent(resolved.sourceId(), instrumentId, fromInclusive, toExclusive,
                window.offset(), window.size())
                : fundingRateRepository.findAsOf(resolved.sourceId(), instrumentId, fromInclusive, toExclusive,
                dataAsOf, window.offset(), window.size());
        Instant factCutoff = dataAsOf == null ? clock.instant() : dataAsOf;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceId", resolved.sourceId()).addValue("instrumentId", instrumentId)
                .addValue("fromInclusive", Timestamp.from(fromInclusive))
                .addValue("toExclusive", Timestamp.from(toExclusive))
                .addValue("dataAsOf", Timestamp.from(factCutoff));
        String revisionPredicate;
        if (dataAsOf == null) {
            revisionPredicate = "revision_slot = 0 and ingested_at <= :dataAsOf";
        } else {
            revisionPredicate = "valid_from <= :dataAsOf and (valid_to is null or valid_to > :dataAsOf) "
                    + "and ingested_at <= :dataAsOf";
        }
        long total = requiredCount("""
                select count(*) from investment_funding_rate
                where source_id = :sourceId and instrument_id = :instrumentId
                  and funding_time >= :fromInclusive and funding_time < :toExclusive
                  and %s
                """.formatted(revisionPredicate), parameters);
        Instant cutoff = dataAsOf == null ? laterCutoff(factCutoff) : dataAsOf;
        List<InvestmentFundingRateResponse> records = rows.stream()
                .filter(row -> visible(row.ingestedAt(), factCutoff))
                .sorted(Comparator.comparing(FundingRateRow::fundingTime).thenComparingLong(FundingRateRow::revision))
                .map(row -> new InvestmentFundingRateResponse(row.fundingTime(), decimal(row.fundingRate()),
                        row.revision(), cutoff))
                .toList();
        return new PageResult<>(records, window.page(), window.size(), total, totalPages(total, window.size()));
    }

    @Transactional(readOnly = true)
    public List<InvestmentPositionTierResponse> positionTiers(long instrumentId, Instant dataAsOf) {
        validateCutoff(dataAsOf);
        ResolvedSubscription resolved = requireCapability(instrumentId, DataCapability.POSITION_TIER);
        Instant factCutoff = dataAsOf == null ? clock.instant() : dataAsOf;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceId", resolved.sourceId()).addValue("instrumentId", instrumentId)
                .addValue("dataAsOf", Timestamp.from(factCutoff));
        List<PositionTierProjection> rows = jdbcTemplate.query("""
                select tier_level, start_notional, end_notional, max_leverage,
                       maintenance_margin_rate, observed_at, ingested_at
                from investment_position_tier
                where source_id = :sourceId and instrument_id = :instrumentId
                  and observed_at <= :dataAsOf and ingested_at <= :dataAsOf
                  and observed_at = (
                    select max(observed_at) from investment_position_tier
                    where source_id = :sourceId and instrument_id = :instrumentId
                      and observed_at <= :dataAsOf
                      and ingested_at <= :dataAsOf
                  )
                order by tier_level asc, id asc
                """, parameters, (resultSet, rowNumber) -> new PositionTierProjection(
                resultSet.getInt("tier_level"), decimal(resultSet.getBigDecimal("start_notional")),
                decimal(resultSet.getBigDecimal("end_notional")), decimal(resultSet.getBigDecimal("max_leverage")),
                decimal(resultSet.getBigDecimal("maintenance_margin_rate")), instant(resultSet, "observed_at"),
                instant(resultSet, "ingested_at")));
        Instant cutoff = dataAsOf == null ? laterCutoff(factCutoff) : dataAsOf;
        return rows.stream().map(row -> new InvestmentPositionTierResponse(
                row.tierLevel(), row.startNotional(), row.endNotional(), row.maxLeverage(),
                row.maintenanceMarginRate(), row.observedAt(), cutoff)).toList();
    }

    /**
     * Returns shared market and data-warning counts no later than one caller-selected cutoff.
     */
    @Transactional(readOnly = true)
    public InvestmentMarketOverviewResponse overview(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        Map<Long, ResolvedSubscription> subscribed = subscribedMappings();
        if (subscribed.isEmpty()) {
            return new InvestmentMarketOverviewResponse(0, 0, 0, 0, cutoff);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("instrumentIds", subscribed.keySet())
                .addValue("sourceIds", subscribed.values().stream().map(ResolvedSubscription::sourceId).toList())
                .addValue("freshAfter", Timestamp.from(cutoff.minus(QUOTE_FRESHNESS)))
                .addValue("cutoff", Timestamp.from(cutoff));
        long fresh = jdbcTemplate.query("""
                select source_id, instrument_id
                from investment_contract_quote_latest
                where instrument_id in (:instrumentIds) and source_id in (:sourceIds)
                  and source_time >= :freshAfter and source_time <= :cutoff
                  and received_at <= :cutoff
                """, parameters, (resultSet, rowNumber) -> new MarketFactRef(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id")))
                .stream().filter(fact -> selectedSource(subscribed, fact)).map(MarketFactRef::instrumentId)
                .distinct().count();
        long issues = jdbcTemplate.query("""
                select source_id, instrument_id from investment_data_quality_issue
                where deleted = false and resolution_status = 'OPEN'
                  and instrument_id in (:instrumentIds) and source_id in (:sourceIds)
                  and point_time <= :cutoff and created_at <= :cutoff
                """, parameters, (resultSet, rowNumber) -> new MarketFactRef(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id")))
                .stream().filter(fact -> selectedSource(subscribed, fact)).count();
        return new InvestmentMarketOverviewResponse(
                subscribed.size(), fresh, Math.max(0, subscribed.size() - fresh), issues, cutoff);
    }

    private boolean selectedSource(Map<Long, ResolvedSubscription> subscribed, MarketFactRef fact) {
        ResolvedSubscription resolved = subscribed.get(fact.instrumentId());
        return resolved != null && resolved.sourceId() == fact.sourceId();
    }

    private Map<Long, ResolvedSubscription> subscribedMappings() {
        Collection<MarketSubscription> declarations = subscriptionRegistry.subscriptions();
        if (declarations.isEmpty()) {
            return Map.of();
        }
        Map<SubscriptionKey, MarketSubscription> byKey = declarations.stream()
                .collect(Collectors.toMap(SubscriptionKey::of, Function.identity()));
        List<MappingProjection> mappings = jdbcTemplate.query("""
                select i.id as instrument_id, i.product_type, ins.source_id, s.code as source_code,
                       ins.external_symbol
                from investment_instrument_source ins
                join investment_instrument i on i.id = ins.instrument_id and i.deleted = false
                join investment_data_source s on s.id = ins.source_id and s.deleted = false and s.status = 'ACTIVE'
                where ins.deleted = false and ins.source_status = 'ACTIVE'
                order by i.id asc, ins.source_id asc, ins.id asc
                """, Map.of(), (resultSet, rowNumber) -> new MappingProjection(
                resultSet.getLong("instrument_id"), ProductType.valueOf(resultSet.getString("product_type")),
                resultSet.getLong("source_id"), resultSet.getString("source_code"),
                resultSet.getString("external_symbol")));
        Map<Long, ResolvedSubscription> resolved = new LinkedHashMap<>();
        for (MappingProjection mapping : mappings) {
            MarketSubscription subscription = byKey.get(new SubscriptionKey(
                    mapping.sourceCode(), mapping.productType(), mapping.externalSymbol()));
            if (subscription != null) {
                resolved.putIfAbsent(mapping.instrumentId(), new ResolvedSubscription(
                        mapping.sourceId(), mapping.sourceCode(), mapping.productType(),
                        mapping.externalSymbol(), subscription));
            }
        }
        return Map.copyOf(resolved);
    }

    private ResolvedSubscription requireSubscribedMapping(long instrumentId) {
        if (instrumentId <= 0) {
            throw invalid("instrumentId must be positive");
        }
        ResolvedSubscription resolved = subscribedMappings().get(instrumentId);
        if (resolved == null) {
            throw new InvestmentException(InvestmentErrorCode.NOT_FOUND,
                    "Subscribed instrument does not exist: " + instrumentId);
        }
        return resolved;
    }

    private ResolvedSubscription requireCapability(long instrumentId, DataCapability capability) {
        ResolvedSubscription resolved = requireSubscribedMapping(instrumentId);
        if (!resolved.subscription().capabilities().contains(capability)) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    capability + " is not available for instrument " + instrumentId);
        }
        return resolved;
    }

    private InstrumentProjection findInstrument(long instrumentId) {
        List<InstrumentProjection> rows = jdbcTemplate.query("""
                select i.id, v.code as venue_code, i.symbol, i.base_asset, i.quote_asset, i.status,
                       i.settlement_asset, i.margin_asset, i.product_type, i.contract_type, i.launch_time
                from investment_instrument i
                join investment_venue v on v.id = i.venue_id and v.deleted = false
                where i.id = :instrumentId and i.deleted = false
                """, new MapSqlParameterSource("instrumentId", instrumentId), this::mapInstrument);
        if (rows.size() != 1) {
            throw new InvestmentException(InvestmentErrorCode.NOT_FOUND,
                    "Instrument does not exist: " + instrumentId);
        }
        return rows.getFirst();
    }

    private ContractSpecProjection findContractSpec(long instrumentId) {
        List<ContractSpecProjection> rows = jdbcTemplate.query("""
                select price_precision, quantity_precision, price_end_step, quantity_step,
                       contract_multiplier, min_trade_quantity, min_trade_notional,
                       max_market_order_quantity, max_limit_order_quantity, maker_fee_rate, taker_fee_rate,
                       min_leverage, max_leverage, funding_interval_hours, buy_limit_price_ratio,
                       sell_limit_price_ratio, source_updated_at, ingested_at, revision
                from investment_contract_spec where instrument_id = :instrumentId
                """, new MapSqlParameterSource("instrumentId", instrumentId), (resultSet, rowNumber) ->
                new ContractSpecProjection(new InvestmentContractSpecResponse(
                        resultSet.getInt("price_precision"), resultSet.getInt("quantity_precision"),
                        decimal(resultSet.getBigDecimal("price_end_step")),
                        decimal(resultSet.getBigDecimal("quantity_step")),
                        decimal(resultSet.getBigDecimal("contract_multiplier")),
                        decimal(resultSet.getBigDecimal("min_trade_quantity")),
                        decimal(resultSet.getBigDecimal("min_trade_notional")),
                        decimal(resultSet.getBigDecimal("max_market_order_quantity")),
                        decimal(resultSet.getBigDecimal("max_limit_order_quantity")),
                        decimal(resultSet.getBigDecimal("maker_fee_rate")),
                        decimal(resultSet.getBigDecimal("taker_fee_rate")),
                        decimal(resultSet.getBigDecimal("min_leverage")),
                        decimal(resultSet.getBigDecimal("max_leverage")),
                        resultSet.getInt("funding_interval_hours"),
                        decimal(resultSet.getBigDecimal("buy_limit_price_ratio")),
                        decimal(resultSet.getBigDecimal("sell_limit_price_ratio")),
                        instant(resultSet, "source_updated_at"), instant(resultSet, "ingested_at"),
                        resultSet.getLong("revision")), instant(resultSet, "ingested_at")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<QuoteKey, ContractQuoteRow> quotes(List<InstrumentProjection> instruments,
                                                   Map<Long, ResolvedSubscription> subscribed) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        Set<Long> instrumentIds = instruments.stream().map(InstrumentProjection::id).collect(Collectors.toSet());
        Set<Long> sourceIds = instruments.stream().map(InstrumentProjection::id)
                .map(subscribed::get).map(ResolvedSubscription::sourceId).collect(Collectors.toSet());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("instrumentIds", instrumentIds).addValue("sourceIds", sourceIds);
        List<ContractQuoteRow> rows = jdbcTemplate.query("""
                select source_id, instrument_id, last_price, mark_price, index_price, bid_price, ask_price,
                       bid_quantity, ask_quantity, open_24h, high_24h, low_24h, base_volume_24h,
                       quote_volume_24h, change_24h, funding_rate, next_funding_time, open_interest,
                       source_time, received_at, version
                from investment_contract_quote_latest
                where instrument_id in (:instrumentIds) and source_id in (:sourceIds)
                """, parameters, this::mapQuote);
        Map<QuoteKey, ContractQuoteRow> result = new HashMap<>();
        rows.forEach(row -> result.put(new QuoteKey(row.sourceId(), row.instrumentId()), row));
        return Map.copyOf(result);
    }

    private InvestmentInstrumentSummaryResponse summary(InstrumentProjection instrument,
                                                         ResolvedSubscription resolved,
                                                         Map<QuoteKey, ContractQuoteRow> quotes,
                                                         Instant cutoff) {
        ContractQuoteRow quote = quotes.get(new QuoteKey(resolved.sourceId(), instrument.id()));
        return new InvestmentInstrumentSummaryResponse(
                instrument.id(), instrument.venueCode(), instrument.symbol(), instrument.baseAsset(),
                instrument.quoteAsset(), instrument.status(), decimal(quote == null ? null : quote.lastPrice()),
                decimal(quote == null ? null : quote.markPrice()), decimal(quote == null ? null : quote.change24h()),
                cutoff, freshness(quote == null ? null : quote.sourceTime(), cutoff),
                names(resolved.subscription().capabilities()));
    }

    private InvestmentQuoteResponse quoteResponse(ContractQuoteRow row, Instant cutoff) {
        return new InvestmentQuoteResponse(
                row.instrumentId(), decimal(row.lastPrice()), decimal(row.markPrice()), decimal(row.indexPrice()),
                decimal(row.bidPrice()), decimal(row.askPrice()), decimal(row.bidQuantity()),
                decimal(row.askQuantity()), decimal(row.open24h()), decimal(row.high24h()), decimal(row.low24h()),
                decimal(row.baseVolume24h()), decimal(row.quoteVolume24h()), decimal(row.change24h()),
                decimal(row.fundingRate()), row.nextFundingTime(), decimal(row.openInterest()), row.sourceTime(),
                row.receivedAt(), row.version(), cutoff, freshness(row.sourceTime(), cutoff));
    }

    private InvestmentCandleResponse candle(MarketBarIntradayRow row, Instant cutoff) {
        return new InvestmentCandleResponse(
                row.openTime(), row.closeTime(), decimal(row.openPrice()), decimal(row.highPrice()),
                decimal(row.lowPrice()), decimal(row.closePrice()), decimal(row.baseVolume()),
                decimal(row.quoteVolume()), row.closed(), row.revision(), cutoff);
    }

    private InvestmentCandleResponse candle(MarketBarDailyRow row, Instant cutoff) {
        Instant openTime = row.barDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        return new InvestmentCandleResponse(
                openTime, openTime.plus(Duration.ofDays(1)), decimal(row.openPrice()), decimal(row.highPrice()),
                decimal(row.lowPrice()), decimal(row.closePrice()), decimal(row.baseVolume()),
                decimal(row.quoteVolume()), row.closed(), row.revision(), cutoff);
    }

    private InstrumentProjection mapInstrument(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InstrumentProjection(
                resultSet.getLong("id"), resultSet.getString("venue_code"), resultSet.getString("symbol"),
                resultSet.getString("base_asset"), resultSet.getString("quote_asset"),
                resultSet.getString("settlement_asset"), resultSet.getString("margin_asset"),
                resultSet.getString("product_type"), resultSet.getString("contract_type"),
                resultSet.getString("status"), instant(resultSet, "launch_time"));
    }

    private ContractQuoteRow mapQuote(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ContractQuoteRow(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                resultSet.getBigDecimal("last_price"), resultSet.getBigDecimal("mark_price"),
                resultSet.getBigDecimal("index_price"), resultSet.getBigDecimal("bid_price"),
                resultSet.getBigDecimal("ask_price"), resultSet.getBigDecimal("bid_quantity"),
                resultSet.getBigDecimal("ask_quantity"), resultSet.getBigDecimal("open_24h"),
                resultSet.getBigDecimal("high_24h"), resultSet.getBigDecimal("low_24h"),
                resultSet.getBigDecimal("base_volume_24h"), resultSet.getBigDecimal("quote_volume_24h"),
                resultSet.getBigDecimal("change_24h"), resultSet.getBigDecimal("funding_rate"),
                instant(resultSet, "next_funding_time"), resultSet.getBigDecimal("open_interest"),
                instant(resultSet, "source_time"), instant(resultSet, "received_at"),
                resultSet.getLong("version"));
    }

    private InvestmentFreshnessResponse freshness(Instant observedAt, Instant cutoff) {
        if (observedAt == null) {
            return new InvestmentFreshnessResponse("UNAVAILABLE", null, 0);
        }
        long age = Math.max(0, Duration.between(observedAt, cutoff).toSeconds());
        return new InvestmentFreshnessResponse(
                observedAt.isBefore(cutoff.minus(QUOTE_FRESHNESS)) ? "STALE" : "FRESH", observedAt, age);
    }

    private Instant laterCutoff(Instant earlierCutoff) {
        Instant responseCutoff = clock.instant();
        return responseCutoff.isBefore(earlierCutoff) ? earlierCutoff : responseCutoff;
    }

    private boolean visible(Instant factTime, Instant cutoff) {
        return factTime == null || !factTime.isAfter(cutoff);
    }

    private boolean visible(Instant firstFactTime, Instant secondFactTime, Instant cutoff) {
        return visible(firstFactTime, cutoff) && visible(secondFactTime, cutoff);
    }

    private void validateCandleWindow(PriceType priceType, BarInterval interval,
                                      Instant fromInclusive, Instant toExclusive,
                                      Instant dataAsOf, int limit) {
        if (priceType == null || priceType == PriceType.NONE) {
            throw invalid("A concrete priceType is required");
        }
        if (interval == null || interval == BarInterval.NONE) {
            throw invalid("A concrete interval is required");
        }
        if (limit < 1 || limit > MAX_CANDLE_POINTS) {
            throw invalid("Candle limit must be between 1 and " + MAX_CANDLE_POINTS);
        }
        validateTimeWindow(fromInclusive, toExclusive, switch (interval) {
            case D1 -> MAX_DAILY_SPAN;
            case H4 -> MAX_H4_SPAN;
            default -> MAX_INTRADAY_SPAN;
        });
        validateCutoff(dataAsOf);
    }

    private void validateTimeWindow(Instant fromInclusive, Instant toExclusive, Duration maxSpan) {
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)) {
            throw invalid("A valid half-open UTC time range is required");
        }
        if (Duration.between(fromInclusive, toExclusive).compareTo(maxSpan) > 0) {
            throw invalid("Requested time span exceeds the server limit");
        }
    }

    private void validateCutoff(Instant dataAsOf) {
        if (dataAsOf != null && dataAsOf.isAfter(clock.instant())) {
            throw invalid("dataAsOf cannot be in the future");
        }
    }

    private void requireUtcMidnight(Instant instant, String name) {
        if (!instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            throw invalid(name + " must be aligned to UTC midnight for D1 candles");
        }
    }

    private PageWindow pageWindow(int page, int size, int maxSize) {
        if (page < 1 || size < 1 || size > maxSize) {
            throw invalid("Invalid pagination");
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        if (offset > Integer.MAX_VALUE) {
            throw invalid("Pagination offset is too large");
        }
        return new PageWindow(page, size, (int) offset);
    }

    private String instrumentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED", "OFFLINE").contains(normalized)) {
            throw invalid("Unsupported instrument status");
        }
        return normalized;
    }

    private String instrumentOrder(String sort) {
        if (sort == null || sort.isBlank() || "SYMBOL_ASC".equalsIgnoreCase(sort)) {
            return "i.symbol asc, i.id asc";
        }
        if ("SYMBOL_DESC".equalsIgnoreCase(sort)) {
            return "i.symbol desc, i.id desc";
        }
        throw invalid("Unsupported market sort");
    }

    private long requiredCount(String sql, MapSqlParameterSource parameters) {
        Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return count == null ? 0 : count;
    }

    private int totalPages(long total, int size) {
        return Math.toIntExact((total + size - 1) / size);
    }

    private <T> PageResult<T> emptyPage(PageWindow window) {
        return new PageResult<>(List.of(), window.page(), window.size(), 0, 0);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : InvestmentDecimalCodec.format(value);
    }

    private static <T extends Enum<T>> List<String> names(Collection<T> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return resultSet.getTimestamp(column).toInstant();
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException unavailable(String message) {
        return new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE, message);
    }

    private record SubscriptionKey(String sourceCode, ProductType productType, String externalSymbol) {
        private static SubscriptionKey of(MarketSubscription subscription) {
            return new SubscriptionKey(subscription.sourceCode(), subscription.productType(), subscription.symbol());
        }
    }

    private record MappingProjection(long instrumentId, ProductType productType, long sourceId,
                                     String sourceCode, String externalSymbol) {
    }

    private record ResolvedSubscription(long sourceId, String sourceCode, ProductType productType,
                                        String externalSymbol, MarketSubscription subscription) {
    }

    private record InstrumentProjection(long id, String venueCode, String symbol, String baseAsset,
                                        String quoteAsset, String settlementAsset, String marginAsset,
                                        String productType, String contractType, String status, Instant launchTime) {
    }

    private record ContractSpecProjection(InvestmentContractSpecResponse response, Instant ingestedAt) {
    }

    private record PositionTierProjection(int tierLevel, String startNotional, String endNotional,
                                          String maxLeverage, String maintenanceMarginRate,
                                          Instant observedAt, Instant ingestedAt) {
    }

    private record QuoteKey(long sourceId, long instrumentId) {
    }

    private record MarketFactRef(long sourceId, long instrumentId) {
    }

    private record PageWindow(int page, int size, int offset) {
    }
}
