package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.RolePermissionPo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for role-permission grants.
 */
public interface RolePermissionRepository extends JpaRepository<RolePermissionPo, Long> {

    List<RolePermissionPo> findByRoleId(Long roleId);

    List<RolePermissionPo> findByRoleIdIn(Collection<Long> roleIds);

    List<RolePermissionPo> findByPermissionId(Long permissionId);

    void deleteByRoleIdAndPermissionIdIn(Long roleId, Collection<Long> permissionIds);

    boolean existsByPermissionId(Long permissionId);

}
