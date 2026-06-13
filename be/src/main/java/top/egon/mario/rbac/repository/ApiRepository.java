package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.Collection;
import java.util.List;

/**
 * Repository for API permission details and runtime authorization rules.
 */
public interface ApiRepository extends JpaRepository<ApiPo, Long> {

    List<ApiPo> findByPermissionIdIn(Collection<Long> permissionIds);

    @Query("""
            select new top.egon.mario.rbac.service.model.ApiPermissionRule(
                p.permCode, a.httpMethod, a.urlPattern, cast(a.matcherType as string), a.publicFlag)
            from ApiPo a
            join PermissionPo p on p.id = a.permissionId
            where p.deleted = false and p.status = top.egon.mario.rbac.po.PermissionStatus.ENABLED
            """)
    List<ApiPermissionRule> findEnabledRules();

}
