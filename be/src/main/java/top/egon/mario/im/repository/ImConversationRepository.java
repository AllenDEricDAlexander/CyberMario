package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.util.Optional;

public interface ImConversationRepository extends JpaRepository<ImConversationPo, Long> {

    Optional<ImConversationPo> findByIdAndDeletedFalse(Long id);

    Optional<ImConversationPo> findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
            ImSurfaceType ownerSurfaceType, Long ownerSurfaceId, ImConversationType conversationType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from ImConversationPo conversation where conversation.id = :id and conversation.deleted = false")
    Optional<ImConversationPo> findLockedByIdAndDeletedFalse(Long id);

}
