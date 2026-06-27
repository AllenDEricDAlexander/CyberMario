package top.egon.mario.im.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImMessagePo;

import java.util.Optional;

public interface ImMessageRepository extends JpaRepository<ImMessagePo, Long> {

    Optional<ImMessagePo> findByIdAndDeletedFalse(Long id);

    Optional<ImMessagePo> findTopByConversationIdAndDeletedFalseOrderByMessageSeqDesc(Long conversationId);

    Optional<ImMessagePo> findByConversationIdAndSenderUserIdAndClientMsgIdAndDeletedFalse(
            Long conversationId, Long senderUserId, String clientMsgId);

    Page<ImMessagePo> findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(Long conversationId, Pageable pageable);

    Page<ImMessagePo> findByConversationIdAndMessageSeqGreaterThanEqualAndDeletedFalseOrderByMessageSeqAsc(
            Long conversationId, Long messageSeq, Pageable pageable);

    long countByConversationIdAndDeletedFalse(Long conversationId);
}
