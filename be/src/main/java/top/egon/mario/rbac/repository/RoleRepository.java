package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rbac.po.RbacStatus;
import top.egon.mario.rbac.po.RolePo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for roles.
 */
public interface RoleRepository extends JpaRepository<RolePo, Long>, JpaSpecificationExecutor<RolePo> {

    Optional<RolePo> findByIdAndDeletedFalse(Long id);

    Optional<RolePo> findByRoleCodeAndDeletedFalse(String roleCode);

    List<RolePo> findByIdInAndDeletedFalse(Collection<Long> ids);

    List<RolePo> findByIdInAndDeletedFalseAndStatus(Collection<Long> ids, RbacStatus status);

    boolean existsByRoleCodeAndDeletedFalse(String roleCode);

}
