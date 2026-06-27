package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.enums.ImChannelVisibility;
import top.egon.mario.im.po.enums.ImSurfaceStatus;

import java.util.List;
import java.util.Optional;

public interface ImChannelRepository extends JpaRepository<ImChannelPo, Long> {

    Optional<ImChannelPo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select channel from ImChannelPo channel where channel.id = :id and channel.deleted = false")
    Optional<ImChannelPo> findLockedByIdAndDeletedFalse(Long id);

    Optional<ImChannelPo> findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
            String contextType, Long contextId, String channelKey);

    default Optional<ImChannelPo> findActiveByContextAndChannelKey(
            String contextType, Long contextId, String channelKey) {
        return findByContextAndChannelKeyAndStatus(contextType, contextId, channelKey, ImSurfaceStatus.ACTIVE);
    }

    @Query("""
            select channel
            from ImChannelPo channel
            where channel.contextType = :contextType
              and ((:contextId is null and channel.contextId is null) or channel.contextId = :contextId)
              and channel.channelKey = :channelKey
              and channel.status = :status
              and channel.deleted = false
            """)
    Optional<ImChannelPo> findByContextAndChannelKeyAndStatus(@Param("contextType") String contextType,
                                                              @Param("contextId") Long contextId,
                                                              @Param("channelKey") String channelKey,
                                                              @Param("status") ImSurfaceStatus status);

    default List<ImChannelPo> findActivePublicByContext(String contextType, Long contextId) {
        return findByContextAndVisibilityAndStatus(
                contextType, contextId, ImChannelVisibility.PUBLIC, ImSurfaceStatus.ACTIVE);
    }

    @Query("""
            select channel
            from ImChannelPo channel
            where channel.contextType = :contextType
              and ((:contextId is null and channel.contextId is null) or channel.contextId = :contextId)
              and channel.visibility = :visibility
              and channel.status = :status
              and channel.deleted = false
            order by channel.lastActiveAt desc, channel.id desc
            """)
    List<ImChannelPo> findByContextAndVisibilityAndStatus(@Param("contextType") String contextType,
                                                          @Param("contextId") Long contextId,
                                                          @Param("visibility") ImChannelVisibility visibility,
                                                          @Param("status") ImSurfaceStatus status);
}
