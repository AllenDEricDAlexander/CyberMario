package top.egon.mario.im.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.util.List;

/**
 * Supplies coarse IM API permissions to the unified RBAC resource synchronizer.
 */
@Component
public class ImRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "im";
    private static final String READ_PATTERN =
            "^/api/im/(conversations|conversations/[^/]+/messages|channels|groups)$";
    private static final String WRITE_PATTERN =
            "^/api/im/(messages|conversations/[^/]+/read|channels|groups|join-requests|"
                    + "join-requests/[^/]+/cancel|surfaces/[^/]+/[^/]+/leave|dms|dms/(block|unblock)|ws-ticket)$";
    private static final String ADMIN_PATTERN =
            "^/api/im/(join-requests/[^/]+/(approve|reject)|"
                    + "governance/(mute|global-mute|announcement|ban))$";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(
                api("api:im:read", "IM Read API", "GET", READ_PATTERN, ApiRiskLevel.LOW, 10),
                api("api:im:write", "IM Write API", "POST", WRITE_PATTERN, ApiRiskLevel.MEDIUM, 20),
                api("api:im:admin", "IM Admin API", "POST", ADMIN_PATTERN, ApiRiskLevel.HIGH, 30)
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
