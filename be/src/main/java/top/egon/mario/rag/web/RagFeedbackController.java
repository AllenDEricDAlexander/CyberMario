package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rag.dto.request.RagFeedbackRequest;
import top.egon.mario.rag.dto.response.RagFeedbackResponse;
import top.egon.mario.rag.service.RagFeedbackService;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Feedback endpoint for RAG answers and citations.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/feedback")
@Validated
public class RagFeedbackController extends ReactiveRagSupport {

    private final RagFeedbackService feedbackService;

    @RbacApi(appCode = "rag", code = "api:rag:feedback:create", name = "RAG 反馈提交", risk = ApiRiskLevel.LOW)
    @PostMapping
    public Mono<ApiResponse<RagFeedbackResponse>> create(@Valid @RequestBody RagFeedbackRequest request,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> feedbackService.create(request, principal));
    }

}
