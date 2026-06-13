package top.egon.mario.rbac.service.resource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.MarioApplication;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.resource.annotation.RbacResourceModule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC annotations can be converted into resource seeds.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=false"
}, classes = {MarioApplication.class, AnnotationRbacResourceProviderTests.TestRbacResourceModule.class,
        AnnotationRbacResourceProviderTests.TestRbacController.class})
class AnnotationRbacResourceProviderTests {

    @Autowired
    private AnnotationRbacResourceProvider annotationProvider;

    @Test
    void providersIncludeMethodLevelApiWithMappingDetails() {
        assertThat(annotationProvider.providers())
                .flatExtracting(RbacResourceProvider::resources)
                .anySatisfy(seed -> {
                    assertThat(seed.code()).isEqualTo("api:test:example:get");
                    assertThat(seed.type()).isEqualTo(PermissionType.API);
                    assertThat(seed.api().httpMethod()).isEqualTo("GET");
                    assertThat(seed.api().urlPattern()).isEqualTo("/internal/example");
                    assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.EXACT);
                    assertThat(seed.api().riskLevel()).isEqualTo(ApiRiskLevel.MEDIUM);
                });
    }

    @Test
    void providersIncludeMethodLevelApiWithoutResourceModule() {
        assertThat(annotationProvider.providers())
                .filteredOn(provider -> provider.appCode().equals("nomodule"))
                .flatExtracting(RbacResourceProvider::resources)
                .anySatisfy(seed -> {
                    assertThat(seed.code()).isEqualTo("api:nomodule:example:post");
                    assertThat(seed.serviceTag()).isEqualTo("nomodule");
                    assertThat(seed.api().httpMethod()).isEqualTo("POST");
                    assertThat(seed.api().urlPattern()).isEqualTo("/internal/no-module");
                });
    }

    @Test
    void providersIncludeAuthSelfApiAnnotation() {
        assertThat(annotationProvider.providers())
                .filteredOn(provider -> provider.appCode().equals("rbac"))
                .flatExtracting(RbacResourceProvider::resources)
                .anySatisfy(seed -> {
                    assertThat(seed.code()).isEqualTo("api:rbac:auth:self");
                    assertThat(seed.api().httpMethod()).isEqualTo("ANY");
                    assertThat(seed.api().urlPattern()).isEqualTo("/api/auth/**");
                    assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.ANT);
                });
    }

    @Configuration
    @RbacResourceModule(appCode = "test", name = "Test", codePrefixes = {"api:test:"})
    static class TestRbacResourceModule {
    }

    @RestController
    static class TestRbacController {

        @RbacApi(code = "api:test:example:get", name = "Test Example", risk = ApiRiskLevel.MEDIUM)
        @GetMapping("/internal/example")
        Mono<String> example() {
            return Mono.just("ok");
        }

        @RbacApi(appCode = "nomodule", code = "api:nomodule:example:post", name = "No Module Example")
        @PostMapping("/internal/no-module")
        Mono<String> noModule() {
            return Mono.just("ok");
        }

    }

}
