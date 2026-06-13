package top.egon.mario.rbac.service.bootstrap;

import java.util.List;

/**
 * Static role presets mapped to permission codes that may be seeded by feature bootstraps.
 */
public class RbacRolePresetCatalog {

    public List<RolePresetSeed> roles() {
        return List.of(
                new RolePresetSeed(
                        "RBAC_ADMIN",
                        "RBAC Administrator",
                        "System role for RBAC management.",
                        10,
                        List.of("api:rbac:admin:*")
                ),
                new RolePresetSeed(
                        "RAG_ADMIN",
                        "RAG Administrator",
                        "System role for full RAG management.",
                        20,
                        List.of(
                                "menu:rag", "menu:rag:chat", "menu:rag:knowledge-bases", "menu:rag:documents",
                                "menu:rag:ingestion-jobs", "menu:rag:retrieval-lab", "menu:rag:settings",
                                "btn:rag:kb:add", "btn:rag:kb:edit", "btn:rag:kb:delete", "btn:rag:kb:users",
                                "btn:rag:doc:upload", "btn:rag:doc:delete", "btn:rag:doc:reindex",
                                "btn:rag:chunk:toggle", "btn:rag:job:retry", "btn:rag:job:cancel",
                                "api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rag:knowledge-base:collection", "api:rag:knowledge-base:*",
                                "api:rag:document:collection", "api:rag:document:*",
                                "api:rag:chunk:*", "api:rag:ingestion-job:collection", "api:rag:ingestion-job:*"
                        )
                ),
                new RolePresetSeed(
                        "RAG_OPERATOR",
                        "RAG Operator",
                        "System role for operating RAG documents and ingestion jobs.",
                        30,
                        List.of(
                                "menu:rag", "menu:rag:chat", "menu:rag:knowledge-bases", "menu:rag:documents",
                                "menu:rag:ingestion-jobs", "menu:rag:retrieval-lab",
                                "btn:rag:doc:upload", "btn:rag:doc:delete", "btn:rag:doc:reindex",
                                "btn:rag:chunk:toggle", "btn:rag:job:retry", "btn:rag:job:cancel",
                                "api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rag:knowledge-base:collection",
                                "api:rag:document:collection", "api:rag:document:*",
                                "api:rag:chunk:*", "api:rag:ingestion-job:collection", "api:rag:ingestion-job:*"
                        )
                ),
                new RolePresetSeed(
                        "RAG_VIEWER",
                        "RAG Viewer",
                        "System role for viewing RAG resources and using RAG chat.",
                        40,
                        List.of(
                                "menu:rag", "menu:rag:chat", "menu:rag:knowledge-bases", "menu:rag:documents",
                                "menu:rag:ingestion-jobs", "menu:rag:retrieval-lab",
                                "api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rag:knowledge-base:collection", "api:rag:document:collection",
                                "api:rag:ingestion-job:collection"
                        )
                ),
                new RolePresetSeed(
                        "CHAT_USER",
                        "Chat User",
                        "System role for the agent chat console.",
                        50,
                        List.of("api:chat:stream")
                )
        );
    }

    public record RolePresetSeed(
            String roleCode,
            String roleName,
            String description,
            int sortNo,
            List<String> permissionCodes
    ) {
    }

}
