package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default user-level data permission service for RAG knowledge bases.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagAccessServiceImpl implements RagAccessService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> readableKnowledgeBaseIds(RbacPrincipal principal, Collection<Long> requestedKnowledgeBaseIds) {
        Set<Long> requestedIds = requestedKnowledgeBaseIds == null ? Set.of() : new HashSet<>(requestedKnowledgeBaseIds);
        if (canBypassDataPermission(principal)) {
            if (requestedIds.isEmpty()) {
                return knowledgeBaseRepository.findByDeletedFalseAndStatus(RagKnowledgeBaseStatus.ENABLED).stream()
                        .map(RagKnowledgeBasePo::getId)
                        .collect(Collectors.toSet());
            }
            return knowledgeBaseRepository.findByIdInAndDeletedFalseAndStatus(requestedIds, RagKnowledgeBaseStatus.ENABLED).stream()
                    .map(RagKnowledgeBasePo::getId)
                    .collect(Collectors.toSet());
        }
        if (principal == null) {
            return Set.of();
        }
        return knowledgeBaseUserRepository.findByUserIdAndDeletedFalse(principal.userId()).stream()
                .filter(grant -> grant.getAccessLevel().allows(RagAccessLevel.READ))
                .filter(grant -> requestedIds.isEmpty() || requestedIds.contains(grant.getKnowledgeBaseId()))
                .map(RagKnowledgeBaseUserPo::getKnowledgeBaseId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAccess(RbacPrincipal principal, Long knowledgeBaseId, RagAccessLevel requiredLevel) {
        if (canBypassDataPermission(principal)) {
            return;
        }
        if (principal == null) {
            throw new RagException("RAG_FORBIDDEN", "RAG knowledge base access is required");
        }
        RagKnowledgeBaseUserPo grant = knowledgeBaseUserRepository
                .findByKnowledgeBaseIdAndUserIdAndDeletedFalse(knowledgeBaseId, principal.userId())
                .orElseThrow(() -> new RagException("RAG_FORBIDDEN", "RAG knowledge base access is required"));
        if (!grant.getAccessLevel().allows(requiredLevel)) {
            throw new RagException("RAG_FORBIDDEN", "RAG knowledge base access is insufficient");
        }
    }

    @Override
    public boolean canBypassDataPermission(RbacPrincipal principal) {
        return principal != null && principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE);
    }

}
