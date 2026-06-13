package top.egon.mario.rbac.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies RBAC administration console menus and buttons to the resource synchronizer.
 */
@Component
public class RbacAdminConsoleResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "system";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        List<RbacResourceSeed> resources = new ArrayList<>();
        resources.add(menu("menu:system", "RBAC 管理", null, null, "rbac", "SafetyCertificateOutlined", 30));
        resources.add(menu("menu:system:users", "用户管理", "menu:system", "/rbac/users", "rbac-users", "TeamOutlined", 31));
        resources.add(menu("menu:system:roles", "角色管理", "menu:system", "/rbac/roles", "rbac-roles", "BranchesOutlined", 32));
        resources.add(menu("menu:system:permissions", "权限管理", "menu:system", "/rbac/permissions", "rbac-permissions", "ControlOutlined", 33));
        resources.add(menu("menu:system:menus", "菜单管理", "menu:system", "/rbac/menus", "rbac-menus", "MenuOutlined", 34));
        resources.add(menu("menu:system:buttons", "按钮管理", "menu:system", "/rbac/buttons", "rbac-buttons", "AppstoreOutlined", 35));
        resources.add(menu("menu:system:apis", "API 权限", "menu:system", "/rbac/apis", "rbac-apis", "ApiOutlined", 36));

        resources.add(button("btn:system:user:add", "新建用户", "menu:system:users", "create", 1));
        resources.add(button("btn:system:user:edit", "编辑用户", "menu:system:users", "edit", 2));
        resources.add(button("btn:system:user:role", "用户角色", "menu:system:users", "roles", 3));
        resources.add(button("btn:system:user:permission", "用户权限", "menu:system:users", "permissions", 4));
        resources.add(button("btn:system:user:resetPassword", "重置密码", "menu:system:users", "resetPassword", 5));
        resources.add(button("btn:system:user:status", "用户状态", "menu:system:users", "status", 6));
        resources.add(button("btn:system:user:delete", "删除用户", "menu:system:users", "delete", 7));

        resources.add(button("btn:system:role:add", "新建角色", "menu:system:roles", "create", 1));
        resources.add(button("btn:system:role:edit", "编辑角色", "menu:system:roles", "edit", 2));
        resources.add(button("btn:system:role:permission", "角色授权", "menu:system:roles", "permissions", 3));
        resources.add(button("btn:system:role:inheritance", "角色继承", "menu:system:roles", "inheritance", 4));
        resources.add(button("btn:system:role:delete", "删除角色", "menu:system:roles", "delete", 5));

        resources.add(button("btn:system:permission:add", "新建权限", "menu:system:permissions", "create", 1));
        resources.add(button("btn:system:permission:edit", "编辑权限", "menu:system:permissions", "edit", 2));
        resources.add(button("btn:system:permission:status", "权限状态", "menu:system:permissions", "status", 3));
        resources.add(button("btn:system:permission:delete", "删除权限", "menu:system:permissions", "delete", 4));

        resources.add(button("btn:system:menu:add", "新建菜单", "menu:system:menus", "create", 1));
        resources.add(button("btn:system:menu:edit", "编辑菜单", "menu:system:menus", "edit", 2));
        resources.add(button("btn:system:menu:delete", "删除菜单", "menu:system:menus", "delete", 3));

        resources.add(button("btn:system:button:add", "新建按钮", "menu:system:buttons", "create", 1));
        resources.add(button("btn:system:button:edit", "编辑按钮", "menu:system:buttons", "edit", 2));
        resources.add(button("btn:system:button:api", "关联 API", "menu:system:buttons", "apis", 3));
        resources.add(button("btn:system:button:delete", "删除按钮", "menu:system:buttons", "delete", 4));

        resources.add(button("btn:system:api:add", "新建 API", "menu:system:apis", "create", 1));
        resources.add(button("btn:system:api:edit", "编辑 API", "menu:system:apis", "edit", 2));
        resources.add(button("btn:system:api:delete", "删除 API", "menu:system:apis", "delete", 3));
        return resources;
    }

    private RbacResourceSeed menu(String code, String name, String parentCode, String routePath, String routeName,
                                  String icon, int sortNo) {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                code,
                name,
                parentCode,
                PermissionStatus.ENABLED,
                sortNo,
                null,
                new RbacMenuSeed(routeName, routePath, null, null, icon, false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed button(String code, String name, String menuCode, String action, int sortNo) {
        return RbacResourceSeed.button(
                APP_CODE,
                APP_CODE,
                code,
                name,
                menuCode,
                PermissionStatus.ENABLED,
                sortNo,
                null,
                new RbacButtonSeed(action, action, null),
                List.of(),
                RbacResourceSource.PROVIDER
        );
    }

}
