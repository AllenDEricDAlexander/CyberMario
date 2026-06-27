package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImWsTicketPo;
import top.egon.mario.im.po.enums.ImWsTicketStatus;

import java.util.Optional;

public interface ImWsTicketRepository extends JpaRepository<ImWsTicketPo, Long> {

    Optional<ImWsTicketPo> findByIdAndDeletedFalse(Long id);

    Optional<ImWsTicketPo> findByTokenHashAndDeletedFalse(String tokenHash);

    Optional<ImWsTicketPo> findByTokenHashAndStatusAndDeletedFalse(String tokenHash, ImWsTicketStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from ImWsTicketPo ticket where ticket.tokenHash = :tokenHash and ticket.deleted = false")
    Optional<ImWsTicketPo> findLockedByTokenHashAndDeletedFalse(@Param("tokenHash") String tokenHash);
}
