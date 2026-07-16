package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for system users.
 */
public interface UserRepository extends JpaRepository<UserPo, Long>, JpaSpecificationExecutor<UserPo> {

    Optional<UserPo> findByAccountNoAndDeletedFalse(String accountNo);

    Optional<UserPo> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<UserPo> findByUsernameAndDeletedFalse(String username);

    Optional<UserPo> findByIdAndDeletedFalse(Long id);

    List<UserPo> findByIdInAndDeletedFalse(Collection<Long> ids);

    Page<UserPo> findByStatusAndDeletedFalse(RbacStatus status, Pageable pageable);

    @Query("""
            select user from UserPo user
            where user.deleted = false
              and user.status = :status
              and user.locked = false
              and user.id <> :excludedUserId
              and (lower(user.accountNo) = lower(:keyword)
                or locate(lower(:keyword), lower(coalesce(user.nickname, ''))) > 0)
            """)
    Page<UserPo> searchDirectory(@Param("keyword") String keyword,
                                 @Param("excludedUserId") Long excludedUserId,
                                 @Param("status") RbacStatus status,
                                 Pageable pageable);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByAccountNoAndDeletedFalse(String accountNo);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByMobileAndDeletedFalse(String mobile);

}
