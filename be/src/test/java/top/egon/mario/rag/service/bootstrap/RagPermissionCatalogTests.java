package top.egon.mario.rag.service.bootstrap;

import org.junit.jupiter.api.Test;

import top.egon.mario.rag.service.bootstrap.RagPermissionCatalog.ApiPermissionSeed;
import top.egon.mario.rag.service.bootstrap.RagPermissionCatalog.ButtonPermissionSeed;
import top.egon.mario.rag.service.bootstrap.RagPermissionCatalog.MenuPermissionSeed;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the RBAC permission seed catalog required by the RAG console.
 */
class RagPermissionCatalogTests {

    @Test
    void catalogContainsRagMenusButtonsAndApis() {
        RagPermissionCatalog catalog = new RagPermissionCatalog();

        assertThat(catalog.menus())
                .extracting(MenuPermissionSeed::permCode)
                .contains("menu:rag", "menu:rag:chat", "menu:rag:documents", "menu:rag:retrieval-lab");
        assertThat(catalog.buttons())
                .extracting(ButtonPermissionSeed::permCode)
                .contains("btn:rag:doc:upload", "btn:rag:doc:reindex", "btn:rag:kb:users");
        assertThat(catalog.apis())
                .extracting(ApiPermissionSeed::permCode)
                .contains("api:rag:chat:stream", "api:rag:retrieval:search",
                        "api:rag:document:collection", "api:rag:document:*");
    }

}
