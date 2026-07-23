package top.egon.mario.rbac.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;

import java.util.Optional;

/**
 * Locked lookup, replacement, and revocation access for persisted one-time tokens.
 */
public interface OneTimeTokenRepository extends JpaRepository<OneTimeTokenPo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from OneTimeTokenPo token
            where token.tokenHash = :tokenHash and token.purpose = :purpose
            """)
    Optional<OneTimeTokenPo> findByHashForUpdate(@Param("tokenHash") String tokenHash,
                                                  @Param("purpose") OneTimeTokenPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from OneTimeTokenPo token
            where token.userId = :userId and token.purpose = :purpose
            """)
    Optional<OneTimeTokenPo> findByUserAndPurposeForUpdate(@Param("userId") Long userId,
                                                           @Param("purpose") OneTimeTokenPurpose purpose);
}
