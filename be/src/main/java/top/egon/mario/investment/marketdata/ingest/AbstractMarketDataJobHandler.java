package top.egon.mario.investment.marketdata.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.marketdata.provider.MarketDataProviderException;
import top.egon.mario.investment.marketdata.provider.ProviderErrorCategory;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Template Method for subscription-fenced fetch, normalization and short page transactions.
 *
 * <p>Provider I/O and normalization deliberately happen before a transaction is opened. Every normalized page is
 * written atomically; a later page can be retried without widening the database transaction around provider I/O.</p>
 */
@Slf4j
public abstract class AbstractMarketDataJobHandler<T> implements InvestmentJobHandler {

    private static final int PROVIDER_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final TransactionTemplate transactionTemplate;
    private final MarketDataDimensionResolver dimensionResolver;
    private final MarketDataQualityService qualityService;
    private final MarketDataJobShapeValidator shapeValidator = new MarketDataJobShapeValidator();

    protected AbstractMarketDataJobHandler(ObjectMapper objectMapper,
                                           InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                           TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.subscriptionRegistry = subscriptionRegistry;
        this.transactionTemplate = transactionTemplate;
        this.dimensionResolver = null;
        this.qualityService = null;
    }

    protected AbstractMarketDataJobHandler(ObjectMapper objectMapper,
                                           InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                           TransactionTemplate transactionTemplate,
                                           MarketDataDimensionResolver dimensionResolver,
                                           MarketDataQualityService qualityService) {
        this.objectMapper = objectMapper;
        this.subscriptionRegistry = subscriptionRegistry;
        this.transactionTemplate = transactionTemplate;
        this.dimensionResolver = dimensionResolver;
        this.qualityService = qualityService;
    }

