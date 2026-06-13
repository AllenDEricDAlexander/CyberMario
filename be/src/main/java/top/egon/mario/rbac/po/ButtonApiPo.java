package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Mapping between a front-end button permission and backend API permissions.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_button_api", uniqueConstraints = {
        @UniqueConstraint(name = "uk_button_api", columnNames = {"button_permission_id", "api_permission_id"})
})
public class ButtonApiPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "button_permission_id", nullable = false)
    private Long buttonPermissionId;

    @Column(name = "api_permission_id", nullable = false)
    private Long apiPermissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private ButtonApiRelationType relationType = ButtonApiRelationType.CALLS;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

}
