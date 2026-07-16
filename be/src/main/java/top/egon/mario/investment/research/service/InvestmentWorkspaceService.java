package top.egon.mario.investment.research.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.research.po.InvestmentWorkspacePo;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWorkspaceRequest;
import top.egon.mario.investment.research.web.dto.InvestmentWorkspaceResponse;

/**
 * Manages private Investment workspaces within their owner boundary.
 */
@Service
@RequiredArgsConstructor
public class InvestmentWorkspaceService {

    private static final String ACTIVE = "ACTIVE";

    private final InvestmentWorkspaceRepository workspaceRepository;

    /**
     * Lists only active workspaces owned by the actor.
     */
    @Transactional(readOnly = true)
    public Page<InvestmentWorkspaceResponse> list(Long actorId, Pageable pageable) {
        requireActor(actorId);
        return workspaceRepository.findOwnedActiveWorkspaces(actorId, pageable)
                .map(InvestmentWorkspaceService::toResponse);
    }

    /**
     * Creates a private workspace or restores its soft-deleted business key.
     */
    @Transactional
    public InvestmentWorkspaceResponse create(Long actorId, CreateInvestmentWorkspaceRequest request) {
        requireActor(actorId);
        String name = normalizedName(request == null ? null : request.name());
        InvestmentWorkspacePo workspace = workspaceRepository.findOwnedByBusinessKey(actorId, name)
                .map(existing -> restore(existing, name))
                .orElseGet(() -> newWorkspace(actorId, name));
        try {
            return toResponse(workspaceRepository.saveAndFlush(workspace));
        } catch (DataIntegrityViolationException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Workspace name already exists for this owner", exception);
        }
    }

    private static InvestmentWorkspacePo restore(InvestmentWorkspacePo workspace, String name) {
        if (!workspace.isDeleted()) {
            throw conflict("Workspace name already exists for this owner");
        }
        workspace.setName(name);
        workspace.setStatus(ACTIVE);
        workspace.setDeleted(false);
        return workspace;
    }

    private static InvestmentWorkspacePo newWorkspace(Long ownerUserId, String name) {
        InvestmentWorkspacePo workspace = new InvestmentWorkspacePo();
        workspace.setOwnerUserId(ownerUserId);
        workspace.setName(name);
        workspace.setBaseCurrency("USDT");
        workspace.setTimezone("UTC");
        workspace.setStatus(ACTIVE);
        workspace.setSettingsJson("{}");
        return workspace;
    }

    private static InvestmentWorkspaceResponse toResponse(InvestmentWorkspacePo workspace) {
        return new InvestmentWorkspaceResponse(
                workspace.getId(), workspace.getName(), workspace.getBaseCurrency(), workspace.getTimezone(),
                workspace.getStatus(), workspace.getCreatedAt());
    }

    private static String normalizedName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, "Workspace name is required");
        }
        return name.trim();
    }

    private static void requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new InvestmentException(InvestmentErrorCode.FORBIDDEN, "Authenticated Investment actor required");
        }
    }

    private static InvestmentException conflict(String message) {
        return new InvestmentException(InvestmentErrorCode.CONFLICT, message);
    }
}
