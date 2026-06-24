package top.egon.mario.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.room.po.RoomMemberPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMemberPo, Long> {

    @Query("""
            select member
            from RoomMemberPo member
            where member.roomId = :roomId
              and member.userId = :userId
              and member.activeStatus = true
              and member.deleted = false
            """)
    Optional<RoomMemberPo> findActiveByRoomIdAndUserId(@Param("roomId") Long roomId,
                                                       @Param("userId") Long userId);

    @Query("""
            select member
            from RoomMemberPo member
            where member.roomId = :roomId
              and member.activeStatus = true
              and member.deleted = false
            order by member.id asc
            """)
    List<RoomMemberPo> findActiveByRoomId(@Param("roomId") Long roomId);

    List<RoomMemberPo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<RoomMemberPo> findByRoomIdAndUserIdAndDeletedFalseOrderByIdAsc(Long roomId, Long userId);

    boolean existsByRoomIdAndSeatNoAndActiveStatusTrueAndDeletedFalse(Long roomId, Integer seatNo);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RoomMemberPo member
            set member.lastActiveAt = :lastActiveAt
            where member.roomId = :roomId
              and member.userId = :userId
              and member.activeStatus = true
              and member.deleted = false
            """)
    int updateLastActiveAtForActiveMember(@Param("roomId") Long roomId,
                                          @Param("userId") Long userId,
                                          @Param("lastActiveAt") Instant lastActiveAt);
}
