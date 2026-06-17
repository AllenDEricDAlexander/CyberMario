package top.egon.mario.clocktower.room.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerRoomRepository extends JpaRepository<ClocktowerRoomPo, Long> {

    Optional<ClocktowerRoomPo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ClocktowerRoomPo room where room.id = :id and room.deleted = false")
    Optional<ClocktowerRoomPo> findLockedByIdAndDeletedFalse(Long id);

    Optional<ClocktowerRoomPo> findByRoomCodeAndDeletedFalse(String roomCode);

    List<ClocktowerRoomPo> findByStorytellerUserIdAndDeletedFalseOrderByIdDesc(Long storytellerUserId);

    boolean existsByRoomCodeAndDeletedFalse(String roomCode);
}
