package top.egon.mario.investment.quant.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;

import java.util.List;

/**
 * Read-only API for Java strategies installed by application code.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment/strategies")
public class InvestmentStrategyController extends ReactiveInvestmentSupport {

    private final InvestmentStrategyRegistry registry;

    @GetMapping
    public Mono<ApiResponse<List<StrategyDescriptor>>> list() {
        return blocking(registry::descriptors);
    }

    @GetMapping("/{strategyCode}")
    public Mono<ApiResponse<StrategyDescriptor>> detail(@PathVariable String strategyCode) {
        return blocking(() -> registry.require(strategyCode).descriptor());
    }
}
