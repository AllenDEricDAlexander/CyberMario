package top.egon.mario.investment.trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.matching.PaperMatchJobInput;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchStatus;
import top.egon.mario.investment.trading.po.InvestmentMarginLedgerPo;
import top.egon.mario.investment.trading.po.InvestmentPaperFillPo;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperFillRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.model.PaperExecutionResult;
import top.egon.mario.investment.trading.service.model.PaperFillSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Short financial transaction using the fixed account-position-order lock order.
 */
@Service
public class PaperExecutionTransactionService {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentPaperOrderRepository orderRepository;
    private final InvestmentPaperFillRepository fillRepository;
    private final InvestmentMarginLedgerRepository ledgerRepository;
    private final InvestmentTradeIntentRepository intentRepository;
    private final ObjectMapper objectMapper;

    public PaperExecutionTransactionService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            InvestmentPaperOrderRepository orderRepository,
            InvestmentPaperFillRepository fillRepository,
            InvestmentMarginLedgerRepository ledgerRepository,
            InvestmentTradeIntentRepository intentRepository,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.ledgerRepository = ledgerRepository;
        this.intentRepository = intentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Appends one fill and its exact financial effects or returns the already committed fill.
     */
    @Transactional
    public PaperExecutionResult execute(PaperMatchJobInput input, MatchResult match, Instant executedAt) {
        if (match.status() != MatchStatus.FILLED || match.orderId() != input.orderId()) {
            throw new IllegalArgumentException("A filled result for the same paper order is required");
        }
        InvestmentPaperAccountPo account = accountRepository
                .findByIdAndWorkspaceIdForUpdate(input.accountId(), input.workspaceId())
                .orElseThrow(PaperExecutionTransactionService::notFound);
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(input.accountId());
        List<InvestmentPaperOrderPo> pendingOpeningOrders =
                orderRepository.findPendingOpeningByAccountIdForUpdate(input.accountId());
        InvestmentPaperOrderPo order = orderRepository
                .findByScopeForUpdate(input.orderId(), input.accountId(), input.instrumentId())
                .orElseThrow(PaperExecutionTransactionService::notFound);
        InvestmentPaperFillPo existing = fillRepository.findByOrderIdAndFillNo(order.getId(), 1L).orElse(null);
        if (existing != null) {
            return new PaperExecutionResult(order.getId(), order.getStatus(), summary(existing), true);
        }
        if (!"PENDING_MATCH".equals(order.getStatus())) {
            return new PaperExecutionResult(order.getId(), order.getStatus(), null, true);
        }
        if (match.fee().compareTo(account.getWalletBalance()) > 0) {
            throw new InvestmentException(
                    InvestmentErrorCode.RISK_REJECTED, "Paper account cannot cover execution fee");
        }

        PositionAction action = PositionAction.valueOf(order.getPositionAction());
        PositionSide side = positionSide(order);
        InvestmentPositionPo position = positions.stream()
                .filter(value -> value.getInstrumentId().equals(order.getInstrumentId()))
                .findFirst().orElse(null);
        BigDecimal wallet = account.getWalletBalance();
        long sequence = account.getLedgerSequence();
        if (action == PositionAction.OPEN) {
            BigDecimal usedMargin = positions.stream().map(InvestmentPositionPo::getIsolatedMargin)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal otherReservedMargin = pendingReservedMargin(pendingOpeningOrders, order.getId());
            BigDecimal requiredMargin = match.notional().divide(order.getLeverage(), MATH);
            BigDecimal walletAfterFee = wallet.subtract(match.fee());
            if (usedMargin.add(otherReservedMargin).add(requiredMargin).compareTo(walletAfterFee) > 0) {
                order.setStatus("REJECTED");
                order.setRejectionCode("AVAILABLE_MARGIN_LIMIT");
                order.setRejectionMessage("Paper account margin changed before execution");
                orderRepository.saveAndFlush(order);
                return new PaperExecutionResult(order.getId(), order.getStatus(), null, false);
            }
            requireNonNegativeWallet(wallet.subtract(match.fee()));
            PositionUpdate update = open(position, order, side, match, input, executedAt);
            positionRepository.saveAndFlush(update.position());
            wallet = wallet.subtract(match.fee());
            sequence = appendLedger(account, order, ++sequence, "FEE", match.fee().negate(), wallet,
                    "fill:" + order.getId() + ":fee", executedAt, Map.of("fillNo", "1"));
            sequence = appendLedger(account, order, ++sequence, "MARGIN_RESERVE", BigDecimal.ZERO, wallet,
                    "fill:" + order.getId() + ":margin-reserve", executedAt,
                    Map.of("isolatedMargin", update.addedMargin().toPlainString()));
        } else {
            if (position == null || !position.getPositionSide().equals(side.name())
                    || match.quantity().compareTo(position.getQuantity()) > 0) {
                throw new InvestmentException(
                        InvestmentErrorCode.RISK_REJECTED, "Paper close no longer matches the locked position");
            }
            PositionUpdate update = close(position, side, match, input, executedAt);
            requireNonNegativeWallet(wallet.add(update.realizedPnl()).subtract(match.fee()));
            wallet = wallet.add(update.realizedPnl());
            sequence = appendLedger(account, order, ++sequence, "REALIZED_PNL", update.realizedPnl(), wallet,
                    "fill:" + order.getId() + ":realized-pnl", executedAt, Map.of("fillNo", "1"));
            wallet = wallet.subtract(match.fee());
            sequence = appendLedger(account, order, ++sequence, "FEE", match.fee().negate(), wallet,
                    "fill:" + order.getId() + ":fee", executedAt, Map.of("fillNo", "1"));
            sequence = appendLedger(account, order, ++sequence, "MARGIN_RELEASE", BigDecimal.ZERO, wallet,
                    "fill:" + order.getId() + ":margin-release", executedAt,
                    Map.of("releasedMargin", update.releasedMargin().toPlainString()));
            if (update.closed()) {
                positionRepository.delete(position);
                positionRepository.flush();
            } else {
                positionRepository.saveAndFlush(update.position());
            }
        }
        InvestmentPaperFillPo fill = fillRepository.saveAndFlush(newFill(order, match, input, executedAt));
        account.setWalletBalance(wallet);
        account.setLedgerSequence(sequence);
        accountRepository.saveAndFlush(account);
        order.setRemainingQuantity(BigDecimal.ZERO);
        order.setStatus("FILLED");
        order.setMatchedAt(executedAt);
        orderRepository.saveAndFlush(order);
        return new PaperExecutionResult(order.getId(), order.getStatus(), summary(fill), false);
    }

