package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.ApiMatcherType;
import top.egon.mario.rbac.dto.enums.ApiRiskLevel;
import top.egon.mario.rbac.dto.enums.PermissionStatus;
import top.egon.mario.rbac.dto.enums.PermissionType;

import java.util.Set;

/**
 * Creates or updates a permission and its type-specific detail.
 */
@Getter
@Setter
public class PermissionRequest {
    @NotBlank
    private String permCode;
    @NotBlank
    private String permName;
    @NotNull
    private PermissionType permType;
    private Long parentId;
    private PermissionStatus status = PermissionStatus.ENABLED;
    private int sortNo;
    private String description;
    private Menu menu;
    private Button button;
    private Api api;

    @Getter
    @Setter
    public static class Menu {
        private Long parentMenuId;
        private String routeName;
        private String routePath;
        private String component;
        private String redirect;
        private String icon;
        private boolean hidden;
        private boolean cacheable = true;
        private String externalLink;
    }

    @Getter
    @Setter
    public static class Button {
        private Long menuPermissionId;
        private String buttonKey;
        private String frontendAction;
        private String styleHint;
        private String description;
        private Set<Long> apiPermissionIds = Set.of();
    }

    @Getter
    @Setter
    public static class Api {
        private String httpMethod;
        private String urlPattern;
        private ApiMatcherType matcherType = ApiMatcherType.EXACT;
        private boolean publicFlag;
        private String serviceTag;
        private String operationName;
        private ApiRiskLevel riskLevel = ApiRiskLevel.LOW;
    }
}
