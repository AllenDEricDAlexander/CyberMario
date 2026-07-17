package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ImJoinRequestRepository extends JpaRepository<ImJoinRequestPo, Long> {

    Optional<ImJoinRequestPo> findByIdAndDeletedFalse(Long id);

    Optional<ImJoinRequestPo> findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, Long userId, ImJoinRequestStatus status);

    Page<ImJoinRequestPo> findBySurfaceTypeAndSurfaceIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, ImJoinRequestStatus status, Pageable pageable);

    List<ImJoinRequestPo> findBySurfaceTypeAndSurfaceIdInAndUserIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Collection<Long> surfaceIds, Long userId, ImJoinRequestStatus status);
}
