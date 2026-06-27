package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.util.Optional;

public interface ImJoinRequestRepository extends JpaRepository<ImJoinRequestPo, Long> {

    Optional<ImJoinRequestPo> findByIdAndDeletedFalse(Long id);

    Optional<ImJoinRequestPo> findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, Long userId, ImJoinRequestStatus status);
}
