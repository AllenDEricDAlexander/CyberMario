package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.RoleInheritancePo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for RBAC1 role inheritance edges.
 */
public interface RoleInheritanceRepository extends JpaRepository<RoleInheritancePo, Long> {

    List<RoleInheritancePo> findByRoleId(Long roleId);

    List<RoleInheritancePo> findByRoleIdIn(Collection<Long> roleIds);

    List<RoleInheritancePo> findByInheritedRoleId(Long inheritedRoleId);

    void deleteByRoleIdAndInheritedRoleIdIn(Long roleId, Collection<Long> inheritedRoleIds);

    boolean existsByRoleIdOrInheritedRoleId(Long roleId, Long inheritedRoleId);

}