    @Transactional
    public String expire(PaperMatchJobInput input, Instant now) {
        accountRepository.findByIdAndWorkspaceIdForUpdate(input.accountId(), input.workspaceId())
                .orElseThrow(PaperExecutionTransactionService::notFound);
        positionRepository.findByAccountIdForUpdate(input.accountId());
        InvestmentPaperOrderPo order = orderRepository
                .findByScopeForUpdate(input.orderId(), input.accountId(), input.instrumentId())
                .orElseThrow(PaperExecutionTransactionService::notFound);
        if ("PENDING_MATCH".equals(order.getStatus())) {
            order.setStatus("EXPIRED");
            order.setCancelledAt(now);
            orderRepository.saveAndFlush(order);
        }
        return order.getStatus();
    }

    private PositionUpdate open(
            InvestmentPositionPo position, InvestmentPaperOrderPo order, PositionSide side,
            MatchResult match, PaperMatchJobInput input, Instant now) {
        BigDecimal addedMargin = match.notional().divide(order.getLeverage(), MATH);
        if (position == null) {
            position = new InvestmentPositionPo();
            position.setAccountId(order.getAccountId());
            position.setInstrumentId(order.getInstrumentId());
            position.setPositionSide(side.name());
            position.setQuantity(match.quantity());
            position.setEntryPrice(match.fillPrice());
            position.setLeverage(order.getLeverage());
            position.setIsolatedMargin(addedMargin);
            position.setMaintenanceMarginRate(input.maintenanceMarginRate());
            position.setMaintenanceMargin(match.notional().multiply(input.maintenanceMarginRate()));
            position.setLiquidationPrice(liquidationPrice(side, match.fillPrice(), order.getLeverage(),
                    input.maintenanceMarginRate()));
            position.setRealizedPnl(BigDecimal.ZERO);
            position.setFundingPnl(BigDecimal.ZERO);
            position.setCreatedAt(now);
        } else {
            if (!position.getPositionSide().equals(side.name())) {
                throw new InvestmentException(
                        InvestmentErrorCode.RISK_REJECTED, "Paper open would cross an opposite position");
            }
            BigDecimal oldCost = position.getEntryPrice().multiply(position.getQuantity());
            BigDecimal newQuantity = position.getQuantity().add(match.quantity());
            position.setEntryPrice(oldCost.add(match.fillPrice().multiply(match.quantity()))
                    .divide(newQuantity, MATH));
            position.setQuantity(newQuantity);
            position.setIsolatedMargin(position.getIsolatedMargin().add(addedMargin));
            BigDecimal totalNotional = newQuantity.multiply(match.fillPrice())
                    .multiply(input.contractMultiplier()).abs();
            position.setLeverage(totalNotional.divide(position.getIsolatedMargin(), MATH));
            position.setMaintenanceMarginRate(input.maintenanceMarginRate());
            position.setMaintenanceMargin(totalNotional.multiply(input.maintenanceMarginRate()));
            position.setLiquidationPrice(liquidationPrice(side, position.getEntryPrice(), position.getLeverage(),
                    input.maintenanceMarginRate()));
        }
        position.setLastFillAt(now);
        position.setUpdatedAt(now);
        return new PositionUpdate(position, addedMargin, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private PositionUpdate close(
            InvestmentPositionPo position, PositionSide side, MatchResult match,
            PaperMatchJobInput input, Instant now) {
        BigDecimal priceChange = side == PositionSide.LONG
                ? match.fillPrice().subtract(position.getEntryPrice())
                : position.getEntryPrice().subtract(match.fillPrice());
        BigDecimal realized = priceChange.multiply(match.quantity()).multiply(input.contractMultiplier());
        BigDecimal ratio = match.quantity().divide(position.getQuantity(), MATH);
        BigDecimal released = position.getIsolatedMargin().multiply(ratio);
        BigDecimal remaining = position.getQuantity().subtract(match.quantity());
        position.setRealizedPnl(position.getRealizedPnl().add(realized));
        position.setLastFillAt(now);
        position.setUpdatedAt(now);
        if (remaining.signum() == 0) {
            return new PositionUpdate(position, BigDecimal.ZERO, released, realized, true);
        }
        position.setQuantity(remaining);
        position.setIsolatedMargin(position.getIsolatedMargin().subtract(released));
        BigDecimal notional = remaining.multiply(match.fillPrice()).multiply(input.contractMultiplier()).abs();
        position.setMaintenanceMargin(notional.multiply(position.getMaintenanceMarginRate()));
        position.setLiquidationPrice(liquidationPrice(side, position.getEntryPrice(), position.getLeverage(),
                position.getMaintenanceMarginRate()));
        return new PositionUpdate(position, BigDecimal.ZERO, released, realized, false);
    }

    private long appendLedger(
            InvestmentPaperAccountPo account, InvestmentPaperOrderPo order, long sequence,
            String eventType, BigDecimal amount, BigDecimal balanceAfter, String key,
            Instant occurredAt, Map<String, String> details) {
        InvestmentMarginLedgerPo ledger = new InvestmentMarginLedgerPo();
        ledger.setAccountId(account.getId());
        ledger.setSequenceNo(sequence);
        ledger.setEventType(eventType);
        ledger.setAsset("USDT");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setInstrumentId(order.getInstrumentId());
        ledger.setReferenceType("PAPER_FILL");
        ledger.setReferenceId(order.getId().toString());
        ledger.setIdempotencyKey(key);
        ledger.setOccurredAt(occurredAt);
        ledger.setDetailsJson(json(details));
        ledger.setCreatedAt(occurredAt);
        ledgerRepository.saveAndFlush(ledger);
        return sequence;
    }

    private static InvestmentPaperFillPo newFill(
            InvestmentPaperOrderPo order, MatchResult match, PaperMatchJobInput input, Instant executedAt) {
        InvestmentPaperFillPo fill = new InvestmentPaperFillPo();
        fill.setOrderId(order.getId());
        fill.setFillNo(1L);
        fill.setInstrumentId(order.getInstrumentId());
        fill.setPositionAction(order.getPositionAction());
        fill.setSide(order.getSide());
        fill.setFillPrice(match.fillPrice());
        fill.setQuantity(match.quantity());
        fill.setNotional(match.notional());
        fill.setFeeRate(match.liquidityRole() == top.egon.mario.investment.trading.matching.model.LiquidityRole.MAKER
                ? input.makerFeeRate() : input.takerFeeRate());
        fill.setFeeAmount(match.fee());
        fill.setFeeAsset("USDT");
        fill.setLiquidity(match.liquidityRole().name());
        fill.setFilledAt(executedAt);
        fill.setMarketBarOpenTime(match.marketBarOpenTime());
        fill.setCreatedAt(executedAt);
        return fill;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.INTERNAL_ERROR, "Unable to serialize paper ledger details", exception);
        }
    }

