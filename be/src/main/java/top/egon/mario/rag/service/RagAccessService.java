package top.egon.mario.rag.service;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Collection;
import java.util.Set;

/**
 * Applies user-scoped RAG knowledge base authorization.
 */
public interface RagAccessService {

    /**
     * Returns enabled knowledge base ids readable by the current user.
     */
    Set<Long> readableKnowledgeBaseIds(RbacPrincipal principal, Collection<Long> requestedKnowledgeBaseIds);

    /**
     * Ensures the current user has the requested access level on a knowledge base.
     */
    void requireAccess(RbacPrincipal principal, @NotNull Long knowledgeBaseId, @NotNull RagAccessLevel requiredLevel);

    /**
     * Returns whether the principal can bypass knowledge base data permissions.
     */
    boolean canBypassDataPermission(RbacPrincipal principal);

}
