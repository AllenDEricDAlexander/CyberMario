package top.egon.mario.rbac.dto;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.po.PermissionStatus;
import top.egon.mario.rbac.po.PermissionType;

/**
 * Unified permission payload with optional resource-specific details.
 */
@Getter
@Setter
public class PermissionResponse {

    private Long id;
    private String permCode;
    private String permName;
    private PermissionType permType;
    private Long parentId;
    private PermissionStatus status;
    private int sortNo;
    private String description;
    private MenuDetail menu;
    private ButtonDetail button;
    private ApiDetail api;

    @Getter
    @Setter
    public static class MenuDetail {
        private Long parentMenuId;
        private String routeName;
        private String routePath;
        private String component;
        private String redirect;
        private String icon;
        private boolean hidden;
        private boolean cacheable;
        private String externalLink;
    }

    @Getter
    @Setter
    public static class ButtonDetail {
        private Long menuPermissionId;
        private String buttonKey;
        private String frontendAction;
        private String styleHint;
        private String description;
        private java.util.Set<Long> apiPermissionIds;
    }

    @Getter
    @Setter
    public static class ApiDetail {
        private String httpMethod;
        private String urlPattern;
        private String matcherType;
        private boolean publicFlag;
        private String serviceTag;
        private String operationName;
        private String riskLevel;
    }

}