    @Override
    public final InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        MarketDataJobInput input = readInput(claim.inputJson());
        shapeValidator.validate(claim.jobType(), input);
        if (claim.jobType() != jobType()) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_SHAPE_INVALID",
                    "Claim job type does not match the selected market-data handler");
        }
        try {
            validateSubscription(input);
        } catch (InvestmentException ex) {
            if (ex.getErrorCode() != InvestmentErrorCode.SUBSCRIPTION_REJECTED) {
                throw ex;
            }
            recordSubscriptionRejection(claim, input);
            throw new InvestmentJobNonRetryableException("MARKET_OUT_OF_SUBSCRIPTION", ex.getMessage(), ex);
        }
        input = executionInput(input);
        return providerPaged(input) ? executePaged(claim, input) : executeSingle(claim, input);
    }

    protected abstract List<T> fetch(MarketDataJobInput input);

    protected abstract void validateSubscription(MarketDataJobInput input);

    protected MarketDataJobInput executionInput(MarketDataJobInput input) {
        return input;
    }

    protected void validatePage(MarketDataJobInput input, List<T> page) {
        // Provider models perform structural validation; handlers add cross-record validation when needed.
    }

    protected void validatePage(InvestmentJobClaim claim, MarketDataJobInput input, List<T> page) {
        validatePage(input, page);
    }

    protected boolean providerPaged(MarketDataJobInput input) {
        return false;
    }

    protected Instant nextPageStart(MarketDataJobInput input, List<T> page) {
        throw new UnsupportedOperationException("Provider paging is not configured for " + jobType());
    }

    protected abstract int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<T> page,
                                       List<T> previousPage);

    protected String resultJson(int fetched, int written) {
        return "{\"fetched\":" + fetched + ",\"written\":" + written + "}";
    }

    protected final InvestmentMarketSubscriptionRegistry subscriptionRegistry() {
        return subscriptionRegistry;
    }

    protected final void persistQualityAudit(InvestmentJobClaim claim, MarketDataJobInput input,
                                             List<top.egon.mario.investment.marketdata.quality.MarketDataQualityFinding>
                                                     findings) {
        if (findings.isEmpty()) {
            return;
        }
        if (dimensionResolver == null || qualityService == null) {
            throw new IllegalStateException("Market-data quality auditing is not configured for " + jobType());
        }
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        transactionTemplate.executeWithoutResult(status -> qualityService.persist(claim.id(),
                new MarketDataQualityService.MarketDataDimensionRef(
                        dimension.sourceId(), dimension.instrumentId()), findings));
    }

    private InvestmentJobHandlerResult executeSingle(InvestmentJobClaim claim, MarketDataJobInput input) {
        List<T> normalized = fetchWithRetry(claim, input);
        validatePage(claim, input, normalized);
        Integer written = transactionTemplate.execute(status -> persistPage(claim, input, normalized, List.of()));
        return InvestmentJobHandlerResult.completed(resultJson(normalized.size(), written == null ? 0 : written));
    }

    private InvestmentJobHandlerResult executePaged(InvestmentJobClaim claim, MarketDataJobInput input) {
        Instant pageStart = Objects.requireNonNull(input.startInclusive(), "paged start");
        Instant endExclusive = Objects.requireNonNull(input.endExclusive(), "paged end");
        List<T> previousPage = List.of();
        int fetched = 0;
        int written = 0;
        while (pageStart.isBefore(endExclusive)) {
            MarketDataJobInput pageInput = input.withRange(pageStart, endExclusive);
            List<T> page = fetchWithRetry(claim, pageInput);
            if (page.size() > input.pageSize()) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_PAGE_LIMIT_EXCEEDED",
                        "Provider returned more records than the requested page limit");
            }
            validatePage(claim, pageInput, page);
            if (page.isEmpty()) {
                break;
            }
            List<T> boundary = previousPage;
            Integer pageWritten = transactionTemplate.execute(status -> persistPage(claim, pageInput, page,
                    boundary));
            fetched += page.size();
            written += pageWritten == null ? 0 : pageWritten;
            if (page.size() < input.pageSize()) {
                break;
            }
            Instant advanced = nextPageStart(pageInput, page);
            if (!advanced.isAfter(pageStart)) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_PAGE_NO_PROGRESS",
                        "Provider page did not advance the half-open time range");
            }
            previousPage = page;
            pageStart = advanced;
        }
        return InvestmentJobHandlerResult.completed(resultJson(fetched, written));
    }

    private List<T> fetchWithRetry(InvestmentJobClaim claim, MarketDataJobInput input) {
        for (int attempt = 1; attempt <= PROVIDER_ATTEMPTS; attempt++) {
            try {
                return List.copyOf(Objects.requireNonNull(fetch(input), "provider result"));
            } catch (MarketDataProviderException ex) {
                if (!ex.isRetryable()) {
                    if (ex.getCategory() == ProviderErrorCategory.INVALID_DATA) {
                        recordInvalidProviderData(claim, input, new IllegalArgumentException(ex.getMessage(), ex));
                    }
                    throw nonRetryable(ex);
                }
                if (attempt == PROVIDER_ATTEMPTS) {
                    throw new InvestmentJobRetryableException("MARKET_PROVIDER_" + ex.getCategory(),
                            "Market-data provider failed after " + PROVIDER_ATTEMPTS + " attempts", ex);
                }
            } catch (InvestmentException ex) {
                if (ex.getErrorCode() != InvestmentErrorCode.CAPABILITY_UNAVAILABLE) {
                    throw ex;
                }
                recordSubscriptionRejection(claim, input);
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_CAPABILITY_UNAVAILABLE",
                        ex.getMessage(), ex);
            } catch (NullPointerException | IllegalArgumentException ex) {
                recordInvalidProviderData(claim, input, ex);
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_INVALID_DATA",
                        "Provider returned invalid normalized data", ex);
            }
        }
        throw new IllegalStateException("Provider retry loop exhausted unexpectedly");
    }

    private void recordSubscriptionRejection(InvestmentJobClaim claim, MarketDataJobInput input) {
        if (dimensionResolver == null || qualityService == null) {
            return;
        }
        try {
            MarketDataDimension dimension = dimensionResolver.resolve(input);
            transactionTemplate.executeWithoutResult(status -> qualityService.persist(claim.id(),
                    new MarketDataQualityService.MarketDataDimensionRef(
                            dimension.sourceId(), dimension.instrumentId()),
                    List.of(qualityService.outOfSubscription(claim.claimedAt(), input))));
        } catch (InvestmentJobNonRetryableException | InvestmentJobRetryableException ex) {
            LogUtil.warn(log).log("investment subscription audit skipped before catalog dimension exists, "
                            + "jobId={}, errorCode={}", claim.id(), errorCode(ex));
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment subscription audit failed, jobId={}, error={}", claim.id(),
                    message(ex));
        }
    }

    private void recordInvalidProviderData(InvestmentJobClaim claim, MarketDataJobInput input,
                                           RuntimeException exception) {
        if (dimensionResolver == null || qualityService == null) {
            return;
        }
        try {
            var finding = qualityService.invalidProviderData(input, claim.claimedAt(), exception.getMessage());
            if (finding == null) {
                return;
            }
            MarketDataDimension dimension = dimensionResolver.resolve(input);
            transactionTemplate.executeWithoutResult(status -> qualityService.persist(claim.id(),
                    new MarketDataQualityService.MarketDataDimensionRef(
                            dimension.sourceId(), dimension.instrumentId()), List.of(finding)));
        } catch (InvestmentJobNonRetryableException | InvestmentJobRetryableException ex) {
            LogUtil.warn(log).log("investment invalid-data audit skipped before catalog dimension exists, "
                    + "jobId={}, errorCode={}", claim.id(), errorCode(ex));
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment invalid-data audit failed, jobId={}, error={}", claim.id(), message(ex));
        }
    }

    private InvestmentJobNonRetryableException nonRetryable(MarketDataProviderException exception) {
        String code = exception.getCategory() == ProviderErrorCategory.INVALID_DATA
                ? "MARKET_PROVIDER_INVALID_DATA" : "MARKET_PROVIDER_NON_RETRYABLE";
        return new InvestmentJobNonRetryableException(code, exception.getMessage(), exception);
    }

    private MarketDataJobInput readInput(String inputJson) {
        try {
            return objectMapper.readValue(inputJson, MarketDataJobInput.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_INPUT_INVALID",
                    "Invalid market-data job input", ex);
        }
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof InvestmentJobNonRetryableException nonRetryable) {
            return nonRetryable.errorCode();
        }
        return ((InvestmentJobRetryableException) ex).errorCode();
    }

    private String message(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
