package top.egon.mario.im.repository;

import top.egon.mario.im.po.ImReadStatePo;

import java.util.Optional;

public interface ImReadStateRepository {

    Optional<ImReadStatePo> findByConversationIdAndUserIdAndDeletedFalse(Long conversationId, Long userId);
}
