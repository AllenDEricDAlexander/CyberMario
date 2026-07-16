package top.egon.mario.investment.marketdata.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentFundingRateResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentDetailResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentSummaryResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPositionTierResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentQuoteResponse;

import java.time.Instant;
import java.util.List;

/**
 * Public read API for code-subscribed Investment market data.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment/market")
@Validated
public class InvestmentMarketController extends ReactiveInvestmentSupport {

    private final InvestmentMarketQueryService queryService;

    @GetMapping("/instruments")
    public Mono<ApiResponse<PageResult<InvestmentInstrumentSummaryResponse>>> instruments(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "SYMBOL_ASC") String sort) {
        return blocking(() -> queryService.listInstruments(page, size, status, sort));
    }

    @GetMapping("/instruments/{instrumentId}")
    public Mono<ApiResponse<InvestmentInstrumentDetailResponse>> instrument(
            @PathVariable @Min(1) long instrumentId) {
        return blocking(() -> queryService.instrument(instrumentId));
    }

    @GetMapping("/instruments/{instrumentId}/quote")
    public Mono<ApiResponse<InvestmentQuoteResponse>> quote(@PathVariable @Min(1) long instrumentId) {
        return blocking(() -> queryService.quote(instrumentId));
    }

    @GetMapping("/instruments/{instrumentId}/candles")
    public Mono<ApiResponse<List<InvestmentCandleResponse>>> candles(
            @PathVariable @Min(1) long instrumentId,
            @RequestParam PriceType priceType,
            @RequestParam BarInterval interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dataAsOf,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(InvestmentMarketQueryService.MAX_CANDLE_POINTS) int limit) {
        return blocking(() -> queryService.candles(
                instrumentId, priceType, interval, from, to, dataAsOf, limit));
    }

    @GetMapping("/instruments/{instrumentId}/funding-rates")
    public Mono<ApiResponse<PageResult<InvestmentFundingRateResponse>>> fundingRates(
            @PathVariable @Min(1) long instrumentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dataAsOf,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return blocking(() -> queryService.fundingRates(
                instrumentId, from, to, dataAsOf, page, size));
    }

    @GetMapping("/instruments/{instrumentId}/position-tiers")
    public Mono<ApiResponse<List<InvestmentPositionTierResponse>>> positionTiers(
            @PathVariable @Min(1) long instrumentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dataAsOf) {
        return blocking(() -> queryService.positionTiers(instrumentId, dataAsOf));
    }
}
