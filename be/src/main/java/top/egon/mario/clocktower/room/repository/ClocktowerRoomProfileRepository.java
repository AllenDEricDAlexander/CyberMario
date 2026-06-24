package top.egon.mario.clocktower.room.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClocktowerRoomProfileRepository extends JpaRepository<ClocktowerRoomProfilePo, Long> {

    Optional<ClocktowerRoomProfilePo> findByRoomId(Long roomId);

    Optional<ClocktowerRoomProfilePo> findByRoomIdAndDeletedFalse(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select profile
            from ClocktowerRoomProfilePo profile
            where profile.roomId = :roomId
              and profile.deleted = false
            """)
    Optional<ClocktowerRoomProfilePo> findLockedByRoomId(@Param("roomId") Long roomId);

    List<ClocktowerRoomProfilePo> findByRoomIdInAndDeletedFalse(Collection<Long> roomIds);

    List<ClocktowerRoomProfilePo> findByStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(String status);
}
