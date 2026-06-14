package top.egon.mario.rag.service.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RAG role presets include the self-service permissions required by normal users.
 */
class RagRbacResourceProviderTests {

    @Test
    void ragUserCanUseRagAndCurrentUserSelfServiceApis() {
        RagRbacResourceProvider provider = new RagRbacResourceProvider();

        assertThat(provider.rolePresets())
                .filteredOn(seed -> "RAG_USER".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rag:feedback:create", "btn:rag:doc:upload",
                                "api:rbac:auth:self", "api:rbac:me:self")
                        .doesNotContain("api:rbac:admin:*", "menu:rag:arxiv-logs"));
    }

    @Test
    void resourcesContainHybridRagButtonsAndTraceApis() {
        RagRbacResourceProvider provider = new RagRbacResourceProvider();

        assertThat(provider.resources())
                .extracting("code")
                .contains(
                        "btn:rag:kb:retrieval-config",
                        "btn:rag:doc:import-arxiv",
                        "btn:rag:retrieval:debug",
                        "btn:rag:retrieval:trace",
                        "btn:rag:feedback:create",
                        "api:rag:retrieval:trace",
                        "api:rag:feedback:create",
                        "api:rag:settings:read"
                );
    }

    @Test
    void resourcesContainSuperAdminArxivLogsMenuUnderRag() {
        RagRbacResourceProvider provider = new RagRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> "menu:rag:arxiv-logs".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.parentCode()).isEqualTo("menu:rag");
                    assertThat(seed.menu().routePath()).isEqualTo("/rag/arxiv-logs");
                });
    }

}
