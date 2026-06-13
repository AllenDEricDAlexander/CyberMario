package top.egon.mario.rag.service.bootstrap;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies RAG RBAC resources to the unified RBAC resource synchronizer.
 */
@Component
public class RagRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "rag";
    private static final String AUTH_SELF_API_PERMISSION_CODE = "api:rbac:auth:self";

    private final RagPermissionCatalog catalog = new RagPermissionCatalog();

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        List<RbacResourceSeed> resources = new ArrayList<>();
        catalog.apis().forEach(seed -> resources.add(api(seed)));
        catalog.menus().forEach(seed -> resources.add(menu(seed)));
        catalog.buttons().forEach(seed -> resources.add(button(seed)));
        return resources;
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(
                        APP_CODE,
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
                                "api:rag:chunk:*", "api:rag:ingestion-job:collection", "api:rag:ingestion-job:*",
                                AUTH_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
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
                                "api:rag:chunk:*", "api:rag:ingestion-job:collection", "api:rag:ingestion-job:*",
                                AUTH_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        "RAG_VIEWER",
                        "RAG Viewer",
                        "System role for viewing RAG resources and using RAG chat.",
                        40,
                        List.of(
                                "menu:rag", "menu:rag:chat", "menu:rag:knowledge-bases", "menu:rag:documents",
                                "menu:rag:ingestion-jobs", "menu:rag:retrieval-lab",
                                "api:rag:chat:stream", "api:rag:retrieval:search",
                                "api:rag:knowledge-base:collection", "api:rag:document:collection",
                                "api:rag:ingestion-job:collection", AUTH_SELF_API_PERMISSION_CODE
                        ),
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed menu(RagPermissionCatalog.MenuPermissionSeed seed) {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                seed.parentPermCode(),
                PermissionStatus.ENABLED,
                seed.sortNo(),
                null,
                new RbacMenuSeed(seed.routeName(), seed.routePath(), null, null, "DatabaseOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed button(RagPermissionCatalog.ButtonPermissionSeed seed) {
        return RbacResourceSeed.button(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                seed.menuPermCode(),
                PermissionStatus.ENABLED,
                seed.sortNo(),
                null,
                new RbacButtonSeed(seed.buttonKey(), seed.buttonKey(), null),
                List.of(seed.apiPermCode()),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed api(RagPermissionCatalog.ApiPermissionSeed seed) {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                seed.permCode(),
                seed.permName(),
                PermissionStatus.ENABLED,
                0,
                null,
                new RbacApiSeed(seed.httpMethod(), seed.urlPattern(), seed.matcherType(), false, seed.riskLevel()),
                RbacResourceSource.PROVIDER
        );
    }

}
