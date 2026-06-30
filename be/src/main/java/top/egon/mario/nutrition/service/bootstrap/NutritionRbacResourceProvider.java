package top.egon.mario.nutrition.service.bootstrap;

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
 * Supplies nutrition RBAC resources to the unified RBAC resource synchronizer.
 */
@Component
public class NutritionRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "nutrition";
    private static final String AUTH_SELF_API_PERMISSION_CODE = "api:rbac:auth:self";
    private static final String ME_SELF_API_PERMISSION_CODE = "api:rbac:me:self";

    private final NutritionPermissionCatalog catalog = new NutritionPermissionCatalog();

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
                        "NUTRITION_PLATFORM_ADMIN",
                        "Nutrition Platform Administrator",
                        "System role for nutrition platform setup and cross-family support.",
                        60,
                        List.of(
                                "menu:nutrition", "menu:nutrition:families", "menu:nutrition:platform",
                                "api:nutrition:family:*", "api:nutrition:platform:*",
                                AUTH_SELF_API_PERMISSION_CODE, ME_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        "NUTRITION_USER",
                        "Nutrition User",
                        "System role for family nutrition usage.",
                        70,
                        List.of(
                                "menu:nutrition", "menu:nutrition:families", "api:nutrition:family:*",
                                AUTH_SELF_API_PERMISSION_CODE, ME_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed menu(NutritionPermissionCatalog.MenuPermissionSeed seed) {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                seed.parentPermCode(),
                PermissionStatus.ENABLED,
                seed.sortNo(),
                null,
                new RbacMenuSeed(seed.routeName(), seed.routePath(), null, null, "NutritionOutlined",
                        false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed button(NutritionPermissionCatalog.ButtonPermissionSeed seed) {
        return RbacResourceSeed.button(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                seed.menuPermCode(),
                PermissionStatus.ENABLED,
                seed.sortNo(),
                null,
                new RbacButtonSeed(seed.buttonKey(), seed.buttonKey(), null),
                List.of(seed.apiPermCode()),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed api(NutritionPermissionCatalog.ApiPermissionSeed seed) {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed(seed.httpMethod(), seed.urlPattern(), seed.matcherType(), false, seed.riskLevel()),
                RbacResourceSource.PROVIDER
        );
    }
}
