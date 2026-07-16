package top.egon.mario.investment.bootstrap;

import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

import java.util.List;

/**
 * Static RBAC permission catalog for Investment web routes and actions.
 */
public class InvestmentPermissionCatalog {

    /**
     * Returns menu permissions used by the Investment route tree.
     */
    public List<MenuPermissionSeed> menus() {
        return List.of(
                new MenuPermissionSeed("menu:investment", "理财投资", null, null, "investment", 80),
                new MenuPermissionSeed("menu:investment:workspace", "投资工作台", "menu:investment",
                        "/investment/overview", "investment-workspace", 81),
                new MenuPermissionSeed("menu:investment:platform", "投资平台数据", "menu:investment",
                        "/investment/platform", "investment-platform", 82)
        );
    }

    /**
     * Returns button permissions used by Investment page actions.
     */
    public List<ButtonPermissionSeed> buttons() {
        return List.of(
                new ButtonPermissionSeed("button:investment:workspace:create", "创建工作区",
                        "menu:investment:workspace", "investment.workspace.create",
                        "api:investment:workspace:*", 10),
                new ButtonPermissionSeed("button:investment:watchlist:manage", "管理自选",
                        "menu:investment:workspace", "investment.watchlist.manage",
                        "api:investment:private-detail:*", 20),
                new ButtonPermissionSeed("button:investment:report:create", "创建分析报告",
                        "menu:investment:workspace", "investment.report.create",
                        "api:investment:workspace:*", 30),
                new ButtonPermissionSeed("button:investment:backtest:create", "运行回测",
                        "menu:investment:workspace", "investment.backtest.create",
                        "api:investment:workspace:*", 40),
                new ButtonPermissionSeed("button:investment:paper:trade", "模拟交易",
                        "menu:investment:workspace", "investment.paper.trade",
                        "api:investment:private-detail:*", 50),
                new ButtonPermissionSeed("button:investment:agent:run", "运行 Agent",
                        "menu:investment:workspace", "investment.agent.run",
                        "api:investment:workspace:*", 60),
                new ButtonPermissionSeed("button:investment:platform:retry-job", "重试任务",
                        "menu:investment:platform", "investment.platform.retry-job",
                        "api:investment:platform:*", 70),
                new ButtonPermissionSeed("button:investment:platform:resolve-quality", "处理质量问题",
                        "menu:investment:platform", "investment.platform.resolve-quality",
                        "api:investment:platform:*", 80)
        );
    }

    /**
     * Returns API permissions enforced by the dynamic authorization manager.
     */
    public List<ApiPermissionSeed> apis() {
        return List.of(
                new ApiPermissionSeed("api:investment:market:*", "投资平台行情读取", "ANY",
                        "/api/investment/market/**", ApiMatcherType.ANT, ApiRiskLevel.LOW),
                new ApiPermissionSeed("api:investment:strategy:read", "代码策略读取", "GET",
                        "/api/investment/strategies/**", ApiMatcherType.ANT, ApiRiskLevel.LOW),
                new ApiPermissionSeed("api:investment:workspace:*", "私人投资工作区接口", "ANY",
                        "/api/investment/workspaces/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:investment:private-detail:*", "私人投资详情接口", "ANY",
                        "^/api/investment/(watchlists|reports|backtests|paper-accounts|agent-runs)(/.*)?$",
                        ApiMatcherType.REGEX, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:investment:platform:*", "投资平台运维接口", "ANY",
                        "/api/investment/platform/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH)
        );
    }

    public record MenuPermissionSeed(
            String permCode,
            String permName,
            String parentPermCode,
            String routePath,
            String routeName,
            int sortNo
    ) {
    }

    public record ButtonPermissionSeed(
            String permCode,
            String permName,
            String menuPermCode,
            String buttonKey,
            String apiPermCode,
            int sortNo
    ) {
    }

    public record ApiPermissionSeed(
            String permCode,
            String permName,
            String httpMethod,
            String urlPattern,
            ApiMatcherType matcherType,
            ApiRiskLevel riskLevel
    ) {
    }
}
