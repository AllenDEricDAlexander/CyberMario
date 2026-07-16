package top.egon.mario.investment.portfolio.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentRiskProfilePo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentRiskProfileRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.web.dto.CreateInvestmentPaperAccountRequest;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPaperAccountDetailResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPaperAccountResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileRequest;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileResponse;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentPaperAccountSwitchesRequest;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentRiskProfileRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;

/**
 * Owns paper-account and risk-profile lifecycle within the private workspace boundary.
 */
@Service
public class InvestmentPaperAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentRiskProfileRepository riskProfileRepository;
    private final InvestmentAccessService accessService;
    private final Clock clock;

    public InvestmentPaperAccountService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentRiskProfileRepository riskProfileRepository,
            InvestmentAccessService accessService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    /**
     * Creates the account and its mandatory explicit risk profile in one transaction.
     */
    @Transactional
    public InvestmentPaperAccountDetailResponse create(
            Long actorId, Long workspaceId, CreateInvestmentPaperAccountRequest request) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        if (request == null || request.riskProfile() == null) {
            throw invalid("A complete risk profile is required");
        }
        String name = normalizedName(request.name());
        BigDecimal initialEquity = positiveDecimal(request.initialEquity(), 38, 18, "initialEquity");
        InvestmentRiskLimits limits = parseLimits(request.riskProfile());
        if (accountRepository.findByWorkspaceIdAndName(workspaceId, name).isPresent()) {
            throw conflict("Paper account name already exists in this workspace");
        }

        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setWorkspaceId(workspaceId);
        account.setName(name);
        account.setMarginAsset("USDT");
        account.setInitialEquity(initialEquity);
        account.setWalletBalance(initialEquity);
        account.setLedgerSequence(0L);
        account.setMarginMode("ISOLATED");
        account.setPositionMode("ONE_WAY");
        account.setTradingEnabled(false);
        account.setAgentAutoTradeEnabled(false);
        account.setStatus("ACTIVE");
        account.setOpenedAt(clock.instant());

        try {
            InvestmentPaperAccountPo savedAccount = accountRepository.saveAndFlush(account);
            InvestmentRiskProfilePo savedProfile = riskProfileRepository.saveAndFlush(
                    newProfile(savedAccount.getId(), limits));
            return new InvestmentPaperAccountDetailResponse(
                    toAccountResponse(savedAccount), toRiskResponse(savedProfile));
        } catch (DataIntegrityViolationException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Paper account or risk profile already exists", exception);
        }
    }

    /**
     * Lists accounts only from the authenticated owner's selected workspace.
     */
    @Transactional(readOnly = true)
    public Page<InvestmentPaperAccountResponse> list(
            Long actorId, Long workspaceId, Pageable pageable) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        return accountRepository.findOwnedAccounts(workspaceId, actorId, pageable)
                .map(InvestmentPaperAccountService::toAccountResponse);
    }

    /**
     * Returns owner-scoped account detail and the mandatory profile.
     */
    @Transactional(readOnly = true)
    public InvestmentPaperAccountDetailResponse get(Long actorId, Long accountId) {
        InvestmentPaperAccountPo account = requireOwnedAccount(actorId, accountId);
        InvestmentRiskProfilePo profile = requireProfile(account.getId());
        return new InvestmentPaperAccountDetailResponse(toAccountResponse(account), toRiskResponse(profile));
    }

    /**
     * Replaces both explicit switches under optimistic locking.
     */
    @Transactional
    public InvestmentPaperAccountResponse updateSwitches(
            Long actorId, Long accountId, UpdateInvestmentPaperAccountSwitchesRequest request) {
        if (request == null || request.tradingEnabled() == null
                || request.agentAutoTradeEnabled() == null || request.version() == null) {
            throw invalid("Both switches and account version are required");
        }
        InvestmentPaperAccountPo account = requireOwnedAccount(actorId, accountId);
        requireVersion(account.getVersion(), request.version(), "Paper account");
        if (!"ACTIVE".equals(account.getStatus())
                && (request.tradingEnabled() || request.agentAutoTradeEnabled())) {
            throw invalid("A non-active paper account cannot enable trading");
        }
        account.setTradingEnabled(request.tradingEnabled());
        account.setAgentAutoTradeEnabled(request.agentAutoTradeEnabled());
        try {
            return toAccountResponse(accountRepository.saveAndFlush(account));
        } catch (OptimisticLockingFailureException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Paper account was modified concurrently", exception);
        }
    }

    @Transactional(readOnly = true)
    public InvestmentRiskProfileResponse getRiskProfile(Long actorId, Long accountId) {
        requireOwnedAccount(actorId, accountId);
        return toRiskResponse(requireProfile(accountId));
    }

    /**
     * Fully replaces risk limits; partial or stale updates are rejected.
     */
    @Transactional
    public InvestmentRiskProfileResponse updateRiskProfile(
            Long actorId, Long accountId, UpdateInvestmentRiskProfileRequest request) {
        requireOwnedAccount(actorId, accountId);
        if (request == null || request.version() == null) {
            throw invalid("Risk profile version is required");
        }
        InvestmentRiskLimits limits = parseLimits(request.profile());
        InvestmentRiskProfilePo profile = requireProfile(accountId);
        requireVersion(profile.getVersion(), request.version(), "Risk profile");
        applyLimits(profile, limits);
        try {
            return toRiskResponse(riskProfileRepository.saveAndFlush(profile));
        } catch (OptimisticLockingFailureException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Risk profile was modified concurrently", exception);
        }
    }

    private InvestmentPaperAccountPo requireOwnedAccount(Long actorId, Long accountId) {
        if (actorId == null || actorId <= 0 || accountId == null || accountId <= 0) {
            throw forbidden();
        }
        return accountRepository.findOwnedAccount(accountId, actorId).orElseThrow(
                InvestmentPaperAccountService::forbidden);
    }

    private InvestmentRiskProfilePo requireProfile(Long accountId) {
        return riskProfileRepository.findByAccountId(accountId).orElseThrow(() ->
                new InvestmentException(
                        InvestmentErrorCode.DATA_UNAVAILABLE, "Paper account risk profile is unavailable"));
    }

    private static InvestmentRiskProfilePo newProfile(Long accountId, InvestmentRiskLimits limits) {
        InvestmentRiskProfilePo profile = new InvestmentRiskProfilePo();
        profile.setAccountId(accountId);
        profile.setSettingsJson("{}");
        applyLimits(profile, limits);
        return profile;
    }

    private static void applyLimits(InvestmentRiskProfilePo profile, InvestmentRiskLimits limits) {
        profile.setMaxLeverage(limits.maxLeverage());
        profile.setMaxOrderNotional(limits.maxOrderNotional());
        profile.setMaxPositionNotional(limits.maxPositionNotional());
        profile.setMaxGrossExposureNotional(limits.maxGrossExposureNotional());
        profile.setMaxOpenPositions(limits.maxOpenPositions());
        profile.setMaxDailyLossAmount(limits.maxDailyLossAmount());
        profile.setMaxDrawdownRatio(limits.maxDrawdownRatio());
        profile.setMaxOrdersPerHour(limits.maxOrdersPerHour());
        profile.setCooldownSeconds(limits.cooldownSeconds());
        profile.setMaxMarketDataAgeSeconds(limits.maxMarketDataAgeSeconds());
        profile.setMaxSlippageBps(limits.maxSlippageBps());
    }

    private static InvestmentRiskLimits parseLimits(InvestmentRiskProfileRequest request) {
        if (request == null) {
            throw invalid("A complete risk profile is required");
        }
        try {
            return new InvestmentRiskLimits(
                    positiveDecimal(request.maxLeverage(), 24, 12, "maxLeverage"),
                    positiveDecimal(request.maxOrderNotional(), 38, 18, "maxOrderNotional"),
                    positiveDecimal(request.maxPositionNotional(), 38, 18, "maxPositionNotional"),
                    positiveDecimal(request.maxGrossExposureNotional(), 38, 18,
                            "maxGrossExposureNotional"),
                    requirePositive(request.maxOpenPositions(), "maxOpenPositions"),
                    positiveDecimal(request.maxDailyLossAmount(), 38, 18, "maxDailyLossAmount"),
                    positiveDecimal(request.maxDrawdownRatio(), 24, 12, "maxDrawdownRatio"),
                    requirePositive(request.maxOrdersPerHour(), "maxOrdersPerHour"),
                    requireNonNegative(request.cooldownSeconds(), "cooldownSeconds"),
                    requirePositive(request.maxMarketDataAgeSeconds(), "maxMarketDataAgeSeconds"),
                    nonNegativeDecimal(request.maxSlippageBps(), 24, 12, "maxSlippageBps"));
        } catch (InvestmentException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.INVALID_REQUEST, "Invalid paper account risk profile", exception);
        }
    }

    private static BigDecimal positiveDecimal(String input, int precision, int scale, String field) {
        BigDecimal value = decimal(input, precision, scale, field);
        if (value.signum() <= 0) {
            throw invalid(field + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegativeDecimal(String input, int precision, int scale, String field) {
        BigDecimal value = decimal(input, precision, scale, field);
        if (value.signum() < 0) {
            throw invalid(field + " must be non-negative");
        }
        return value;
    }

    private static BigDecimal decimal(String input, int precision, int scale, String field) {
        final BigDecimal value;
        try {
            value = InvestmentDecimalCodec.parse(input);
        } catch (IllegalArgumentException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.INVALID_REQUEST, field + " must be a canonical decimal string", exception);
        }
        int integerDigits = Math.max(0, value.precision() - value.scale());
        if (value.scale() > scale || integerDigits > precision - scale) {
            throw invalid(field + " exceeds NUMERIC(" + precision + "," + scale + ")");
        }
        return value;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
        return value;
    }

    private static Long requireNonNegative(Long value, String field) {
        if (value == null || value < 0) {
            throw invalid(field + " must be non-negative");
        }
        return value;
    }

    private static String normalizedName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw invalid("Paper account name is required");
        }
        return name.trim();
    }

    private static void requireVersion(Long actual, Long requested, String resource) {
        long actualVersion = actual == null ? 0L : actual;
        if (!Objects.equals(actualVersion, requested)) {
            throw conflict(resource + " version conflict");
        }
    }

    private static InvestmentPaperAccountResponse toAccountResponse(InvestmentPaperAccountPo account) {
        BigDecimal wallet = account.getWalletBalance();
        return new InvestmentPaperAccountResponse(
                account.getId(), account.getWorkspaceId(), account.getName(), account.getMarginAsset(),
                format(account.getInitialEquity()), format(wallet), format(wallet), format(ZERO),
                format(wallet), format(ZERO), format(ZERO), account.isTradingEnabled(),
                account.isAgentAutoTradeEnabled(), account.getStatus(), account.getOpenedAt(),
                account.getVersion() == null ? 0L : account.getVersion());
    }

    private static InvestmentRiskProfileResponse toRiskResponse(InvestmentRiskProfilePo profile) {
        return new InvestmentRiskProfileResponse(
                profile.getId(), profile.getAccountId(), format(profile.getMaxLeverage()),
                format(profile.getMaxOrderNotional()), format(profile.getMaxPositionNotional()),
                format(profile.getMaxGrossExposureNotional()), profile.getMaxOpenPositions(),
                format(profile.getMaxDailyLossAmount()), format(profile.getMaxDrawdownRatio()),
                profile.getMaxOrdersPerHour(), profile.getCooldownSeconds(),
                profile.getMaxMarketDataAgeSeconds(), format(profile.getMaxSlippageBps()),
                profile.getVersion() == null ? 0L : profile.getVersion());
    }

    private static String format(BigDecimal value) {
        return InvestmentDecimalCodec.format(value);
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException conflict(String message) {
        return new InvestmentException(InvestmentErrorCode.CONFLICT, message);
    }

    private static InvestmentException forbidden() {
        return new InvestmentException(
                InvestmentErrorCode.FORBIDDEN, "Investment paper account access denied");
    }
}
