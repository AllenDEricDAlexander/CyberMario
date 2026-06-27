package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImBanPo;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;
import java.util.Optional;

public interface ImBanRepository extends JpaRepository<ImBanPo, Long> {

    Optional<ImBanPo> findByIdAndDeletedFalse(Long id);

    default Optional<ImBanPo> findActiveBan(ImSurfaceType surfaceType, Long surfaceId, Long userId, Instant now) {
        return findActiveBan(surfaceType, surfaceId, userId, ImGovernanceStatus.ACTIVE, now);
    }

    @Query("""
            select ban
            from ImBanPo ban
            where ban.surfaceType = :surfaceType
              and ban.surfaceId = :surfaceId
              and ban.userId = :userId
              and ban.status = :status
              and ban.deleted = false
              and (ban.expiresAt is null or ban.expiresAt > :now)
            """)
    Optional<ImBanPo> findActiveBan(@Param("surfaceType") ImSurfaceType surfaceType,
                                    @Param("surfaceId") Long surfaceId,
                                    @Param("userId") Long userId,
                                    @Param("status") ImGovernanceStatus status,
                                    @Param("now") Instant now);
}
