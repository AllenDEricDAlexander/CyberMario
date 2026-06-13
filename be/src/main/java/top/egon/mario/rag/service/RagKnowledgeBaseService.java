package top.egon.mario.rag.service;

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

    Page<KnowledgeBaseResponse> page(Pageable pageable);

    KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request, RbacPrincipal principal);

    KnowledgeBaseResponse update(Long id, UpdateKnowledgeBaseRequest request, RbacPrincipal principal);

    void delete(Long id, RbacPrincipal principal);

    List<KnowledgeBaseUserResponse> replaceUsers(Long id, ReplaceKnowledgeBaseUsersRequest request, RbacPrincipal principal);

    List<KnowledgeBaseUserResponse> users(Long id, RbacPrincipal principal);

}
