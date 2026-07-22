package top.egon.mario.agent.externalim.runtime.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExternalChatEventRepository extends JpaRepository<ExternalChatEventPo, Long> {

    Optional<ExternalChatEventPo> findByPlatformAndConnectorIdAndExternalEventId(
            ExternalChatPlatform platform, String connectorId, String externalEventId);

    List<ExternalChatEventPo>
    findByProcessingStatusAndAvailableAtLessThanEqualOrderByReceivedAtAscIdAsc(
            ExternalChatProcessingStatus status, Instant availableAt, Pageable pageable);

    List<ExternalChatEventPo>
    findByProcessingStatusAndLockedAtLessThanOrderByLockedAtAscIdAsc(
            ExternalChatProcessingStatus status, Instant lockedBefore, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ExternalChatEventPo event
            set event.processingStatus = :running,
                event.lockedAt = :now,
                event.lockedBy = :workerId,
                event.updatedAt = :now,
                event.version = event.version + 1
            where event.id = :eventId
              and event.processingStatus = :received
              and event.availableAt <= :now
            """)
    int claimReady(@Param("eventId") Long eventId,
                   @Param("workerId") String workerId,
                   @Param("now") Instant now,
                   @Param("received") ExternalChatProcessingStatus received,
                   @Param("running") ExternalChatProcessingStatus running);
}
