package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.converter.jpa.RbacStatusConverter;
import top.egon.mario.rbac.po.enums.RbacStatus;

import java.time.Instant;

/**
 * System user that can authenticate and receive roles.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_username_deleted", columnNames = {"username", "deleted"}),
        @UniqueConstraint(name = "uk_user_email_deleted", columnNames = {"email", "deleted"}),
        @UniqueConstraint(name = "uk_user_mobile_deleted", columnNames = {"mobile", "deleted"})
})
public class UserPo extends BaseAuditablePo {

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "nickname", length = 64)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "mobile", length = 32)
    private String mobile;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Convert(converter = RbacStatusConverter.class)
    @Column(name = "status", nullable = false)
    private RbacStatus status = RbacStatus.ENABLED;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "password_expired", nullable = false)
    private boolean passwordExpired;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "remark", length = 255)
    private String remark;

}
