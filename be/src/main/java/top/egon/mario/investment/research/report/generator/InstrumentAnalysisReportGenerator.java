package top.egon.mario.investment.research.report.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentDetailResponse;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorPoint;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorSnapshot;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instrument report composed from the market facade and deterministic indicator service.
 */
@Component
public class InstrumentAnalysisReportGenerator implements InvestmentResearchReportGenerator {

    private final InvestmentMarketQueryService marketQueryService;
    private final InvestmentIndicatorService indicatorService;
    private final ResearchEvidenceSourceService evidenceSourceService;
    private final ObjectMapper objectMapper;

    public InstrumentAnalysisReportGenerator(InvestmentMarketQueryService marketQueryService,
                                             InvestmentIndicatorService indicatorService,
                                             ResearchEvidenceSourceService evidenceSourceService,
                                             ObjectMapper objectMapper) {
        this.marketQueryService = marketQueryService;
        this.indicatorService = indicatorService;
        this.evidenceSourceService = evidenceSourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.INSTRUMENT_ANALYSIS;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        FrozenResearchReportInput input = context.input();
        InvestmentInstrumentDetailResponse instrument = marketQueryService.instrument(input.instrumentId());
        InvestmentIndicatorSnapshot indicators = indicatorService.calculate(
                input.instrumentId(), input.priceType(), input.interval(), input.fromInclusive(),
                input.toExclusive(), input.dataAsOf());
        InvestmentIndicatorPoint latest = indicators.points().getLast();
        ResearchEvidenceSource source = evidenceSourceService.requireInstrumentSource(input.instrumentId());
        Map<String, Object> metrics = metrics(instrument, latest, indicators.points().size());
        ResearchReportEvidenceDraft evidence = new ResearchReportEvidenceDraft(
                "CLOSED_CANDLE_INDICATORS", source.sourceId(), input.instrumentId(), indicators.dataStartTime(),
                indicators.dataEndTime(), input.dataAsOf(),
                "source:%d/instrument:%d/%s/%s".formatted(source.sourceId(), input.instrumentId(),
                        input.priceType(), input.interval()), indicators.inputHash(),
                json(Map.of("sourceCode", source.sourceCode(), "externalSymbol", source.externalSymbol(),
                        "priceType", input.priceType().name(), "interval", input.interval().name(),
                        "revisions", indicators.revisions())));
        String markdown = """
                # %s 合约分析

                - 数据截止：%s
                - K 线范围：%s 至 %s
                - 价格类型 / 周期：%s / %s
                - 最新收盘价：%s
                - SMA20：%s
                - EMA20：%s
                - RSI14：%s
                - MACD：%s
                - ATR14：%s

                指标仅使用数据截止时间内的已关闭 K 线计算。
                """.formatted(instrument.symbol(), input.dataAsOf(), indicators.dataStartTime(),
                indicators.dataEndTime(), input.priceType(), input.interval(), value(latest.close()),
                value(latest.sma20()), value(latest.ema20()), value(latest.rsi14()), value(latest.macd()),
                value(latest.atr14()));
        String summary = "%s %s/%s 已关闭 K 线分析，共 %d 个数据点。".formatted(
                instrument.symbol(), input.priceType(), input.interval(), indicators.points().size());
        return new GeneratedResearchReport(instrument.symbol() + " 合约分析", summary, markdown,
                metrics, indicators, List.of(evidence));
    }

    private Map<String, Object> metrics(InvestmentInstrumentDetailResponse instrument,
                                        InvestmentIndicatorPoint latest, int pointCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("instrumentId", instrument.instrumentId());
        metrics.put("symbol", instrument.symbol());
        metrics.put("pointCount", pointCount);
        metrics.put("close", latest.close());
        metrics.put("sma20", latest.sma20());
        metrics.put("ema20", latest.ema20());
        metrics.put("rsi14", latest.rsi14());
        metrics.put("macd", latest.macd());
        metrics.put("macdSignal", latest.macdSignal());
        metrics.put("macdHistogram", latest.macdHistogram());
        metrics.put("bollingerUpper", latest.bollingerUpper());
        metrics.put("bollingerMiddle", latest.bollingerMiddle());
        metrics.put("bollingerLower", latest.bollingerLower());
        metrics.put("atr14", latest.atr14());
        return metrics;
    }

    private String value(String value) {
        return value == null ? "不可用" : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize instrument report evidence", exception);
        }
    }
}