    private BigDecimal pendingReservedMargin(List<InvestmentPaperOrderPo> pendingOrders, Long currentOrderId) {
        List<InvestmentPaperOrderPo> otherOrders = pendingOrders.stream()
                .filter(order -> !order.getId().equals(currentOrderId)).toList();
        if (otherOrders.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> intentIds = otherOrders.stream().map(InvestmentPaperOrderPo::getIntentId).toList();
        Map<Long, BigDecimal> notionals = new HashMap<>();
        intentRepository.findAllById(intentIds)
                .forEach(intent -> notionals.put(intent.getId(), intent.getRequestedNotional()));
        if (notionals.size() != intentIds.size()) {
            throw new InvestmentException(
                    InvestmentErrorCode.DATA_UNAVAILABLE, "Pending paper order intent snapshot is unavailable");
        }
        return otherOrders.stream()
                .map(order -> notionals.get(order.getIntentId()).divide(order.getLeverage(), MATH))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal liquidationPrice(
            PositionSide side, BigDecimal entryPrice, BigDecimal leverage, BigDecimal maintenanceRate) {
        BigDecimal marginRatio = BigDecimal.ONE.divide(leverage, MATH);
        BigDecimal factor = side == PositionSide.LONG
                ? BigDecimal.ONE.subtract(marginRatio).add(maintenanceRate)
                : BigDecimal.ONE.add(marginRatio).subtract(maintenanceRate);
        return entryPrice.multiply(factor).max(new BigDecimal("0.000000000000000001"));
    }

    private static PositionSide positionSide(InvestmentPaperOrderPo order) {
        boolean opening = "OPEN".equals(order.getPositionAction());
        boolean buy = "BUY".equals(order.getSide());
        return opening ? (buy ? PositionSide.LONG : PositionSide.SHORT)
                : (buy ? PositionSide.SHORT : PositionSide.LONG);
    }

    private static PaperFillSummary summary(InvestmentPaperFillPo fill) {
        return new PaperFillSummary(
                fill.getId(), fill.getFillPrice(), fill.getQuantity(), fill.getFeeAmount(), fill.getFilledAt());
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Paper execution scope was not found");
    }

    private static void requireNonNegativeWallet(BigDecimal wallet) {
        if (wallet.signum() < 0) {
            throw new InvestmentException(InvestmentErrorCode.RISK_REJECTED, "Paper wallet would become negative");
        }
    }

    private record PositionUpdate(
            InvestmentPositionPo position,
            BigDecimal addedMargin,
            BigDecimal releasedMargin,
            BigDecimal realizedPnl,
            boolean closed
    ) {
    }
}
