package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImFriendshipPo;

import java.util.Optional;

public interface ImFriendshipRepository extends JpaRepository<ImFriendshipPo, Long> {

    Optional<ImFriendshipPo> findByIdAndDeletedFalse(Long id);

    Optional<ImFriendshipPo> findByUserLoIdAndUserHiIdAndDeletedFalse(Long userLoId, Long userHiId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select friendship from ImFriendshipPo friendship
            where friendship.userLoId = :userLoId
              and friendship.userHiId = :userHiId
              and friendship.deleted = false
            """)
    Optional<ImFriendshipPo> findLockedByUsers(@Param("userLoId") Long userLoId,
                                               @Param("userHiId") Long userHiId);
}
