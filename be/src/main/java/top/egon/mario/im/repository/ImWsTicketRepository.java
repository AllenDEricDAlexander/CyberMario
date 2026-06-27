package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImWsTicketPo;
import top.egon.mario.im.po.enums.ImWsTicketStatus;

import java.util.Optional;

public interface ImWsTicketRepository extends JpaRepository<ImWsTicketPo, Long> {

    Optional<ImWsTicketPo> findByIdAndDeletedFalse(Long id);

    Optional<ImWsTicketPo> findByTokenHashAndDeletedFalse(String tokenHash);

    Optional<ImWsTicketPo> findByTokenHashAndStatusAndDeletedFalse(String tokenHash, ImWsTicketStatus status);
}
