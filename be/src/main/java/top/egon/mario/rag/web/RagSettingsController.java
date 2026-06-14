package top.egon.mario.rag.web;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rag.dto.response.RagSettingsResponse;
import top.egon.mario.rag.service.RagSettingsService;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;

/**
 * Read-only RAG settings endpoint.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/settings")
@Validated
public class RagSettingsController extends ReactiveRagSupport {

    private final RagSettingsService settingsService;

    @RbacApi(appCode = "rag", code = "api:rag:settings:read", name = "RAG 设置查看", risk = ApiRiskLevel.LOW)
    @GetMapping
    public Mono<ApiResponse<RagSettingsResponse>> settings() {
        return blocking(settingsService::settings);
    }

}
