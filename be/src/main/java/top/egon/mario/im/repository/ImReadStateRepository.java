package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImReadStatePo;

import java.util.Optional;

public interface ImReadStateRepository extends JpaRepository<ImReadStatePo, Long> {

    Optional<ImReadStatePo> findByConversationIdAndUserIdAndDeletedFalse(Long conversationId, Long userId);
}
