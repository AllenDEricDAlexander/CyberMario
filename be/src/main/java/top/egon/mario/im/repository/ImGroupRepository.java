package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.enums.ImSurfaceStatus;

import java.util.List;
import java.util.Optional;

public interface ImGroupRepository extends JpaRepository<ImGroupPo, Long> {

    Optional<ImGroupPo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select imGroup from ImGroupPo imGroup where imGroup.id = :id and imGroup.deleted = false")
    Optional<ImGroupPo> findLockedByIdAndDeletedFalse(Long id);

    Optional<ImGroupPo> findByChannelIdAndGroupKeyAndDeletedFalse(Long channelId, String groupKey);

    default Optional<ImGroupPo> findActiveByChannelIdAndGroupKey(Long channelId, String groupKey) {
        return findByChannelIdAndGroupKeyAndStatusAndDeletedFalse(channelId, groupKey, ImSurfaceStatus.ACTIVE);
    }

    Optional<ImGroupPo> findByChannelIdAndGroupKeyAndStatusAndDeletedFalse(
            Long channelId, String groupKey, ImSurfaceStatus status);

    default Optional<ImGroupPo> findActiveStandaloneByContextAndGroupKey(
            String contextType, Long contextId, String groupKey) {
        return findStandaloneByContextAndGroupKeyAndStatus(contextType, contextId, groupKey, ImSurfaceStatus.ACTIVE);
    }

    @Query("""
            select imGroup
            from ImGroupPo imGroup
            where imGroup.channelId is null
              and imGroup.contextType = :contextType
              and ((:contextId is null and imGroup.contextId is null) or imGroup.contextId = :contextId)
              and imGroup.groupKey = :groupKey
              and imGroup.status = :status
              and imGroup.deleted = false
            """)
    Optional<ImGroupPo> findStandaloneByContextAndGroupKeyAndStatus(@Param("contextType") String contextType,
                                                                    @Param("contextId") Long contextId,
                                                                    @Param("groupKey") String groupKey,
                                                                    @Param("status") ImSurfaceStatus status);

    default List<ImGroupPo> findActiveByChannelId(Long channelId) {
        return findByChannelIdAndStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(
                channelId, ImSurfaceStatus.ACTIVE);
    }

    List<ImGroupPo> findByChannelIdAndStatusAndDeletedFalseOrderByLastActiveAtDescIdDesc(
            Long channelId, ImSurfaceStatus status);

    default List<ImGroupPo> findActiveStandaloneByContext(String contextType, Long contextId) {
        return findStandaloneByContextAndStatus(contextType, contextId, ImSurfaceStatus.ACTIVE);
    }

    @Query("""
            select imGroup
            from ImGroupPo imGroup
            where imGroup.channelId is null
              and imGroup.contextType = :contextType
              and ((:contextId is null and imGroup.contextId is null) or imGroup.contextId = :contextId)
              and imGroup.status = :status
              and imGroup.deleted = false
            order by imGroup.lastActiveAt desc, imGroup.id desc
            """)
    List<ImGroupPo> findStandaloneByContextAndStatus(@Param("contextType") String contextType,
                                                     @Param("contextId") Long contextId,
                                                     @Param("status") ImSurfaceStatus status);
}
