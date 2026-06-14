package top.egon.mario.rag.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rag.dto.request.CreateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.request.ReplaceKnowledgeBaseUsersRequest;
import top.egon.mario.rag.dto.request.UpdateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.response.KnowledgeBaseResponse;
import top.egon.mario.rag.dto.response.KnowledgeBaseUserResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Application service for RAG knowledge base management.
 */
public interface RagKnowledgeBaseService {

    Page<KnowledgeBaseResponse> page(@NotNull Pageable pageable);

    KnowledgeBaseResponse create(@Valid @NotNull CreateKnowledgeBaseRequest request, RbacPrincipal principal);

    KnowledgeBaseResponse update(@NotNull Long id, @Valid @NotNull UpdateKnowledgeBaseRequest request, RbacPrincipal principal);

    void delete(@NotNull Long id, RbacPrincipal principal);

    List<KnowledgeBaseUserResponse> replaceUsers(@NotNull Long id, @Valid @NotNull ReplaceKnowledgeBaseUsersRequest request, RbacPrincipal principal);

    List<KnowledgeBaseUserResponse> users(@NotNull Long id, RbacPrincipal principal);

}
