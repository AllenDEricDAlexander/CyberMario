package top.egon.mario.investment.quant.web.dto;

import java.time.Instant;

public record InvestmentBacktestRunResponse(
        Long runId, Long workspaceId, Long jobId, Long strategyReleaseId, Long datasetSnapshotId,
        String status, String initialEquity, String totalReturn, String annualizedReturn,
        String maxDrawdown, String sharpeRatio, String sortinoRatio, String winRate,
        String profitFactor, String turnover, Long tradeCount, String totalFee,
        String totalFunding, Long liquidationCount, String errorCode, String errorMessage,
        Instant startedAt, Instant finishedAt, Instant createdAt) {
}
