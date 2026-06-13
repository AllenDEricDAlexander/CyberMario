package top.egon.mario.rbac.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node used by current-user and management menu APIs.
 */
@Getter
@Setter
public class MenuTreeResponse {

    private Long permissionId;
    private String permCode;
    private String permName;
    private String routeName;
    private String routePath;
    private String component;
    private String redirect;
    private String icon;
    private boolean hidden;
    private boolean cacheable;
    private String externalLink;
    private int sortNo;
    private List<MenuTreeResponse> children = new ArrayList<>();

}
