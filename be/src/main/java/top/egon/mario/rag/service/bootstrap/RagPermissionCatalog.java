package top.egon.mario.rag.service.bootstrap;

import org.springframework.http.HttpMethod;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

import java.util.List;

/**
 * Static RBAC permission seed catalog required by the RAG console.
 */
public class RagPermissionCatalog {

    /**
     * Returns menu permissions used by the frontend route tree.
     */
    public List<MenuPermissionSeed> menus() {
        return List.of(
                new MenuPermissionSeed("menu:rag", "RAG 管理", null, null, "rag", 20),
                new MenuPermissionSeed("menu:rag:chat", "RAG 问答", "menu:rag", "/rag/chat", "rag-chat", 21),
                new MenuPermissionSeed("menu:rag:knowledge-bases", "知识库管理", "menu:rag", "/rag/knowledge-bases", "rag-knowledge-bases", 22),
                new MenuPermissionSeed("menu:rag:documents", "文档管理", "menu:rag", "/rag/documents", "rag-documents", 23),
                new MenuPermissionSeed("menu:rag:ingestion-jobs", "入库任务", "menu:rag", "/rag/ingestion-jobs", "rag-ingestion-jobs", 24),
                new MenuPermissionSeed("menu:rag:retrieval-lab", "检索调试", "menu:rag", "/rag/retrieval-lab", "rag-retrieval-lab", 25),
                new MenuPermissionSeed("menu:rag:settings", "RAG 设置", "menu:rag", "/rag/settings", "rag-settings", 26),
                new MenuPermissionSeed("menu:rag:arxiv-logs", "arXiv 工具日志", "menu:rag", "/rag/arxiv-logs", "rag-arxiv-logs", 27)
        );
    }

    /**
     * Returns button permissions used by RAG page actions.
     */
    public List<ButtonPermissionSeed> buttons() {
        return List.of(
                new ButtonPermissionSeed("btn:rag:kb:add", "新建知识库", "menu:rag:knowledge-bases", "create", "api:rag:knowledge-base:*", 1),
                new ButtonPermissionSeed("btn:rag:kb:edit", "编辑知识库", "menu:rag:knowledge-bases", "edit", "api:rag:knowledge-base:*", 2),
                new ButtonPermissionSeed("btn:rag:kb:delete", "删除知识库", "menu:rag:knowledge-bases", "delete", "api:rag:knowledge-base:*", 3),
                new ButtonPermissionSeed("btn:rag:kb:users", "知识库用户授权", "menu:rag:knowledge-bases", "users", "api:rag:knowledge-base:*", 4),
                new ButtonPermissionSeed("btn:rag:kb:retrieval-config", "修改检索配置", "menu:rag:knowledge-bases", "retrievalConfig", "api:rag:knowledge-base:*", 5),
                new ButtonPermissionSeed("btn:rag:doc:upload", "上传文档", "menu:rag:documents", "upload", "api:rag:document:*", 1),
                new ButtonPermissionSeed("btn:rag:doc:import-text", "导入文本", "menu:rag:documents", "importText", "api:rag:document:*", 2),
                new ButtonPermissionSeed("btn:rag:doc:import-arxiv", "导入 arXiv", "menu:rag:documents", "importArxiv", "api:rag:document:*", 3),
                new ButtonPermissionSeed("btn:rag:doc:delete", "删除文档", "menu:rag:documents", "delete", "api:rag:document:*", 4),
                new ButtonPermissionSeed("btn:rag:doc:reindex", "重建索引", "menu:rag:documents", "reindex", "api:rag:document:*", 5),
                new ButtonPermissionSeed("btn:rag:chunk:toggle", "启用禁用切片", "menu:rag:documents", "toggleChunk", "api:rag:chunk:*", 6),
                new ButtonPermissionSeed("btn:rag:job:retry", "重试入库任务", "menu:rag:ingestion-jobs", "retry", "api:rag:ingestion-job:*", 1),
                new ButtonPermissionSeed("btn:rag:job:cancel", "取消入库任务", "menu:rag:ingestion-jobs", "cancel", "api:rag:ingestion-job:*", 2),
                new ButtonPermissionSeed("btn:rag:retrieval:debug", "执行检索调试", "menu:rag:retrieval-lab", "debug", "api:rag:retrieval:search", 1),
                new ButtonPermissionSeed("btn:rag:retrieval:trace", "查看检索 Trace", "menu:rag:retrieval-lab", "trace", "api:rag:retrieval:trace", 2),
                new ButtonPermissionSeed("btn:rag:feedback:create", "提交反馈", "menu:rag:chat", "feedback", "api:rag:feedback:create", 1)
        );
    }

    /**
     * Returns API permissions enforced by the dynamic authorization manager.
     */
    public List<ApiPermissionSeed> apis() {
        return List.of(
                new ApiPermissionSeed("api:rag:chat:stream", "RAG 流式问答", HttpMethod.POST.name(), "/api/rag/chat/stream", ApiMatcherType.EXACT, ApiRiskLevel.MEDIUM),
                new ApiPermissionSeed("api:rag:retrieval:search", "RAG 检索调试", HttpMethod.POST.name(), "/api/rag/retrieval/search", ApiMatcherType.EXACT, ApiRiskLevel.MEDIUM),
                new ApiPermissionSeed("api:rag:retrieval:trace", "RAG 检索追踪详情", "ANY", "/api/rag/retrieval/traces/**", ApiMatcherType.ANT, ApiRiskLevel.MEDIUM),
                new ApiPermissionSeed("api:rag:feedback:create", "RAG 反馈提交", HttpMethod.POST.name(), "/api/rag/feedback", ApiMatcherType.EXACT, ApiRiskLevel.LOW),
                new ApiPermissionSeed("api:rag:settings:read", "RAG 设置查看", HttpMethod.GET.name(), "/api/rag/settings", ApiMatcherType.EXACT, ApiRiskLevel.LOW),
                new ApiPermissionSeed("api:rag:knowledge-base:collection", "RAG 知识库集合", "ANY", "/api/rag/knowledge-bases", ApiMatcherType.EXACT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:rag:knowledge-base:*", "RAG 知识库管理", "ANY", "/api/rag/knowledge-bases/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:rag:document:collection", "RAG 文档集合", "ANY", "/api/rag/documents", ApiMatcherType.EXACT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:rag:document:*", "RAG 文档管理", "ANY", "/api/rag/documents/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:rag:chunk:*", "RAG 切片管理", "ANY", "/api/rag/chunks/**", ApiMatcherType.ANT, ApiRiskLevel.HIGH),
                new ApiPermissionSeed("api:rag:ingestion-job:collection", "RAG 入库任务集合", "ANY", "/api/rag/ingestion-jobs", ApiMatcherType.EXACT, ApiRiskLevel.MEDIUM),
                new ApiPermissionSeed("api:rag:ingestion-job:*", "RAG 入库任务管理", "ANY", "/api/rag/ingestion-jobs/**", ApiMatcherType.ANT, ApiRiskLevel.MEDIUM)
        );
    }

    public record MenuPermissionSeed(
            String permCode,
            String permName,
            String parentPermCode,
            String routePath,
            String routeName,
            int sortNo
    ) {
    }

    public record ButtonPermissionSeed(
            String permCode,
            String permName,
            String menuPermCode,
            String buttonKey,
            String apiPermCode,
            int sortNo
    ) {
    }

    public record ApiPermissionSeed(
            String permCode,
            String permName,
            String httpMethod,
            String urlPattern,
            ApiMatcherType matcherType,
            ApiRiskLevel riskLevel
    ) {
    }

}
