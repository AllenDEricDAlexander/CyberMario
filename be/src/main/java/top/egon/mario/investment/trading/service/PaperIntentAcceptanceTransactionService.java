package top.egon.mario.investment.trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.po.InvestmentRiskProfilePo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentRiskProfileRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskEvaluation;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskService;
import top.egon.mario.investment.trading.matching.PaperMatchJobInput;
import top.egon.mario.investment.trading.matching.model.TradeSide;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentRiskCheckPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentRiskCheckRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.model.PaperAcceptanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Short account-locking transaction that recomputes risk and atomically accepts or rejects one intent.
 */
@Service
public class PaperIntentAcceptanceTransactionService {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentRiskProfileRepository riskProfileRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentPaperOrderRepository orderRepository;
    private final InvestmentTradeIntentRepository intentRepository;
    private final InvestmentRiskCheckRepository riskCheckRepository;
    private final InvestmentMarginLedgerRepository ledgerRepository;
    private final InvestmentRiskService riskService;
    private final InvestmentJobEnqueueService enqueueService;
    private final PaperOrderService orderService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperIntentAcceptanceTransactionService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentRiskProfileRepository riskProfileRepository,
            InvestmentPositionRepository positionRepository,
            InvestmentPaperOrderRepository orderRepository,
            InvestmentTradeIntentRepository intentRepository,
            InvestmentRiskCheckRepository riskCheckRepository,
            InvestmentMarginLedgerRepository ledgerRepository,
            InvestmentRiskService riskService,
            InvestmentJobEnqueueService enqueueService,
            PaperOrderService orderService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.intentRepository = intentRepository;
        this.riskCheckRepository = riskCheckRepository;
        this.ledgerRepository = ledgerRepository;
        this.riskService = riskService;
        this.enqueueService = enqueueService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PaperTradeResult accept(
            long workspaceId, PaperTradeCommand command, PaperAcceptanceMarketSnapshot snapshot) {
        InvestmentPaperAccountPo account = accountRepository
                .findByIdAndWorkspaceIdForUpdate(command.accountId(), workspaceId)
                .orElseThrow(PaperIntentAcceptanceTransactionService::forbidden);
        PaperTradeResult existing = orderService
                .findByIdempotencyKey(command.accountId(), command.idempotencyKey()).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(account.getId());
        List<InvestmentPaperOrderPo> pendingOpeningOrders =
                orderRepository.findPendingOpeningByAccountIdForUpdate(account.getId());
        Map<Long, BigDecimal> pendingNotionalByIntent = pendingNotionalByIntent(pendingOpeningOrders);
        InvestmentRiskProfilePo profile = riskProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new InvestmentException(
                        InvestmentErrorCode.DATA_UNAVAILABLE, "Paper account risk profile is unavailable"));
        Instant now = clock.instant();
        BigDecimal requestedNotional = requestedNotional(command, snapshot);
        InvestmentTradeIntentPo intent = intentRepository.saveAndFlush(
                newIntent(workspaceId, command, requestedNotional));

        InvestmentRiskEvaluation evaluation = riskService.evaluate(context(
                account, profile, command, snapshot, positions, pendingOpeningOrders,
                pendingNotionalByIntent, requestedNotional, now));
        persistRiskResults(intent.getId(), evaluation, now);
        intent.setRiskCheckedAt(evaluation.results().getFirst().checkedAt());
        if (!evaluation.passed()) {
            intent.setStatus("RISK_REJECTED");
            intentRepository.saveAndFlush(intent);
            return new PaperTradeResult(intent.getId(), intent.getStatus(), evaluation.results(), null, null);
        }

