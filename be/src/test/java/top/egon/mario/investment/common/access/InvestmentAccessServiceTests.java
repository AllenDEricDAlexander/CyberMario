package top.egon.mario.investment.common.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that private Investment access is based on explicit ownership only.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentAccessServiceTests {

    @Mock
    private InvestmentWorkspaceRepository workspaceRepository;

    @Mock
    private InvestmentWatchlistRepository watchlistRepository;

    @Mock
    private InvestmentPaperAccountRepository accountRepository;

    @Mock
    private InvestmentAgentRunRepository agentRunRepository;

    private InvestmentAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new InvestmentAccessService(
                workspaceRepository, watchlistRepository, accountRepository, agentRunRepository);
    }

    @Test
    void acceptsOnlyTheActiveWorkspaceOwner() {
        when(workspaceRepository.existsOwnedActiveWorkspace(11L, 101L)).thenReturn(true);

        assertThatCode(() -> accessService.requireWorkspaceOwner(11L, 101L)).doesNotThrowAnyException();

        verify(workspaceRepository).existsOwnedActiveWorkspace(11L, 101L);
    }

    @Test
    void rejectsAnotherUserWithoutAPlatformAdminBypass() {
        when(workspaceRepository.existsOwnedActiveWorkspace(11L, 999L)).thenReturn(false);

        assertThatThrownBy(() -> accessService.requireWorkspaceOwner(11L, 999L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> {
                    InvestmentException exception = (InvestmentException) error;
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(InvestmentErrorCode.FORBIDDEN);
                });
    }

    @Test
    void scopesWatchlistOwnershipByWatchlistWorkspaceAndActor() {
        when(watchlistRepository.existsOwnedActiveWatchlist(21L, 11L, 101L)).thenReturn(true);

        assertThatCode(() -> accessService.requireWatchlistOwner(21L, 11L, 101L)).doesNotThrowAnyException();

        verify(watchlistRepository).existsOwnedActiveWatchlist(21L, 11L, 101L);
    }

    @Test
    void requiresTheAccountToBelongToTheActor() {
        when(accountRepository.findOwnedAccount(31L, 101L))
                .thenReturn(Optional.of(new top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo()));

        assertThatCode(() -> accessService.requireAccountOwner(31L, 101L)).doesNotThrowAnyException();

        verify(accountRepository).findOwnedAccount(31L, 101L);
    }

    @Test
    void rejectsAnAccountOwnedByAnotherActor() {
        when(accountRepository.findOwnedAccount(31L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireAccountOwner(31L, 999L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                                ((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.FORBIDDEN));
    }
}
