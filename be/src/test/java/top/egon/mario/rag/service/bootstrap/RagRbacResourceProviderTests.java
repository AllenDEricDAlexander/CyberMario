package top.egon.mario.rag.service.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RAG role presets include the self-service permissions required by normal users.
 */
class RagRbacResourceProviderTests {

    @Test
    void ragViewerCanUseRagAndCurrentUserSelfServiceApis() {
        RagRbacResourceProvider provider = new RagRbacResourceProvider();

        assertThat(provider.rolePresets())
                .filteredOn(seed -> "RAG_VIEWER".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rbac:auth:self", "api:rbac:me:self")
                        .doesNotContain("api:rbac:admin:*", "menu:rag:arxiv-logs"));
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
