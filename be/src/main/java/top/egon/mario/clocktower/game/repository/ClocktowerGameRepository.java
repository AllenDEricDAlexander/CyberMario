package top.egon.mario.clocktower.game.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGameRepository extends JpaRepository<ClocktowerGamePo, Long> {

    Optional<ClocktowerGamePo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select game from ClocktowerGamePo game where game.id = :id and game.deleted = false")
    Optional<ClocktowerGamePo> findLockedByIdAndDeletedFalse(@Param("id") Long id);

    Optional<ClocktowerGamePo> findTopByRoomIdAndDeletedFalseOrderByGameNoDesc(Long roomId);

    List<ClocktowerGamePo> findByRoomIdAndDeletedFalseOrderByGameNoAsc(Long roomId);
}
