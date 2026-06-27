package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.enums.ImMembershipStatus;

import java.util.List;
import java.util.Optional;

public interface ImConversationMemberRepository extends JpaRepository<ImConversationMemberPo, Long> {

    Optional<ImConversationMemberPo> findByConversationIdAndUserIdAndDeletedFalse(Long conversationId, Long userId);

    Optional<ImConversationMemberPo> findByConversationIdAndUserIdAndStatusAndDeletedFalse(
            Long conversationId, Long userId, ImMembershipStatus status);

    default Optional<ImConversationMemberPo> findByConversationIdAndUserIdAndStatusAndDeletedFalse(
            Long conversationId, Long userId, String status) {
        return findByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, ImMembershipStatus.valueOf(status));
    }

    default Optional<ImConversationMemberPo> findActiveByConversationIdAndUserId(Long conversationId, Long userId) {
        return findByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, ImMembershipStatus.ACTIVE);
    }

    List<ImConversationMemberPo> findByConversationIdAndDeletedFalse(Long conversationId);

    List<ImConversationMemberPo> findByUserIdAndStatusAndDeletedFalse(Long userId, ImMembershipStatus status);

    default List<ImConversationMemberPo> findActiveByUserId(Long userId) {
        return findByUserIdAndStatusAndDeletedFalse(userId, ImMembershipStatus.ACTIVE);
    }

    boolean existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
            Long conversationId, Long userId, ImMembershipStatus status);

    default boolean existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
            Long conversationId, Long userId, String status) {
        return existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, ImMembershipStatus.valueOf(status));
    }
}
