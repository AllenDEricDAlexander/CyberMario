package top.egon.mario.room.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.room.po.RoomSpacePo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomSpaceRepository extends JpaRepository<RoomSpacePo, Long> {

    Optional<RoomSpacePo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomSpacePo room where room.id = :id and room.deleted = false")
    Optional<RoomSpacePo> findLockedByIdAndDeletedFalse(@Param("id") Long id);

    Optional<RoomSpacePo> findByContextTypeAndContextIdAndDeletedFalse(String contextType, Long contextId);

    List<RoomSpacePo> findByVisibilityInAndStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(
            Collection<String> visibilities, String status);

    List<RoomSpacePo> findByContextTypeAndVisibilityInAndStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(
            String contextType, Collection<String> visibilities, String status);

    boolean existsByRoomCodeAndDeletedFalse(String roomCode);
}
