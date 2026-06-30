package top.egon.mario.nutrition.service.bootstrap;

import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

import java.util.List;

/**
 * Static RBAC permission seed catalog required by the nutrition MVP.
 */
public class NutritionPermissionCatalog {

    /**
     * Returns menu permissions used by the nutrition route tree.
     */
    public List<MenuPermissionSeed> menus() {
        return List.of(
                new MenuPermissionSeed("menu:nutrition", "营养管理", null, null, "nutrition", 70),
                new MenuPermissionSeed("menu:nutrition:families", "家庭营养", "menu:nutrition",
                        "/nutrition/families", "nutrition-families", 71),
                new MenuPermissionSeed("menu:nutrition:platform", "营养平台", "menu:nutrition",
                        "/nutrition/platform", "nutrition-platform", 72)
        );
    }

    /**
     * Returns button permissions used by nutrition page actions.
     */
    public List<ButtonPermissionSeed> buttons() {
        return List.of();
    }

    /**
     * Returns API permissions enforced by the dynamic authorization manager.
     */
    public List<ApiPermissionSeed> apis() {
        return List.of(
                new ApiPermissionSeed("api:nutrition:family:*", "家庭营养接口", "ANY",
                        "/api/nutrition/family/**", ApiMatcherType.ANT, ApiRiskLevel.MEDIUM),
                new ApiPermissionSeed("api:nutrition:platform:*", "营养平台接口", "ANY",
                        "/api/nutrition/platform/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH)
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
