package top.egon.mario.investment.bootstrap;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies Investment resources while keeping platform operations separate from private data access.
 */
@Component
public class InvestmentRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "investment";
    private static final String AUTH_SELF_API_PERMISSION_CODE = "api:rbac:auth:self";
    private static final String ME_SELF_API_PERMISSION_CODE = "api:rbac:me:self";

    private final InvestmentPermissionCatalog catalog = new InvestmentPermissionCatalog();

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        List<RbacResourceSeed> resources = new ArrayList<>();
        catalog.apis().forEach(seed -> resources.add(api(seed)));
        catalog.menus().forEach(seed -> resources.add(menu(seed)));
        catalog.buttons().forEach(seed -> resources.add(button(seed)));
        return resources;
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(
                        APP_CODE,
                        "INVESTMENT_USER",
                        "Investment User",
                        "System role for private analysis, backtests and paper trading.",
                        80,
                        List.of(
                                "menu:investment", "menu:investment:workspace",
                                "api:investment:market:*", "api:investment:strategy:read",
                                "api:investment:workspace:*", "api:investment:private-detail:*",
                                "button:investment:workspace:create", "button:investment:watchlist:manage",
                                "button:investment:report:create", "button:investment:backtest:create",
                                "button:investment:paper:trade", "button:investment:agent:run",
                                AUTH_SELF_API_PERMISSION_CODE, ME_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        "INVESTMENT_PLATFORM_ADMIN",
                        "Investment Platform Administrator",
                        "System role for shared market data operations without private workspace access.",
                        70,
                        List.of(
                                "menu:investment", "menu:investment:platform",
                                "api:investment:market:*", "api:investment:platform:*",
                                "button:investment:platform:retry-job",
                                "button:investment:platform:resolve-quality",
                                AUTH_SELF_API_PERMISSION_CODE, ME_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed menu(InvestmentPermissionCatalog.MenuPermissionSeed seed) {
        return RbacResourceSeed.menu(
                APP_CODE, APP_CODE, seed.permCode(), seed.permName(), seed.parentPermCode(),
                PermissionStatus.ENABLED, seed.sortNo(), null,
                new RbacMenuSeed(seed.routeName(), seed.routePath(), null, null, "FundOutlined",
                        false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed button(InvestmentPermissionCatalog.ButtonPermissionSeed seed) {
        return RbacResourceSeed.button(
                APP_CODE, APP_CODE, seed.permCode(), seed.permName(), seed.menuPermCode(),
                PermissionStatus.ENABLED, seed.sortNo(), null,
                new RbacButtonSeed(seed.buttonKey(), seed.buttonKey(), null),
                List.of(seed.apiPermCode()), RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed api(InvestmentPermissionCatalog.ApiPermissionSeed seed) {
        return RbacResourceSeed.api(
                APP_CODE, APP_CODE, seed.permCode(), seed.permName(), PermissionStatus.ENABLED, 0, null,
                new RbacApiSeed(seed.httpMethod(), seed.urlPattern(), seed.matcherType(), false, seed.riskLevel()),
                RbacResourceSource.PROVIDER
        );
    }
}
