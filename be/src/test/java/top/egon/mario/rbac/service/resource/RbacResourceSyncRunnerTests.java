package top.egon.mario.rbac.service.resource;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies resource sync coordinates local providers before role presets are applied.
 */
class RbacResourceSyncRunnerTests {

    @Test
    void runSynchronizesAllResourcesBeforeAnyRolePreset() {
        RbacResourceSynchronizer synchronizer = mock(RbacResourceSynchronizer.class);
        AnnotationRbacResourceProvider annotationProvider = mock(AnnotationRbacResourceProvider.class);
        RbacResourceProvider rbacProvider = provider("rbac", List.of(authSelfApi()), List.of());
        RbacResourceProvider ragProvider = provider("rag", List.of(ragApi()), List.of(ragRolePreset()));
        when(annotationProvider.providers()).thenReturn(List.of(rbacProvider));
        RbacResourceSyncRunner runner = new RbacResourceSyncRunner(
                properties(true),
                synchronizer,
                List.of(ragProvider),
                annotationProvider
        );

        runner.run(null);

        InOrder inOrder = inOrder(synchronizer);
        inOrder.verify(synchronizer).synchronize("rag", List.of(ragApi()), List.of());
        inOrder.verify(synchronizer).synchronize("rbac", List.of(authSelfApi()), List.of());
        inOrder.verify(synchronizer).synchronize("rag", List.of(), List.of(ragRolePreset()));
    }

    private RbacResourceProvider provider(String appCode, List<RbacResourceSeed> resources, List<RbacRolePresetSeed> rolePresets) {
        return new RbacResourceProvider() {
            @Override
            public String appCode() {
                return appCode;
            }

            @Override
            public List<RbacResourceSeed> resources() {
                return resources;
            }

            @Override
            public List<RbacRolePresetSeed> rolePresets() {
                return rolePresets;
            }
        };
    }

    private RbacResourceSyncProperties properties(boolean enabled) {
        RbacResourceSyncProperties properties = new RbacResourceSyncProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private RbacResourceSeed authSelfApi() {
        return api("rbac", "api:rbac:auth:self", "/api/auth/**");
    }

    private RbacResourceSeed ragApi() {
        return api("rag", "api:rag:chat:stream", "/api/rag/chat/stream");
    }

    private RbacResourceSeed api(String appCode, String code, String pattern) {
        return RbacResourceSeed.api(
                appCode,
                appCode,
                code,
                code,
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed("ANY", pattern, ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacRolePresetSeed ragRolePreset() {
        return new RbacRolePresetSeed(
                "rag",
                "RAG_OPERATOR",
                "RAG Operator",
                "System role for operating RAG resources.",
                30,
                List.of("api:rag:chat:stream", "api:rbac:auth:self"),
                RbacResourceSource.PROVIDER
        );
    }

}
