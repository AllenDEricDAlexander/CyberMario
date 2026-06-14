package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.converter.RagDtoConverter;
import top.egon.mario.rag.dto.request.CreateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.request.ReplaceKnowledgeBaseUsersRequest;
import top.egon.mario.rag.dto.request.UpdateKnowledgeBaseRequest;
import top.egon.mario.rag.dto.response.KnowledgeBaseResponse;
import top.egon.mario.rag.dto.response.KnowledgeBaseUserResponse;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.service.RagKnowledgeBaseService;
import top.egon.mario.rag.service.bootstrap.SuperAdminArxivKnowledgeBaseBootstrap;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of RAG knowledge base management.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagKnowledgeBaseServiceImpl implements RagKnowledgeBaseService {

    private final RagProperties properties;
    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    private final RagAccessService accessService;
    private final RagDtoConverter dtoConverter;

    @Override
    @Transactional(readOnly = true)
    public Page<KnowledgeBaseResponse> page(Pageable pageable, RbacPrincipal principal) {
        return knowledgeBaseRepository.findAll((root, query, cb) -> {
                    var notDeleted = cb.isFalse(root.get("deleted"));
                    if (accessService.canBypassDataPermission(principal)) {
                        return notDeleted;
                    }
                    // The system arXiv collection is a protected super-admin
                    // knowledge base. Hide it from normal knowledge-base
                    // listings so papers collected from all users are not
                    // exposed through regular RAG management screens.
                    return cb.and(notDeleted, cb.notEqual(root.get("code"),
                            SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE));
                }, pageable)
                .map(dtoConverter::toKnowledgeBaseResponse);
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request, RbacPrincipal principal) {
        String code = request.code().trim();
        if (knowledgeBaseRepository.existsByCodeAndDeletedFalse(code)) {
            throw new RagException("RAG_KB_CODE_DUPLICATED", "knowledge base code already exists");
        }
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode(code);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setDefaultTopK(request.defaultTopK() == null ? properties.retrieval().defaultTopK() : request.defaultTopK());
        knowledgeBase.setDefaultSimilarityThreshold(request.defaultSimilarityThreshold() == null
                ? properties.retrieval().defaultSimilarityThreshold()
                : request.defaultSimilarityThreshold());
        knowledgeBase.setDefaultSearchMode(request.defaultSearchMode() == null
                ? properties.retrieval().defaultSearchMode()
                : request.defaultSearchMode());
        knowledgeBase.setRerankEnabled(request.rerankEnabled() == null
                ? properties.retrieval().rerankEnabled()
                : request.rerankEnabled());
        knowledgeBase.setVectorWeight(request.vectorWeight() == null ? properties.retrieval().vectorWeight() : request.vectorWeight());
        knowledgeBase.setKeywordWeight(request.keywordWeight() == null ? properties.retrieval().keywordWeight() : request.keywordWeight());
        knowledgeBase.setCandidateTopK(request.candidateTopK() == null ? properties.retrieval().candidateTopK() : request.candidateTopK());
        knowledgeBase.setContextTopK(request.contextTopK() == null ? properties.retrieval().contextTopK() : request.contextTopK());
        knowledgeBase.setChunkSize(request.chunkSize() == null ? properties.retrieval().chunkSize() : request.chunkSize());
        knowledgeBase.setChunkOverlap(request.chunkOverlap() == null ? properties.retrieval().chunkOverlap() : request.chunkOverlap());
        knowledgeBase.setStatus(RagKnowledgeBaseStatus.ENABLED);
        RagKnowledgeBasePo saved = knowledgeBaseRepository.save(knowledgeBase);
        grantCreatorManage(saved.getId(), principal);
        return dtoConverter.toKnowledgeBaseResponse(saved);
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse update(Long id, UpdateKnowledgeBaseRequest request, RbacPrincipal principal) {
        accessService.requireAccess(principal, id, RagAccessLevel.MANAGE);
        RagKnowledgeBasePo knowledgeBase = getKnowledgeBase(id);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setDefaultTopK(request.defaultTopK() == null ? knowledgeBase.getDefaultTopK() : request.defaultTopK());
        knowledgeBase.setDefaultSimilarityThreshold(request.defaultSimilarityThreshold() == null
                ? knowledgeBase.getDefaultSimilarityThreshold()
                : request.defaultSimilarityThreshold());
        knowledgeBase.setDefaultSearchMode(request.defaultSearchMode() == null
                ? knowledgeBase.getDefaultSearchMode()
                : request.defaultSearchMode());
        knowledgeBase.setRerankEnabled(request.rerankEnabled() == null
                ? knowledgeBase.isRerankEnabled()
                : request.rerankEnabled());
        knowledgeBase.setVectorWeight(request.vectorWeight() == null ? knowledgeBase.getVectorWeight() : request.vectorWeight());
        knowledgeBase.setKeywordWeight(request.keywordWeight() == null ? knowledgeBase.getKeywordWeight() : request.keywordWeight());
        knowledgeBase.setCandidateTopK(request.candidateTopK() == null ? knowledgeBase.getCandidateTopK() : request.candidateTopK());
        knowledgeBase.setContextTopK(request.contextTopK() == null ? knowledgeBase.getContextTopK() : request.contextTopK());
        knowledgeBase.setChunkSize(request.chunkSize() == null ? knowledgeBase.getChunkSize() : request.chunkSize());
        knowledgeBase.setChunkOverlap(request.chunkOverlap() == null ? knowledgeBase.getChunkOverlap() : request.chunkOverlap());
        knowledgeBase.setStatus(request.status() == null ? knowledgeBase.getStatus() : request.status());
        return dtoConverter.toKnowledgeBaseResponse(knowledgeBaseRepository.save(knowledgeBase));
    }

    @Override
    @Transactional
    public void delete(Long id, RbacPrincipal principal) {
        accessService.requireAccess(principal, id, RagAccessLevel.MANAGE);
        RagKnowledgeBasePo knowledgeBase = getKnowledgeBase(id);
        // The arXiv agent tool imports papers from every user into this global
        // super-admin knowledge base. Keep it protected so normal RAG cleanup
        // cannot accidentally remove the system collection.
        if (SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE.equals(knowledgeBase.getCode())) {
            throw new RagException("RAG_KB_PROTECTED", "protected arXiv knowledge base cannot be deleted");
        }
        knowledgeBase.setDeleted(true);
        knowledgeBaseRepository.save(knowledgeBase);
    }

    @Override
    @Transactional
    public List<KnowledgeBaseUserResponse> replaceUsers(Long id, ReplaceKnowledgeBaseUsersRequest request, RbacPrincipal principal) {
        accessService.requireAccess(principal, id, RagAccessLevel.MANAGE);
        getKnowledgeBase(id);
        Set<Long> requestedUserIds = request.users() == null ? Set.of() : request.users().stream()
                .map(ReplaceKnowledgeBaseUsersRequest.Grant::userId)
                .collect(Collectors.toSet());
        knowledgeBaseUserRepository.findByKnowledgeBaseIdAndDeletedFalse(id).forEach(existing -> {
            if (!requestedUserIds.contains(existing.getUserId())) {
                existing.setDeleted(true);
                knowledgeBaseUserRepository.save(existing);
            }
        });
        if (request.users() != null) {
            request.users().forEach(grant -> upsertGrant(id, grant.userId(), grant.accessLevel()));
        }
        return users(id, principal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeBaseUserResponse> users(Long id, RbacPrincipal principal) {
        accessService.requireAccess(principal, id, RagAccessLevel.MANAGE);
        return knowledgeBaseUserRepository.findByKnowledgeBaseIdAndDeletedFalse(id).stream()
                .map(dtoConverter::toKnowledgeBaseUserResponse)
                .toList();
    }

    private RagKnowledgeBasePo getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RagException("RAG_KB_NOT_FOUND", "knowledge base not found"));
    }

    private void grantCreatorManage(Long knowledgeBaseId, RbacPrincipal principal) {
        if (principal != null) {
            upsertGrant(knowledgeBaseId, principal.userId(), RagAccessLevel.MANAGE);
        }
    }

    private void upsertGrant(Long knowledgeBaseId, Long userId, RagAccessLevel accessLevel) {
        RagKnowledgeBaseUserPo grant = knowledgeBaseUserRepository
                .findByKnowledgeBaseIdAndUserIdAndDeletedFalse(knowledgeBaseId, userId)
                .orElseGet(RagKnowledgeBaseUserPo::new);
        grant.setKnowledgeBaseId(knowledgeBaseId);
        grant.setUserId(userId);
        grant.setAccessLevel(accessLevel == null ? RagAccessLevel.READ : accessLevel);
        grant.setDeleted(false);
        knowledgeBaseUserRepository.save(grant);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
