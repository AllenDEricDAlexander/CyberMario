package top.egon.mario.agent.tools.arxiv;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.tools.arxiv.dto.ArxivToolLogResponse;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Query service for super-admin arXiv tool logs.
 */
@Service
@RequiredArgsConstructor
public class ArxivToolLogQueryService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final ArxivToolLogRepository repository;

    @Transactional(readOnly = true)
    public Page<ArxivToolLogResponse> page(Pageable pageable, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        return repository.findAll(pageable).map(this::toResponse);
    }

    private void requireSuperAdmin(RbacPrincipal principal) {
        if (principal == null || !principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            throw new RagException("ARXIV_LOG_FORBIDDEN", "arXiv tool logs are only available to super administrators");
        }
    }

    private ArxivToolLogResponse toResponse(ArxivToolLogPo po) {
        return new ArxivToolLogResponse(
                po.getId(),
                po.getRequestId(),
                po.getRequestUserId(),
                po.getRequestUsername(),
                po.getQuery(),
                po.getMaxResults(),
                po.isIncludeFullText(),
                po.getResultCount(),
                po.getKnowledgeBaseId(),
                po.getEntryId(),
                po.getTitle(),
                po.getPdfUrl(),
                po.getStatus(),
                po.getDocumentId(),
                po.getRagIngestionJobId(),
                po.getErrorMessage(),
                po.getStartedAt(),
                po.getFinishedAt(),
                po.getCreatedAt()
        );
    }

}
