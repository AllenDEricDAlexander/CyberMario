package top.egon.mario.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.room.po.RoomBanPo;

import java.time.Instant;
import java.util.Optional;

public interface RoomBanRepository extends JpaRepository<RoomBanPo, Long> {

    Optional<RoomBanPo> findByRoomIdAndUserIdAndDeletedFalse(Long roomId, Long userId);

    @Query("""
            select ban
            from RoomBanPo ban
            where ban.roomId = :roomId
              and ban.userId = :userId
              and ban.status = 'ACTIVE'
              and ban.deleted = false
              and (ban.expiresAt is null or ban.expiresAt > :now)
            """)
    Optional<RoomBanPo> findActiveByRoomIdAndUserId(@Param("roomId") Long roomId,
                                                    @Param("userId") Long userId,
                                                    @Param("now") Instant now);
}
