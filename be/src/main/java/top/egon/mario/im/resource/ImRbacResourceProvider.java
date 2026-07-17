package top.egon.mario.im.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies platform IM menu, API permissions, and managed role presets.
 */
@Component
public class ImRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "im";
    private static final String MENU_IM = "menu:im";
    private static final String READ = "api:im:read";
    private static final String WRITE = "api:im:write";
    private static final String WRITE_PATCH = "api:im:write:patch";
    private static final String WRITE_DELETE = "api:im:write:delete";
    private static final String INSTANCE_READ = "api:im:instance:read";
    private static final String INSTANCE_WRITE = "api:im:instance:write";
    private static final String INSTANCE_DELETE = "api:im:instance:delete";
    private static final String PLATFORM_ADMIN = "api:im:platform-admin";
    private static final String AUTH_SELF = "api:rbac:auth:self";
    private static final String ME_SELF = "api:rbac:me:self";

    private static final List<String> USER_PERMISSION_CODES = List.of(
            MENU_IM, READ, WRITE, WRITE_PATCH, WRITE_DELETE,
            INSTANCE_READ, INSTANCE_WRITE, INSTANCE_DELETE, AUTH_SELF, ME_SELF
    );

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(
                menu(),
                api(READ, "Platform IM read", "GET",
                        "^/api/im/(conversations|conversations/[^/]+/messages|"
                                + "platform/(bootstrap|conversations|users|friends|friend-requests|groups|channels|"
                                + "channels/[^/]+/groups))$",
                        ApiRiskLevel.LOW, 10),
                api(WRITE, "Platform IM write", "POST",
                        "^/api/im/(messages|conversations/[^/]+/read|platform/(groups|channels|"
                                + "channels/[^/]+/groups)|platform/friend-requests|"
                                + "platform/friend-requests/[^/]+/(accept|reject|cancel)|join-requests|"
                                + "join-requests/[^/]+/cancel|surfaces/[^/]+/[^/]+/leave|dms|"
                                + "dms/(block|unblock)|ws-ticket)$",
                        ApiRiskLevel.MEDIUM, 20),
                api(WRITE_PATCH, "Platform IM update", "PATCH",
                        "^/api/im/platform/friends/[^/]+$", ApiRiskLevel.MEDIUM, 21),
                api(WRITE_DELETE, "Platform IM delete", "DELETE",
                        "^/api/im/platform/friends/[^/]+$", ApiRiskLevel.MEDIUM, 22),
                api(INSTANCE_READ, "IM instance management read", "GET",
                        "^/api/im/surfaces/[^/]+/[^/]+/(members|join-requests)$", ApiRiskLevel.MEDIUM, 30),
                api(INSTANCE_WRITE, "IM instance management write", "POST",
                        "^/api/im/(join-requests/[^/]+/(approve|reject)|"
                                + "governance/(mute|announcement|ban))$",
                        ApiRiskLevel.HIGH, 31),
                api(INSTANCE_DELETE, "IM instance member removal", "DELETE",
                        "^/api/im/surfaces/[^/]+/[^/]+/members/[^/]+$", ApiRiskLevel.HIGH, 32),
                api(PLATFORM_ADMIN, "IM platform administration", "ANY",
                        "^/api/im/(channels|groups|governance/global-mute)$", ApiRiskLevel.HIGH, 40)
        );
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(
                        APP_CODE,
                        "IM_USER",
                        "IM User",
                        "System role for the platform IM workspace.",
                        60,
                        USER_PERMISSION_CODES,
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        "IM_ADMIN",
                        "IM Administrator",
                        "System role for platform-wide IM administration.",
                        50,
                        java.util.stream.Stream.concat(USER_PERMISSION_CODES.stream(), java.util.stream.Stream.of(PLATFORM_ADMIN))
                                .toList(),
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed menu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                MENU_IM,
                "即时通讯",
                null,
                PermissionStatus.ENABLED,
                25,
                null,
                new RbacMenuSeed("im", "/im", null, null, "MessageOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed api(String code, String name, String method, String pattern,
                                 ApiRiskLevel riskLevel, int sortNo) {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                code,
                name,
                PermissionStatus.ENABLED,
                sortNo,
                null,
                new RbacApiSeed(method, pattern, ApiMatcherType.REGEX, false, riskLevel),
                RbacResourceSource.PROVIDER
        );
    }
}
