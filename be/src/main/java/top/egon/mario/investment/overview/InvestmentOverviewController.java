package top.egon.mario.investment.overview;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.overview.dto.InvestmentOverviewResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Owner-scoped aggregate endpoint for the Investment workspace overview.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment/workspaces")
@Validated
public class InvestmentOverviewController extends ReactiveInvestmentSupport {

    private final InvestmentOverviewQueryService queryService;

    @GetMapping("/{workspaceId}/overview")
    public Mono<ApiResponse<InvestmentOverviewResponse>> overview(
            @PathVariable @Min(1) Long workspaceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> queryService.overview(actorId(principal), workspaceId));
    }
}
