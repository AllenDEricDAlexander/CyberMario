package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.UserRolePo;

import java.util.Collection;
import java.util.List;

/**
 * Repository for user-role grants.
 */
public interface UserRoleRepository extends JpaRepository<UserRolePo, Long> {

    List<UserRolePo> findByUserId(Long userId);

    List<UserRolePo> findByUserIdIn(Collection<Long> userIds);

    List<UserRolePo> findByRoleId(Long roleId);

    List<UserRolePo> findByRoleIdAndUserIdIn(Long roleId, Collection<Long> userIds);

    void deleteByUserIdAndRoleIdIn(Long userId, Collection<Long> roleIds);

    boolean existsByRoleId(Long roleId);

}
