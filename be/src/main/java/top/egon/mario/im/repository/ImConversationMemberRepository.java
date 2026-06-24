package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImConversationMemberPo;

import java.util.List;
import java.util.Optional;

public interface ImConversationMemberRepository extends JpaRepository<ImConversationMemberPo, Long> {

    Optional<ImConversationMemberPo> findByConversationIdAndUserIdAndDeletedFalse(Long conversationId, Long userId);

    Optional<ImConversationMemberPo> findByConversationIdAndUserIdAndStatusAndDeletedFalse(
            Long conversationId, Long userId, String status);

    List<ImConversationMemberPo> findByConversationIdAndDeletedFalse(Long conversationId);

    boolean existsByConversationIdAndUserIdAndStatusAndDeletedFalse(Long conversationId, Long userId, String status);
}
