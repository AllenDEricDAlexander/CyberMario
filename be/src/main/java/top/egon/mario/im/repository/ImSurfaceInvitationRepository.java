package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImSurfaceInvitationPo;
import top.egon.mario.im.po.enums.ImSurfaceInvitationStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.util.Optional;

public interface ImSurfaceInvitationRepository extends JpaRepository<ImSurfaceInvitationPo, Long> {

    Optional<ImSurfaceInvitationPo> findByIdAndDeletedFalse(Long id);

    Optional<ImSurfaceInvitationPo> findBySurfaceTypeAndSurfaceIdAndInviteeUserIdAndDeletedFalse(
            ImSurfaceType surfaceType, Long surfaceId, Long inviteeUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation from ImSurfaceInvitationPo invitation
            where invitation.id = :id
              and invitation.deleted = false
            """)
    Optional<ImSurfaceInvitationPo> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation from ImSurfaceInvitationPo invitation
            where invitation.surfaceType = :surfaceType
              and invitation.surfaceId = :surfaceId
              and invitation.inviteeUserId = :inviteeUserId
              and invitation.deleted = false
            """)
    Optional<ImSurfaceInvitationPo> findLockedByTarget(@Param("surfaceType") ImSurfaceType surfaceType,
                                                       @Param("surfaceId") Long surfaceId,
                                                       @Param("inviteeUserId") Long inviteeUserId);

    Page<ImSurfaceInvitationPo> findByInviteeUserIdAndStatusAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long inviteeUserId, ImSurfaceInvitationStatus status, Pageable pageable);
}
