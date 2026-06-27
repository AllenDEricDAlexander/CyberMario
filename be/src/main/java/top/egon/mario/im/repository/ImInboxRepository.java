package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImInboxPo;

import java.util.List;
import java.util.Optional;

public interface ImInboxRepository extends JpaRepository<ImInboxPo, Long> {

    Optional<ImInboxPo> findByIdAndDeletedFalse(Long id);

    List<ImInboxPo> findByUserIdAndReadFalseAndDeletedFalseOrderByMessageSeqAsc(Long userId);
}
