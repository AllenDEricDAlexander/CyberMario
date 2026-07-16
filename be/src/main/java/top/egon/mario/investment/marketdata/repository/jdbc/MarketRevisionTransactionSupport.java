package top.egon.mario.investment.marketdata.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentIngestCursorRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;

import java.time.Instant;
import java.util.Map;

/**
 * Shared JPA seam for cursor fencing and quality facts around JDBC revision writes.
 */
@Component
class MarketRevisionTransactionSupport {

    static final String DAILY_BAR = "BAR_DAILY";
    static final String INTRADAY_BAR = "BAR_INTRADAY";
    static final String FUNDING_RATE = "FUNDING_RATE";

    private final InvestmentIngestCursorRepository cursorRepository;
    private final InvestmentDataQualityIssueRepository qualityIssueRepository;
    private final ObjectMapper objectMapper;

    MarketRevisionTransactionSupport(InvestmentIngestCursorRepository cursorRepository,
                                     InvestmentDataQualityIssueRepository qualityIssueRepository,
                                     ObjectMapper objectMapper) {
        this.cursorRepository = cursorRepository;
        this.qualityIssueRepository = qualityIssueRepository;
        this.objectMapper = objectMapper;
    }

    InvestmentIngestCursorPo lockCursor(long sourceId, long instrumentId, String dataType,
                                        PriceType priceType, BarInterval interval) {
        return cursorRepository.findDimensionForUpdate(sourceId, instrumentId, dataType, priceType, interval)
                .orElseThrow(() -> new IllegalStateException("Ingestion cursor does not exist for "
                        + dataType + ":" + sourceId + ":" + instrumentId + ":" + priceType + ":" + interval));
    }

    void completeCursor(InvestmentIngestCursorPo cursor, MarketDataWriteContext context, String checksum) {
        cursor.setNextStartTime(context.nextStartTime());
        cursor.setLastSuccessTime(context.effectiveAt());
        cursor.setLastChecksum(checksum);
        cursor.setStatus("SUCCEEDED");
        cursor.setLastError(null);
        cursor.setUpdatedAt(context.effectiveAt());
    }

    void recordRevision(MarketDataWriteContext context, long sourceId, long instrumentId,
                        String dataType, PriceType priceType, BarInterval interval, Instant pointTime,
                        long oldRevision, String oldChecksum, long newRevision, String newChecksum) {
        InvestmentDataQualityIssuePo issue = new InvestmentDataQualityIssuePo();
        issue.setJobId(context.jobId());
        issue.setSourceId(sourceId);
        issue.setInstrumentId(instrumentId);
        issue.setDataType(dataType);
        issue.setPriceType(priceType);
        issue.setInterval(interval);
        issue.setPointTime(pointTime);
        issue.setIssueCode("UNEXPECTED_REVISION");
        issue.setSeverity("WARNING");
        issue.setDetailsJson(json(Map.of(
                "oldRevision", oldRevision,
                "oldChecksum", oldChecksum,
                "newRevision", newRevision,
                "newChecksum", newChecksum
        )));
        issue.setResolutionStatus("OPEN");
        qualityIssueRepository.save(issue);
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode market-data revision details", ex);
        }
    }
}
