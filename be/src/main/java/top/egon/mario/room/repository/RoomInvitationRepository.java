package top.egon.mario.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.room.po.RoomInvitationPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitationPo, Long> {

    @Query("""
            select invitation
            from RoomInvitationPo invitation
            where invitation.id = :id
              and invitation.roomId = :roomId
              and invitation.activeStatus = true
              and invitation.deleted = false
            """)
    Optional<RoomInvitationPo> findActiveByIdAndRoomId(@Param("id") Long id, @Param("roomId") Long roomId);

    @Query("""
            select invitation
            from RoomInvitationPo invitation
            where invitation.roomId = :roomId
              and invitation.activeStatus = true
              and invitation.deleted = false
            order by invitation.id asc
            """)
    List<RoomInvitationPo> findActiveByRoomId(@Param("roomId") Long roomId);

    @Query("""
            select invitation
            from RoomInvitationPo invitation
            where invitation.roomId = :roomId
              and invitation.inviteeUserId = :inviteeUserId
              and invitation.activeStatus = true
              and invitation.deleted = false
            order by invitation.id asc
            """)
    List<RoomInvitationPo> findActiveByRoomIdAndInviteeUserId(@Param("roomId") Long roomId,
                                                              @Param("inviteeUserId") Long inviteeUserId);

    @Query("""
            select invitation
            from RoomInvitationPo invitation
            where invitation.roomId = :roomId
              and invitation.targetSeatNo is not null
              and invitation.activeStatus = true
              and invitation.deleted = false
              and (invitation.expiresAt is null or invitation.expiresAt > :now)
            order by invitation.targetSeatNo asc, invitation.id asc
            """)
    List<RoomInvitationPo> findActiveTargetSeatReservations(@Param("roomId") Long roomId,
                                                            @Param("now") Instant now);

    boolean existsByRoomIdAndTargetSeatNoAndActiveStatusTrueAndDeletedFalse(Long roomId, Integer targetSeatNo);

    boolean existsByInvitationCodeAndDeletedFalse(String invitationCode);

}
