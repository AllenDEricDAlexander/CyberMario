package top.egon.mario.investment.agent;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentAgentAccessTests {

    @Test
    void requiresExplicitRunOwnershipWithoutAPlatformAdminBypass() {
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAccessService accessService = new InvestmentAccessService(
                mock(InvestmentWorkspaceRepository.class), mock(InvestmentWatchlistRepository.class),
                mock(InvestmentPaperAccountRepository.class), runRepository);
        when(runRepository.findOwnedRun(41L, 5L)).thenReturn(Optional.of(new InvestmentAgentRunPo()));
        when(runRepository.findOwnedRun(41L, 999L)).thenReturn(Optional.empty());

        assertThatCode(() -> accessService.requireAgentRunOwner(41L, 5L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.requireAgentRunOwner(41L, 999L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((InvestmentException) error).getErrorCode()).isEqualTo(InvestmentErrorCode.FORBIDDEN));

        verify(runRepository).findOwnedRun(41L, 5L);
        verify(runRepository).findOwnedRun(41L, 999L);
    }
}
