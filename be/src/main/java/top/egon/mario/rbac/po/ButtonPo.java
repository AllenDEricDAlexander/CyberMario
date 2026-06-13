package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Button-specific resource details for a BUTTON permission.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_button", uniqueConstraints = {
        @UniqueConstraint(name = "uk_button_menu_key", columnNames = {"menu_permission_id", "button_key"})
})
public class ButtonPo {

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "menu_permission_id", nullable = false)
    private Long menuPermissionId;

    @Column(name = "button_key", nullable = false, length = 64)
    private String buttonKey;

    @Column(name = "frontend_action", length = 128)
    private String frontendAction;

    @Column(name = "style_hint", length = 64)
    private String styleHint;

    @Column(name = "description", length = 255)
    private String description;

}
