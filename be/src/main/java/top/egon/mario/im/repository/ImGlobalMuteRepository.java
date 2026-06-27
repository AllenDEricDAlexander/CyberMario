package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImGlobalMutePo;
import top.egon.mario.im.po.enums.ImGlobalMuteScopeType;
import top.egon.mario.im.po.enums.ImGovernanceStatus;

import java.time.Instant;
import java.util.Optional;

public interface ImGlobalMuteRepository extends JpaRepository<ImGlobalMutePo, Long> {

    Optional<ImGlobalMutePo> findByIdAndDeletedFalse(Long id);

    default Optional<ImGlobalMutePo> findActiveMute(
            Long userId, ImGlobalMuteScopeType scopeType, Long scopeId, Instant now) {
        return findActiveMute(userId, scopeType, scopeId, ImGovernanceStatus.ACTIVE, now);
    }

    @Query("""
            select mute
            from ImGlobalMutePo mute
            where mute.userId = :userId
              and mute.scopeType = :scopeType
              and mute.scopeId = :scopeId
              and mute.status = :status
              and mute.deleted = false
              and (mute.expiresAt is null or mute.expiresAt > :now)
            """)
    Optional<ImGlobalMutePo> findActiveMute(@Param("userId") Long userId,
                                            @Param("scopeType") ImGlobalMuteScopeType scopeType,
                                            @Param("scopeId") Long scopeId,
                                            @Param("status") ImGovernanceStatus status,
                                            @Param("now") Instant now);
}
