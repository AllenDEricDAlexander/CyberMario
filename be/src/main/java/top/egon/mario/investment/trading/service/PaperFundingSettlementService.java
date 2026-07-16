package top.egon.mario.investment.trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.po.InvestmentMarginLedgerPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.service.model.PaperFundingJobInput;
import top.egon.mario.investment.trading.service.model.PaperFundingMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperFundingResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Settles one immutable funding fact through the account ledger sequence. */
@Service
public class PaperFundingSettlementService {

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentMarginLedgerRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    public PaperFundingSettlementService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            InvestmentMarginLedgerRepository ledgerRepository,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.ledgerRepository = ledgerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaperFundingResult settle(PaperFundingJobInput input, PaperFundingMarketSnapshot market) {
        validate(input, market);
        InvestmentPaperAccountPo account = accountRepository
                .findByIdAndWorkspaceIdForUpdate(input.accountId(), input.workspaceId())
                .orElseThrow(PaperFundingSettlementService::notFound);
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(input.accountId());
        String key = idempotencyKey(input);
        InvestmentMarginLedgerPo existing = ledgerRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return new PaperFundingResult(
                    input.accountId(), input.positionId(), "SETTLED", existing.getAmount(), true);
        }
        InvestmentPositionPo position = positions.stream()
                .filter(value -> value.getId() == input.positionId()
                        && value.getInstrumentId() == input.instrumentId())
                .findFirst().orElse(null);
        if (position == null) {
            return new PaperFundingResult(
                    input.accountId(), input.positionId(), "POSITION_CLOSED", BigDecimal.ZERO, false);
        }
        BigDecimal notional = position.getQuantity().multiply(market.markPrice())
                .multiply(market.contractMultiplier()).abs();
        BigDecimal absolute = notional.multiply(market.fundingRate()).abs();
        boolean longPays = market.fundingRate().signum() > 0 && "LONG".equals(position.getPositionSide());
        boolean shortPays = market.fundingRate().signum() < 0 && "SHORT".equals(position.getPositionSide());
        BigDecimal amount = longPays || shortPays ? absolute.negate() : absolute;
        if (market.fundingRate().signum() == 0) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal balance = account.getWalletBalance().add(amount);
        if (balance.signum() < 0) {
            throw new InvestmentException(
                    InvestmentErrorCode.RISK_REJECTED, "Funding settlement would make the paper wallet negative");
        }
        long sequence = account.getLedgerSequence() + 1L;
        InvestmentMarginLedgerPo ledger = new InvestmentMarginLedgerPo();
        ledger.setAccountId(account.getId());
        ledger.setSequenceNo(sequence);
        ledger.setEventType("FUNDING");
        ledger.setAsset("USDT");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(balance);
        ledger.setInstrumentId(position.getInstrumentId());
        ledger.setReferenceType("PAPER_FUNDING");
        ledger.setReferenceId(position.getId() + ":" + input.fundingTime().toEpochMilli());
        ledger.setIdempotencyKey(key);
        ledger.setOccurredAt(input.fundingTime());
        ledger.setDetailsJson(json(Map.of(
                "fundingRate", market.fundingRate().toPlainString(),
                "fundingRevision", Long.toString(market.fundingRevision()),
                "markPrice", market.markPrice().toPlainString(),
                "marketTime", market.marketTime().toString(),
                "positionSide", position.getPositionSide())));
        ledger.setCreatedAt(input.fundingTime());
        ledgerRepository.saveAndFlush(ledger);

        account.setWalletBalance(balance);
        account.setLedgerSequence(sequence);
        accountRepository.saveAndFlush(account);
        position.setFundingPnl(position.getFundingPnl().add(amount));
        position.setUpdatedAt(input.fundingTime());
        positionRepository.saveAndFlush(position);
        return new PaperFundingResult(input.accountId(), input.positionId(), "SETTLED", amount, false);
    }

    public static String idempotencyKey(PaperFundingJobInput input) {
        return "funding:%d:%d:%d:%d".formatted(
                input.accountId(), input.positionId(), input.instrumentId(), input.fundingTime().toEpochMilli());
    }

    private static void validate(PaperFundingJobInput input, PaperFundingMarketSnapshot market) {
        if (input == null || market == null || input.accountId() <= 0 || input.positionId() <= 0
                || input.instrumentId() <= 0 || input.workspaceId() <= 0 || input.sourceId() <= 0
                || input.fundingTime() == null || market.markPrice() == null
                || market.markPrice().signum() <= 0 || market.fundingRate() == null
                || market.contractMultiplier() == null || market.contractMultiplier().signum() <= 0
                || market.marketTime() == null || market.fundingRevision() <= 0) {
            throw new InvestmentException(
                    InvestmentErrorCode.DATA_UNAVAILABLE, "Complete funding settlement input is unavailable");
        }
    }

    private String json(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.INTERNAL_ERROR, "Unable to serialize funding ledger details", exception);
        }
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Funding settlement scope was not found");
    }
}
