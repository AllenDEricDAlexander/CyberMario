package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.ButtonApiPo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for button-API mappings.
 */
public interface ButtonApiRepository extends JpaRepository<ButtonApiPo, Long> {

    List<ButtonApiPo> findByButtonPermissionId(Long buttonPermissionId);

    List<ButtonApiPo> findByButtonPermissionIdIn(Collection<Long> buttonPermissionIds);

    List<ButtonApiPo> findByApiPermissionId(Long apiPermissionId);

    void deleteByButtonPermissionIdAndApiPermissionIdIn(Long buttonPermissionId, Collection<Long> apiPermissionIds);

    void deleteByButtonPermissionId(Long buttonPermissionId);

    boolean existsByButtonPermissionIdOrApiPermissionId(Long buttonPermissionId, Long apiPermissionId);

}
