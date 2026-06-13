package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.PermissionStatus;
import top.egon.mario.rbac.po.PermissionType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for unified permissions.
 */
public interface PermissionRepository extends JpaRepository<PermissionPo, Long>, JpaSpecificationExecutor<PermissionPo> {

    Optional<PermissionPo> findByIdAndDeletedFalse(Long id);

    Optional<PermissionPo> findByPermCodeAndDeletedFalse(String permCode);

    List<PermissionPo> findByIdInAndDeletedFalse(Collection<Long> ids);

    List<PermissionPo> findByIdInAndDeletedFalseAndStatus(Collection<Long> ids, PermissionStatus status);

    List<PermissionPo> findByParentIdAndDeletedFalse(Long parentId);

    List<PermissionPo> findByPermTypeAndDeletedFalseAndStatus(PermissionType permType, PermissionStatus status);

    boolean existsByPermCodeAndDeletedFalse(String permCode);

    boolean existsByParentIdAndDeletedFalse(Long parentId);

}
