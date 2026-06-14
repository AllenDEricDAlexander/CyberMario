package top.egon.mario.rag.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.request.RagFeedbackRequest;
import top.egon.mario.rag.dto.response.RagFeedbackResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Stores user feedback for RAG answers and sources.
 */
public interface RagFeedbackService {

    RagFeedbackResponse create(@Valid @NotNull RagFeedbackRequest request, RbacPrincipal principal);

}