        intent.setStatus("ACCEPTED");
        intent.setAcceptedAt(now);
        intentRepository.saveAndFlush(intent);
        InvestmentPaperOrderPo order = orderRepository.saveAndFlush(newOrder(intent, command, now));
        enqueueMatchJob(workspaceId, order, snapshot, command.dataAsOf(), now);
        return new PaperTradeResult(intent.getId(), intent.getStatus(), evaluation.results(),
                new PaperOrderSummary(order.getId(), order.getStatus(), order.getSubmittedAt(), null), null);
    }

    private InvestmentRiskContext context(
            InvestmentPaperAccountPo account,
            InvestmentRiskProfilePo profile,
            PaperTradeCommand command,
            PaperAcceptanceMarketSnapshot snapshot,
            List<InvestmentPositionPo> positions,
            List<InvestmentPaperOrderPo> pendingOrders,
            Map<Long, BigDecimal> pendingNotionalByIntent,
            BigDecimal requestedNotional,
            Instant now) {
        InvestmentRiskLimits accountLimits = limits(profile);
        InvestmentRiskLimits marketLimits = marketLimits(accountLimits, snapshot).tightenedBy(command.callerLimits());
        InvestmentPositionPo current = positions.stream()
                .filter(position -> position.getInstrumentId().equals(command.instrumentId()))
                .findFirst().orElse(null);
        BigDecimal currentNotional = notional(current == null ? BigDecimal.ZERO : current.getQuantity(), snapshot);
        BigDecimal pendingSameInstrumentNotional = pendingOrders.stream()
                .filter(order -> order.getInstrumentId().equals(command.instrumentId()))
                .map(order -> pendingNotionalByIntent.getOrDefault(order.getIntentId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resultingPositionNotional = resultingPositionNotional(
                command, current, currentNotional, pendingSameInstrumentNotional, requestedNotional);
        BigDecimal existingGross = positions.stream()
                .map(position -> existingPositionNotional(position, command.instrumentId(), snapshot))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingGross = pendingNotionalByIntent.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = existingGross.subtract(currentNotional)
                .add(resultingPositionNotional).add(pendingGross.subtract(pendingSameInstrumentNotional))
                .max(BigDecimal.ZERO);
        long openPositions = resultingOpenPositions(command, current, positions, pendingOrders);
        BigDecimal usedMargin = positions.stream().map(InvestmentPositionPo::getIsolatedMargin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reservedMargin = pendingOrders.stream()
                .map(order -> pendingNotionalByIntent.getOrDefault(order.getIntentId(), BigDecimal.ZERO)
                        .divide(order.getLeverage(), MATH))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = account.getWalletBalance().subtract(usedMargin).subtract(reservedMargin);
        BigDecimal requiredMargin = requestedNotional.divide(command.leverage(), MATH);
        long ordersLastHour = orderRepository.countByAccountIdAndSubmittedAtGreaterThanEqualAndDeletedFalse(
                account.getId(), now.minusSeconds(3600));
        Long secondsSinceLastOrder = orderRepository
                .findFirstByAccountIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(account.getId())
                .map(order -> Math.max(0L, Duration.between(order.getSubmittedAt(), now).getSeconds()))
                .orElse(null);
        Instant startOfDay = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal dailyLoss = ledgerRepository.sumDailyLoss(account.getId(), startOfDay);
        Long age = snapshot.quoteSourceTime() == null ? null
                : Duration.between(snapshot.quoteSourceTime(), command.dataAsOf()).getSeconds();
        boolean reduceValid = reduceOnlyValid(command, current);
        return new InvestmentRiskContext(
                command.source(),
                new InvestmentRiskContext.AccountState(
                        account.isTradingEnabled(), account.isAgentAutoTradeEnabled()),
                new InvestmentRiskContext.MarketState(
                        snapshot.subscribed(), snapshot.tradable() && snapshot.completeContractTerms(), age,
                        snapshot.markPrice(), snapshot.tierAvailable(), snapshot.fundingAvailable()),
                new InvestmentRiskContext.OrderState(
                        requestedNotional, command.leverage(), ordersLastHour, secondsSinceLastOrder,
                        requiredMargin, snapshot.slippageBps(), command.reduceOnly(), reduceValid),
                new InvestmentRiskContext.PortfolioState(
                        resultingPositionNotional, gross, openPositions,
                        dailyLoss == null ? BigDecimal.ZERO : dailyLoss, BigDecimal.ZERO, available),
                accountLimits,
                marketLimits);
    }

    private static InvestmentRiskLimits limits(InvestmentRiskProfilePo profile) {
        return new InvestmentRiskLimits(
                profile.getMaxLeverage(), profile.getMaxOrderNotional(), profile.getMaxPositionNotional(),
                profile.getMaxGrossExposureNotional(), profile.getMaxOpenPositions(),
                profile.getMaxDailyLossAmount(), profile.getMaxDrawdownRatio(), profile.getMaxOrdersPerHour(),
                profile.getCooldownSeconds(), profile.getMaxMarketDataAgeSeconds(), profile.getMaxSlippageBps());
    }

    private static InvestmentRiskLimits marketLimits(
            InvestmentRiskLimits accountLimits, PaperAcceptanceMarketSnapshot snapshot) {
        BigDecimal leverage = accountLimits.maxLeverage();
        if (snapshot.contractMaxLeverage() != null && snapshot.contractMaxLeverage().signum() > 0) {
            leverage = leverage.min(snapshot.contractMaxLeverage());
        }
        if (snapshot.tierMaxLeverage() != null && snapshot.tierMaxLeverage().signum() > 0) {
            leverage = leverage.min(snapshot.tierMaxLeverage());
        }
        return new InvestmentRiskLimits(
                leverage, accountLimits.maxOrderNotional(), accountLimits.maxPositionNotional(),
                accountLimits.maxGrossExposureNotional(), accountLimits.maxOpenPositions(),
                accountLimits.maxDailyLossAmount(), accountLimits.maxDrawdownRatio(),
                accountLimits.maxOrdersPerHour(), accountLimits.cooldownSeconds(),
                accountLimits.maxMarketDataAgeSeconds(), accountLimits.maxSlippageBps());
    }

    private static BigDecimal requestedNotional(
            PaperTradeCommand command, PaperAcceptanceMarketSnapshot snapshot) {
        if (snapshot.markPrice() == null || snapshot.markPrice().signum() <= 0
                || snapshot.contractMultiplier() == null || snapshot.contractMultiplier().signum() <= 0) {
            return command.requestedNotional();
        }
        return command.quantity().multiply(snapshot.markPrice())
                .multiply(snapshot.contractMultiplier()).abs();
    }

    private static BigDecimal notional(BigDecimal quantity, PaperAcceptanceMarketSnapshot snapshot) {
        if (quantity == null || snapshot.markPrice() == null || snapshot.contractMultiplier() == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(snapshot.markPrice()).multiply(snapshot.contractMultiplier()).abs();
    }

    private static BigDecimal resultingPositionNotional(
            PaperTradeCommand command, InvestmentPositionPo current,
            BigDecimal currentNotional, BigDecimal pendingSameInstrumentNotional,
            BigDecimal requestedNotional) {
        if (command.positionAction() == PositionAction.OPEN) {
            return currentNotional.add(pendingSameInstrumentNotional).add(requestedNotional);
        }
        if (current == null || current.getQuantity().signum() <= 0) {
            return pendingSameInstrumentNotional;
        }
        BigDecimal remainingRatio = current.getQuantity().subtract(command.quantity())
                .max(BigDecimal.ZERO).divide(current.getQuantity(), MATH);
        return currentNotional.multiply(remainingRatio).add(pendingSameInstrumentNotional);
    }

    private static long resultingOpenPositions(
            PaperTradeCommand command, InvestmentPositionPo current,
            List<InvestmentPositionPo> positions, List<InvestmentPaperOrderPo> pendingOrders) {
        Set<Long> instrumentIds = new HashSet<>();
        positions.forEach(position -> instrumentIds.add(position.getInstrumentId()));
        pendingOrders.forEach(order -> instrumentIds.add(order.getInstrumentId()));
        if (command.positionAction() == PositionAction.OPEN) {
            instrumentIds.add(command.instrumentId());
        }
        if (command.positionAction() != PositionAction.OPEN && current != null
                && command.quantity().compareTo(current.getQuantity()) >= 0
                && pendingOrders.stream().noneMatch(order -> order.getInstrumentId().equals(command.instrumentId()))) {
            instrumentIds.remove(command.instrumentId());
        }
        return instrumentIds.size();
    }

    private Map<Long, BigDecimal> pendingNotionalByIntent(List<InvestmentPaperOrderPo> pendingOrders) {
        if (pendingOrders.isEmpty()) {
            return Map.of();
        }
        List<Long> intentIds = pendingOrders.stream().map(InvestmentPaperOrderPo::getIntentId).toList();
        Map<Long, BigDecimal> notionals = new HashMap<>();
        intentRepository.findAllById(intentIds)
                .forEach(intent -> notionals.put(intent.getId(), intent.getRequestedNotional()));
        if (notionals.size() != intentIds.size()) {
            throw new InvestmentException(
                    InvestmentErrorCode.DATA_UNAVAILABLE, "Pending paper order intent snapshot is unavailable");
        }
        return Map.copyOf(notionals);
    }

    private static BigDecimal existingPositionNotional(
            InvestmentPositionPo position, long requestedInstrumentId,
            PaperAcceptanceMarketSnapshot snapshot) {
        if (position.getInstrumentId() == requestedInstrumentId) {
            return notional(position.getQuantity(), snapshot);
        }
        return position.getIsolatedMargin().multiply(position.getLeverage()).abs();
    }

    private static boolean reduceOnlyValid(PaperTradeCommand command, InvestmentPositionPo current) {
        if (!command.reduceOnly()) {
            return command.positionAction() == PositionAction.OPEN
                    && (current == null || current.getPositionSide().equals(command.positionSide().name()));
        }
        return current != null && current.getPositionSide().equals(command.positionSide().name())
                && command.quantity().compareTo(current.getQuantity()) <= 0;
    }

    private InvestmentTradeIntentPo newIntent(
            long workspaceId, PaperTradeCommand command, BigDecimal requestedNotional) {
        InvestmentTradeIntentPo intent = new InvestmentTradeIntentPo();
        intent.setWorkspaceId(workspaceId);
        intent.setAccountId(command.accountId());
        intent.setInstrumentId(command.instrumentId());
        intent.setSourceType(command.source().name());
        intent.setSourceReferenceId(command.sourceReferenceId());
        intent.setIdempotencyKey(PaperOrderService.scopedIdempotencyKey(
                command.accountId(), command.idempotencyKey()));
        intent.setPositionAction(command.positionAction().name());
        intent.setSide(tradeSide(command).name());
        intent.setOrderType(command.orderType().name());
        intent.setQuantity(command.quantity());
        intent.setRequestedNotional(requestedNotional);
        intent.setLeverage(command.leverage());
        intent.setLimitPrice(command.limitPrice());
        intent.setReduceOnly(command.reduceOnly());
        intent.setReason(command.reason());
        intent.setDataAsOf(command.dataAsOf());
        intent.setStatus("RECEIVED");
        intent.setExpiresAt(command.expiresAt());
        return intent;
    }

    private InvestmentPaperOrderPo newOrder(
            InvestmentTradeIntentPo intent, PaperTradeCommand command, Instant now) {
        InvestmentPaperOrderPo order = new InvestmentPaperOrderPo();
        order.setWorkspaceId(intent.getWorkspaceId());
        order.setAccountId(intent.getAccountId());
        order.setIntentId(intent.getId());
        order.setClientOrderId("PAPER-" + intent.getId());
        order.setInstrumentId(intent.getInstrumentId());
        order.setOrigin(command.source().name());
        order.setPositionAction(command.positionAction().name());
        order.setSide(tradeSide(command).name());
        order.setOrderType(command.orderType().name());
        order.setTimeInForce(command.orderType() == top.egon.mario.investment.common.model.OrderType.MARKET
                ? "IOC" : "GTC");
        order.setQuantity(command.quantity());
        order.setRemainingQuantity(command.quantity());
        order.setLeverage(command.leverage());
        order.setLimitPrice(command.limitPrice());
        order.setReduceOnly(command.reduceOnly());
        order.setStatus("PENDING_MATCH");
        order.setSubmittedAt(now);
        return order;
    }

    private void persistRiskResults(Long intentId, InvestmentRiskEvaluation evaluation, Instant now) {
        List<InvestmentRiskCheckPo> values = evaluation.results().stream().map(result -> {
            InvestmentRiskCheckPo value = new InvestmentRiskCheckPo();
            value.setIntentId(intentId);
            value.setRuleCode(result.ruleCode().name());
            value.setPassed(result.passed());
            value.setObservedValue(result.observedValue());
            value.setLimitValue(result.limitValue());
            value.setMessage(result.message());
            value.setDetailsJson(json(result.details()));
            value.setCheckedAt(result.checkedAt());
            value.setCreatedAt(now);
            return value;
        }).toList();
        riskCheckRepository.saveAllAndFlush(values);
    }

    private void enqueueMatchJob(
            long workspaceId, InvestmentPaperOrderPo order, PaperAcceptanceMarketSnapshot snapshot,
            Instant eligibleAfter, Instant now) {
        PaperMatchJobInput input = new PaperMatchJobInput(
                order.getId(), workspaceId, order.getAccountId(), order.getInstrumentId(), snapshot.sourceId(),
                eligibleAfter, snapshot.priceStep(), snapshot.quantityStep(), snapshot.contractMultiplier(),
                snapshot.makerFeeRate(), snapshot.takerFeeRate(), snapshot.slippageBps(),
                snapshot.maintenanceMarginRate());
        enqueueService.enqueue(new InvestmentJobEnqueueCommand(
                workspaceId, InvestmentJobType.PAPER_MATCH, 50, now, 5,
                "paper-match:" + order.getId(), json(input)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.INTERNAL_ERROR, "Unable to serialize paper trading snapshot", exception);
        }
    }

    private static TradeSide tradeSide(PaperTradeCommand command) {
        boolean opening = command.positionAction() == PositionAction.OPEN;
        if (command.positionSide() == PositionSide.LONG) {
            return opening ? TradeSide.BUY : TradeSide.SELL;
        }
        return opening ? TradeSide.SELL : TradeSide.BUY;
    }

    private static InvestmentException forbidden() {
        return new InvestmentException(InvestmentErrorCode.FORBIDDEN, "Paper account access denied");
    }
}
