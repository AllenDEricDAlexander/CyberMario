package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImMembershipRepository extends JpaRepository<ImMembershipPo, Long> {

    Optional<ImMembershipPo> findByIdAndDeletedFalse(Long id);

    Optional<ImMembershipPo> findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, Long userId, ImMembershipStatus status);

    Optional<ImMembershipPo> findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, Long userId);

    List<ImMembershipPo> findBySurfaceTypeAndSurfaceIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, ImMembershipStatus status);

    List<ImMembershipPo> findBySurfaceTypeAndSurfaceIdInAndUserIdAndDeletedFalse(
            ImSurfaceType surfaceType, Collection<Long> surfaceIds, Long userId);

    List<ImMembershipPo> findByUserIdAndStatusAndDeletedFalse(Long userId, ImMembershipStatus status);
}
