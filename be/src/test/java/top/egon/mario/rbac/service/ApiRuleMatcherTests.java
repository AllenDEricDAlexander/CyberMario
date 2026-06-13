package top.egon.mario.rbac.service;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRuleMatcherTests {

    private final ApiRuleMatcher matcher = new ApiRuleMatcher();

    @Test
    void matchPrefersExactMethodAndExactPath() {
        Optional<ApiPermissionRule> rule = matcher.match("GET", "/api/admin/users", List.of(
                new ApiPermissionRule("api:any", "ANY", "/api/admin/users", "EXACT", false),
                new ApiPermissionRule("api:list", "GET", "/api/admin/users", "EXACT", false),
                new ApiPermissionRule("api:wildcard", "GET", "/api/admin/**", "ANT", false)
        ));

        assertThat(rule).contains(new ApiPermissionRule("api:list", "GET", "/api/admin/users", "EXACT", false));
    }

    @Test
    void matchFallsBackToAnyMethodWhenSpecificMethodDoesNotMatch() {
        Optional<ApiPermissionRule> rule = matcher.match("DELETE", "/api/admin/users/1", List.of(
                new ApiPermissionRule("api:list", "GET", "/api/admin/users/{id}", "MVC", false),
                new ApiPermissionRule("api:any-user", "ANY", "/api/admin/users/{id}", "MVC", false)
        ));

        assertThat(rule).contains(new ApiPermissionRule("api:any-user", "ANY", "/api/admin/users/{id}", "MVC", false));
    }

}
