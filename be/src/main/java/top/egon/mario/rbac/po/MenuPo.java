package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Menu-specific resource details for a MENU permission.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_menu", uniqueConstraints = {
        @UniqueConstraint(name = "uk_menu_route_name", columnNames = {"route_name"})
})
public class MenuPo {

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "parent_menu_id")
    private Long parentMenuId;

    @Column(name = "route_name", length = 128)
    private String routeName;

    @Column(name = "route_path", length = 255)
    private String routePath;

    @Column(name = "component", length = 255)
    private String component;

    @Column(name = "redirect", length = 255)
    private String redirect;

    @Column(name = "icon", length = 128)
    private String icon;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "cacheable", nullable = false)
    private boolean cacheable = true;

    @Column(name = "external_link", length = 512)
    private String externalLink;

}
