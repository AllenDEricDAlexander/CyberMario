package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImDmPairPo;

import java.util.Optional;

public interface ImDmPairRepository extends JpaRepository<ImDmPairPo, Long> {

    Optional<ImDmPairPo> findByIdAndDeletedFalse(Long id);

    Optional<ImDmPairPo> findByUserLoIdAndUserHiIdAndDeletedFalse(Long userLoId, Long userHiId);

    default Optional<ImDmPairPo> findByOrderedUsers(Long userLoId, Long userHiId) {
        return findByUserLoIdAndUserHiIdAndDeletedFalse(userLoId, userHiId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pair from ImDmPairPo pair
            where pair.userLoId = :userLoId
              and pair.userHiId = :userHiId
              and pair.deleted = false
            """)
    Optional<ImDmPairPo> findLockedByUserLoIdAndUserHiIdAndDeletedFalse(@Param("userLoId") Long userLoId,
                                                                        @Param("userHiId") Long userHiId);
}
