package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImInboxPo;

import java.util.List;
import java.util.Optional;

public interface ImInboxRepository extends JpaRepository<ImInboxPo, Long> {

    Optional<ImInboxPo> findByIdAndDeletedFalse(Long id);

    List<ImInboxPo> findByUserIdAndReadFalseAndDeletedFalseOrderByMessageSeqAsc(Long userId);

    @Modifying
    @Query("""
            update ImInboxPo inbox
            set inbox.read = true
            where inbox.userId = :userId
              and inbox.conversationId = :conversationId
              and inbox.messageSeq <= :messageSeq
              and inbox.deleted = false
            """)
    int markReadUpTo(@Param("userId") Long userId,
                     @Param("conversationId") Long conversationId,
                     @Param("messageSeq") Long messageSeq);
}
