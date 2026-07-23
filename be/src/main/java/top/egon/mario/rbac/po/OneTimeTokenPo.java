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
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;

import java.time.Instant;

/**
 * JPA mapping containing only one-time token hashes and metadata.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_one_time_token", uniqueConstraints = {
        @UniqueConstraint(name = "uk_one_time_token_hash", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_one_time_token_user_purpose", columnNames = {"user_id", "purpose"})
})
public class OneTimeTokenPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private OneTimeTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;
}
