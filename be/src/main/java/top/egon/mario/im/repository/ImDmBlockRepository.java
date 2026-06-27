package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
