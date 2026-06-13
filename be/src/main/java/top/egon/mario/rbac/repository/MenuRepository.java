package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.MenuPo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for menu details.
 */
public interface MenuRepository extends JpaRepository<MenuPo, Long> {

    List<MenuPo> findByPermissionIdIn(Collection<Long> permissionIds);

    boolean existsByParentMenuId(Long parentMenuId);

}
