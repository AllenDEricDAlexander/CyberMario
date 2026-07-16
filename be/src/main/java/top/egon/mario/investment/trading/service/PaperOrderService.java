package top.egon.mario.investment.trading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.trading.po.InvestmentPaperFillPo;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentRiskCheckPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentPaperFillRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentRiskCheckRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.model.PaperFillSummary;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owner-neutral order projection and cancellation service used behind the paper trading facade.
 */
@Service
public class PaperOrderService {

    private final InvestmentTradeIntentRepository intentRepository;
    private final InvestmentRiskCheckRepository riskCheckRepository;
    private final InvestmentPaperOrderRepository orderRepository;
    private final InvestmentPaperFillRepository fillRepository;
    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperOrderService(
            InvestmentTradeIntentRepository intentRepository,
            InvestmentRiskCheckRepository riskCheckRepository,
            InvestmentPaperOrderRepository orderRepository,
            InvestmentPaperFillRepository fillRepository,
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.intentRepository = intentRepository;
        this.riskCheckRepository = riskCheckRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PaperTradeResult> findByIdempotencyKey(Long accountId, String idempotencyKey) {
        return intentRepository.findByIdempotencyKey(scopedIdempotencyKey(accountId, idempotencyKey))
                .map(this::result);
    }

    @Transactional(readOnly = true)
    public PaperTradeResult result(InvestmentTradeIntentPo intent) {
        List<InvestmentRiskCheckResult> riskResults = riskCheckRepository.findByIntentIdOrderById(intent.getId())
                .stream().map(this::toRiskResult).toList();
        InvestmentPaperOrderPo order = orderRepository.findByIntentId(intent.getId()).orElse(null);
        InvestmentPaperFillPo fill = order == null ? null
                : fillRepository.findByOrderIdAndFillNo(order.getId(), 1L).orElse(null);
        return new PaperTradeResult(intent.getId(), intent.getStatus(), riskResults,
                order == null ? null : new PaperOrderSummary(
                        order.getId(), order.getStatus(), order.getSubmittedAt(), order.getMatchedAt()),
                fill == null ? null : new PaperFillSummary(
                        fill.getId(), fill.getFillPrice(), fill.getQuantity(), fill.getFeeAmount(), fill.getFilledAt()));
    }

    @Transactional(readOnly = true)
    public Optional<MatchCandidate> findMatchCandidate(long orderId) {
        return orderRepository.findById(orderId).filter(order -> !order.isDeleted()).map(order -> {
            InvestmentTradeIntentPo intent = intentRepository.findById(order.getIntentId()).orElseThrow();
            return new MatchCandidate(order.getId(), order.getWorkspaceId(), order.getAccountId(),
                    order.getInstrumentId(), order.getStatus(), OrderType.valueOf(order.getOrderType()),
                    positionSide(order), PositionAction.valueOf(order.getPositionAction()), order.getQuantity(),
                    order.getLimitPrice(), intent.getDataAsOf(), intent.getExpiresAt());
        });
    }

    @Transactional(readOnly = true)
    public Page<PaperOrderSummary> listOwned(Long actorId, Long accountId, Pageable pageable) {
        accountRepository.findOwnedAccount(accountId, actorId).orElseThrow(PaperOrderService::forbidden);
        return orderRepository.findByAccountIdAndDeletedFalse(accountId, pageable)
                .map(order -> new PaperOrderSummary(
                        order.getId(), order.getStatus(), order.getSubmittedAt(), order.getMatchedAt()));
    }

    @Transactional
    public PaperOrderSummary cancelOwned(Long actorId, Long orderId) {
        InvestmentPaperOrderPo visible = orderRepository.findById(orderId)
                .filter(order -> !order.isDeleted()).orElseThrow(PaperOrderService::notFound);
        accountRepository.findOwnedAccount(visible.getAccountId(), actorId).orElseThrow(PaperOrderService::forbidden);
        return cancel(visible.getWorkspaceId(), visible.getAccountId(), visible.getInstrumentId(), visible.getId());
    }

    /**
     * Cancels only a still-pending order using the global account-position-order lock order.
     */
    @Transactional
    public PaperOrderSummary cancel(Long workspaceId, Long accountId, Long instrumentId, Long orderId) {
        accountRepository.findByIdAndWorkspaceIdForUpdate(accountId, workspaceId)
                .orElseThrow(PaperOrderService::notFound);
        positionRepository.findByAccountIdForUpdate(accountId);
        InvestmentPaperOrderPo order = orderRepository
                .findByScopeForUpdate(orderId, accountId, instrumentId).orElseThrow(PaperOrderService::notFound);
        if ("PENDING_MATCH".equals(order.getStatus())) {
            order.setStatus("CANCELLED");
            order.setCancelledAt(clock.instant());
            orderRepository.saveAndFlush(order);
        }
        return new PaperOrderSummary(order.getId(), order.getStatus(), order.getSubmittedAt(), order.getMatchedAt());
    }

    private InvestmentRiskCheckResult toRiskResult(InvestmentRiskCheckPo value) {
        Map<String, String> details;
        try {
            details = objectMapper.readValue(value.getDetailsJson(), new TypeReference<>() { });
        } catch (Exception exception) {
            details = Map.of("reason", "DETAILS_UNAVAILABLE");
        }
        return new InvestmentRiskCheckResult(
                InvestmentRiskRuleCode.valueOf(value.getRuleCode()), value.isPassed(), value.getObservedValue(),
                value.getLimitValue(), value.getMessage(), details, value.getCheckedAt());
    }

    private static PositionSide positionSide(InvestmentPaperOrderPo order) {
        boolean opening = "OPEN".equals(order.getPositionAction());
        boolean buy = "BUY".equals(order.getSide());
        return opening ? (buy ? PositionSide.LONG : PositionSide.SHORT)
                : (buy ? PositionSide.SHORT : PositionSide.LONG);
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Paper order was not found");
    }

    private static InvestmentException forbidden() {
        return new InvestmentException(InvestmentErrorCode.FORBIDDEN, "Paper order access denied");
    }

    static String scopedIdempotencyKey(Long accountId, String idempotencyKey) {
        try {
            String scoped = accountId + "\u0000" + idempotencyKey;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(scoped.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record MatchCandidate(
            long orderId,
            long workspaceId,
            long accountId,
            long instrumentId,
            String status,
            OrderType orderType,
            PositionSide positionSide,
            PositionAction positionAction,
            java.math.BigDecimal quantity,
            java.math.BigDecimal limitPrice,
            Instant eligibleAfter,
            Instant expiresAt
    ) {
    }
}
