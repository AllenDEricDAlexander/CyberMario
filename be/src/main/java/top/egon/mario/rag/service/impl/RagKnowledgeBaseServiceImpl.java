package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of RAG knowledge base management.
 */
@Service
@RequiredArgsConstructor
public class RagKnowledgeBaseServiceImpl implements RagKnowledgeBaseService {

    private final RagProperties properties;
    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    private final RagAccessService accessService;
    private final RagDtoConverter dtoConverter;

    @Override
    @Transactional(readOnly = true)
    public Page<KnowledgeBaseResponse> page(Pageable pageable) {
        return knowledgeBaseRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
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
        knowledgeBase.setStatus(request.status() == null ? knowledgeBase.getStatus() : request.status());
        return dtoConverter.toKnowledgeBaseResponse(knowledgeBaseRepository.save(knowledgeBase));
    }

    @Override
    @Transactional
    public void delete(Long id, RbacPrincipal principal) {
        accessService.requireAccess(principal, id, RagAccessLevel.MANAGE);
        RagKnowledgeBasePo knowledgeBase = getKnowledgeBase(id);
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
