package top.egon.mario.investment.common.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;

/**
 * Enforces explicit owner checks for private Investment resources.
 *
 * <p>Platform roles intentionally do not bypass these checks.</p>
 */
@Service
@RequiredArgsConstructor
public class InvestmentAccessService {

    private final InvestmentWorkspaceRepository workspaceRepository;
    private final InvestmentWatchlistRepository watchlistRepository;
    private final InvestmentPaperAccountRepository accountRepository;

    /**
     * Requires the actor to own an active workspace.
     */
    @Transactional(readOnly = true)
    public void requireWorkspaceOwner(Long workspaceId, Long actorId) {
        if (!validId(workspaceId) || !validId(actorId)
                || !workspaceRepository.existsOwnedActiveWorkspace(workspaceId, actorId)) {
            throw forbidden();
        }
    }

    /**
     * Requires a watchlist to belong to both the supplied workspace and actor.
     */
    @Transactional(readOnly = true)
    public void requireWatchlistOwner(Long watchlistId, Long workspaceId, Long actorId) {
        if (!validId(watchlistId) || !validId(workspaceId) || !validId(actorId)
                || !watchlistRepository.existsOwnedActiveWatchlist(watchlistId, workspaceId, actorId)) {
            throw forbidden();
        }
    }

    /** Requires a paper account to belong to the actor's workspace. */
    @Transactional(readOnly = true)
    public void requireAccountOwner(Long accountId, Long actorId) {
        if (!validId(accountId) || !validId(actorId)
                || accountRepository.findOwnedAccount(accountId, actorId).isEmpty()) {
            throw forbidden();
        }
    }

    private static boolean validId(Long id) {
        return id != null && id > 0;
    }

    private static InvestmentException forbidden() {
        return new InvestmentException(InvestmentErrorCode.FORBIDDEN, "Investment private resource access denied");
    }
}
