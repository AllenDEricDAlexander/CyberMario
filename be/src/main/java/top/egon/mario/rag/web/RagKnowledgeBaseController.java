package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rag.dto.request.CreateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.request.ReplaceKnowledgeBaseUsersRequest;
import top.egon.mario.rag.dto.request.UpdateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.response.KnowledgeBaseResponse;
import top.egon.mario.rag.dto.response.KnowledgeBaseUserResponse;
import top.egon.mario.rag.service.RagKnowledgeBaseService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Management endpoints for RAG knowledge bases.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/knowledge-bases")
public class RagKnowledgeBaseController extends ReactiveRagSupport {

    private final RagKnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public Mono<ApiResponse<PageResult<KnowledgeBaseResponse>>> page(@RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(knowledgeBaseService.page(PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()))));
    }

    @PostMapping
    public Mono<ApiResponse<KnowledgeBaseResponse>> create(@Valid @RequestBody CreateKnowledgeBaseRequest request,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> knowledgeBaseService.create(request, principal));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<KnowledgeBaseResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateKnowledgeBaseRequest request,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> knowledgeBaseService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> knowledgeBaseService.delete(id, principal));
    }

    @GetMapping("/{id}/users")
    public Mono<ApiResponse<List<KnowledgeBaseUserResponse>>> users(@PathVariable Long id,
                                                                    @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> knowledgeBaseService.users(id, principal));
    }

    @PutMapping("/{id}/users")
    public Mono<ApiResponse<List<KnowledgeBaseUserResponse>>> replaceUsers(@PathVariable Long id,
                                                                           @Valid @RequestBody ReplaceKnowledgeBaseUsersRequest request,
                                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> knowledgeBaseService.replaceUsers(id, request, principal));
    }

}
