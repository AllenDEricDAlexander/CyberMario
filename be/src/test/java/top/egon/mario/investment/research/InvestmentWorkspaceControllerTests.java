package top.egon.mario.investment.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.research.service.InvestmentWatchlistService;
import top.egon.mario.investment.research.service.InvestmentWorkspaceService;
import top.egon.mario.investment.research.web.InvestmentWorkspaceController;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWorkspaceRequest;
import top.egon.mario.investment.research.web.dto.InvestmentWorkspaceResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the reactive workspace API delegates with the authenticated actor id.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentWorkspaceControllerTests {

    @Mock
    private InvestmentWorkspaceService workspaceService;
    @Mock
    private InvestmentWatchlistService watchlistService;

    private InvestmentWorkspaceController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new InvestmentWorkspaceController(workspaceService, watchlistService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(101L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void listsOnlyTheAuthenticatedActorsWorkspacesUsingTheStablePageEnvelope() {
        InvestmentWorkspaceResponse workspace = new InvestmentWorkspaceResponse(
                11L, "Main", "USDT", "UTC", "ACTIVE", null);
        when(workspaceService.list(org.mockito.ArgumentMatchers.eq(101L), anyPageable()))
                .thenReturn(new PageImpl<>(List.of(workspace)));

        StepVerifier.create(controller.listWorkspaces(1, 20, principal))
                .assertNext(response -> {
                    assertThat(response.data().records()).containsExactly(workspace);
                    assertThat(response.data().page()).isEqualTo(1);
                })
                .verifyComplete();

        verify(workspaceService).list(org.mockito.ArgumentMatchers.eq(101L),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
    }

    @Test
    void createsAWorkspaceForTheAuthenticatedActor() {
        CreateInvestmentWorkspaceRequest request = new CreateInvestmentWorkspaceRequest("Main");
        InvestmentWorkspaceResponse workspace = new InvestmentWorkspaceResponse(
                11L, "Main", "USDT", "UTC", "ACTIVE", null);
        when(workspaceService.create(101L, request)).thenReturn(workspace);

        StepVerifier.create(controller.createWorkspace(request, principal))
                .assertNext(response -> assertThat(response.data()).isEqualTo(workspace))
                .verifyComplete();

        verify(workspaceService).create(101L, request);
    }

    private static Pageable anyPageable() {
        return org.mockito.ArgumentMatchers.any(Pageable.class);
    }
}
