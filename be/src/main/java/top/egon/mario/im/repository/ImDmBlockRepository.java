package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImDmBlockPo;
import top.egon.mario.im.po.enums.ImGovernanceStatus;

import java.util.Optional;

public interface ImDmBlockRepository extends JpaRepository<ImDmBlockPo, Long> {

    Optional<ImDmBlockPo> findByIdAndDeletedFalse(Long id);

    default Optional<ImDmBlockPo> findActiveBlock(Long blockerUserId, Long blockedUserId) {
        return findByBlockerUserIdAndBlockedUserIdAndStatusAndDeletedFalse(
                blockerUserId, blockedUserId, ImGovernanceStatus.ACTIVE);
    }

    Optional<ImDmBlockPo> findByBlockerUserIdAndBlockedUserIdAndStatusAndDeletedFalse(
            Long blockerUserId, Long blockedUserId, ImGovernanceStatus status);

    Optional<ImDmBlockPo> findByBlockerUserIdAndBlockedUserIdAndDeletedFalse(
            Long blockerUserId, Long blockedUserId);

    @Query("""
            select count(block) from ImDmBlockPo block
            where block.status = top.egon.mario.im.po.enums.ImGovernanceStatus.ACTIVE
              and block.deleted = false
              and ((block.blockerUserId = :firstUserId and block.blockedUserId = :secondUserId)
                or (block.blockerUserId = :secondUserId and block.blockedUserId = :firstUserId))
            """)
    long countActiveBetween(@Param("firstUserId") Long firstUserId, @Param("secondUserId") Long secondUserId);
}
