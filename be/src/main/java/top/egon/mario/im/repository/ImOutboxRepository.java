package top.egon.mario.im.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.enums.ImOutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImOutboxRepository extends JpaRepository<ImOutboxPo, Long> {

    Optional<ImOutboxPo> findByIdAndDeletedFalse(Long id);

    default List<ImOutboxPo> claimPendingForDispatch(Instant availableAt, Pageable pageable) {
        return claimPendingForDispatch(ImOutboxStatus.PENDING, availableAt, pageable);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from ImOutboxPo outbox
            where outbox.status = :status
              and outbox.availableAt <= :availableAt
              and outbox.deleted = false
            order by outbox.availableAt asc, outbox.id asc
            """)
    List<ImOutboxPo> claimPendingForDispatch(@Param("status") ImOutboxStatus status,
                                             @Param("availableAt") Instant availableAt,
                                             Pageable pageable);

    @Query(value = """
            select *
            from im_outbox
            where status = 'PENDING'
              and available_at <= :availableAt
              and deleted = false
            order by available_at asc, id asc
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<ImOutboxPo> claimPendingForDispatchPostgreSql(@Param("availableAt") Instant availableAt,
                                                       @Param("limit") int limit);
}
