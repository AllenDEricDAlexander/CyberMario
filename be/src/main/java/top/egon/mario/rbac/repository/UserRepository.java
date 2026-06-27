package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rbac.po.UserPo;

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

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByAccountNoAndDeletedFalse(String accountNo);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByMobileAndDeletedFalse(String mobile);

}
