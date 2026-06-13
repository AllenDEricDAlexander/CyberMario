package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.ButtonPo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for button details.
 */
public interface ButtonRepository extends JpaRepository<ButtonPo, Long> {

    List<ButtonPo> findByPermissionIdIn(Collection<Long> permissionIds);

    List<ButtonPo> findByMenuPermissionId(Long menuPermissionId);

    boolean existsByMenuPermissionId(Long menuPermissionId);

}
