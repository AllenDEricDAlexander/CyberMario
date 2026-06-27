package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImDmPairPo;

import java.util.Optional;

public interface ImDmPairRepository extends JpaRepository<ImDmPairPo, Long> {

    Optional<ImDmPairPo> findByIdAndDeletedFalse(Long id);

    Optional<ImDmPairPo> findByUserLoIdAndUserHiIdAndDeletedFalse(Long userLoId, Long userHiId);

    default Optional<ImDmPairPo> findByOrderedUsers(Long userLoId, Long userHiId) {
        return findByUserLoIdAndUserHiIdAndDeletedFalse(userLoId, userHiId);
    }
}
