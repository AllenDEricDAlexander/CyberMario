package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobShapeValidator;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityFinding;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reconciles freshness and mandatory contract-input availability from database facts.
 */
@Component
@Slf4j
public class DataQualityCheckJobHandler implements InvestmentJobHandler {

    private final ObjectMapper objectMapper;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final MarketDataDimensionResolver dimensionResolver;
    private final ContractQuoteJdbcRepository quoteRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MarketDataQualityService qualityService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MarketDataJobShapeValidator shapeValidator = new MarketDataJobShapeValidator();

    public DataQualityCheckJobHandler(ObjectMapper objectMapper,
                                      InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                      MarketDataDimensionResolver dimensionResolver,
                                      ContractQuoteJdbcRepository quoteRepository,
                                      NamedParameterJdbcTemplate jdbcTemplate,
                                      MarketDataQualityService qualityService,
                                      TransactionTemplate transactionTemplate,
                                      Clock clock) {
        this.objectMapper = objectMapper;
        this.subscriptionRegistry = subscriptionRegistry;
        this.dimensionResolver = dimensionResolver;
        this.quoteRepository = quoteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.qualityService = qualityService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.DATA_QUALITY_CHECK;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        MarketDataJobInput input = input(claim.inputJson());
        shapeValidator.validate(claim.jobType(), input);
        MarketSubscription subscription;
        try {
            subscription = subscriptionRegistry.requireCapability(input.sourceCode(),
                    input.productType(), input.symbol(), input.capability());
        } catch (InvestmentException ex) {
            if (ex.getErrorCode() != InvestmentErrorCode.SUBSCRIPTION_REJECTED) {
                throw ex;
            }
            auditRevoked(claim, input);
            throw new InvestmentJobNonRetryableException("MARKET_OUT_OF_SUBSCRIPTION", ex.getMessage(), ex);
        }
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        Instant now = clock.instant();
        List<MarketDataQualityFinding> findings = new ArrayList<>();
        Optional<ContractQuoteRow> quote = quoteRepository.findLatest(dimension.sourceId(), dimension.instrumentId());
        boolean requireFunding = subscription.capabilities().contains(DataCapability.FUNDING_RATE);
        boolean requireTier = subscription.capabilities().contains(DataCapability.POSITION_TIER);
        boolean fundingPresent = !requireFunding || count("investment_funding_rate", dimension) > 0;
        boolean tierPresent = !requireTier || count("investment_position_tier", dimension) > 0;
        findings.addAll(qualityService.missingQuoteInputs(now, subscription.capabilities(),
                quote.map(ContractQuoteRow::markPrice).orElse(null),
                quote.map(ContractQuoteRow::openInterest).orElse(null)));
        findings.addAll(qualityService.missingContractInputs(now, true, fundingPresent, tierPresent));
        quote.map(ContractQuoteRow::sourceTime)
                .map(sourceTime -> qualityService.staleQuote(sourceTime, now.minus(Duration.ofMinutes(2))))
                .ifPresent(findings::add);
        transactionTemplate.executeWithoutResult(status -> qualityService.persist(claim.id(),
                new MarketDataQualityService.MarketDataDimensionRef(dimension.sourceId(), dimension.instrumentId()),
                findings));
        return InvestmentJobHandlerResult.completed("{\"issues\":" + findings.size() + "}");
    }

    private void auditRevoked(InvestmentJobClaim claim, MarketDataJobInput input) {
        try {
            MarketDataDimension dimension = dimensionResolver.resolve(input);
            transactionTemplate.executeWithoutResult(status -> qualityService.persist(claim.id(),
                    new MarketDataQualityService.MarketDataDimensionRef(dimension.sourceId(), dimension.instrumentId()),
                    List.of(qualityService.outOfSubscription(claim.claimedAt(), input))));
        } catch (InvestmentJobNonRetryableException | InvestmentJobRetryableException ex) {
            LogUtil.warn(log).log("investment quality subscription audit skipped before catalog dimension exists, "
                    + "jobId={}, error={}", claim.id(), ex.getMessage());
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment quality subscription audit failed, jobId={}, error={}",
                    claim.id(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private int count(String table, MarketDataDimension dimension) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table
                        + " where source_id = :sourceId and instrument_id = :instrumentId",
                Map.of("sourceId", dimension.sourceId(), "instrumentId", dimension.instrumentId()), Integer.class);
        return count == null ? 0 : count;
    }

    private MarketDataJobInput input(String json) {
        try {
            return objectMapper.readValue(json, MarketDataJobInput.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_INPUT_INVALID",
                    "Invalid market-data quality job input", ex);
        }
    }
}
