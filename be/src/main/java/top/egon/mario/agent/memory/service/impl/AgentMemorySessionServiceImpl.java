package top.egon.mario.agent.memory.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.AgentMemoryDefaults;
import top.egon.mario.agent.memory.service.AgentMemoryException;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionCreate;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionUpdate;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of user-owned memory session lifecycle rules.
 */
@Service
public class AgentMemorySessionServiceImpl implements AgentMemorySessionService {

    private final AgentMemorySessionRepository repository;

    public AgentMemorySessionServiceImpl(AgentMemorySessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public AgentMemorySessionPo create(AgentMemorySessionCreate request, RbacPrincipal principal) {
        RbacPrincipal safePrincipal = requirePrincipal(principal);
        if (request == null || request.entryType() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_ENTRY_TYPE_REQUIRED", "memory entry type is required");
        }
        Instant now = Instant.now();
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(UUID.randomUUID().toString());
        session.setEntryType(request.entryType());
        session.setTitle(trimToNull(request.title()));
        session.setUserId(safePrincipal.userId());
        session.setUsername(safePrincipal.username());
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setMemoryEnabled(request.memoryEnabled() == null || request.memoryEnabled());
        session.setLongTermExtractionEnabled(request.longTermExtractionEnabled() == null
                || request.longTermExtractionEnabled());
        session.setShortTermWindowTurns(AgentMemoryDefaults.SHORT_TERM_WINDOW_TURNS);
        session.setLastActiveAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return repository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentMemorySessionPo> page(AgentMemoryEntryType entryType, AgentMemorySessionStatus status,
                                           Pageable pageable, RbacPrincipal principal) {
        RbacPrincipal safePrincipal = requirePrincipal(principal);
        List<AgentMemorySessionStatus> statuses = status == null
                ? List.of(AgentMemorySessionStatus.ACTIVE, AgentMemorySessionStatus.RELEASED)
                : List.of(status);
        if (entryType == null) {
            return repository.findByUserIdAndStatusInAndDeletedFalse(safePrincipal.userId(), statuses, pageable);
        }
        return repository.findByUserIdAndEntryTypeAndStatusInAndDeletedFalse(
                safePrincipal.userId(), entryType, statuses, pageable);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo resolveOrCreate(AgentMemoryEntryType entryType, String sessionId,
                                                Boolean memoryEnabled, Boolean longTermExtractionEnabled,
                                                RbacPrincipal principal) {
        if (StringUtils.hasText(sessionId)) {
            AgentMemorySessionPo session = requireUsableForChat(sessionId, principal);
            applyRuntimeSwitches(session, memoryEnabled, longTermExtractionEnabled);
            return repository.save(session);
        }
        return create(new AgentMemorySessionCreate(entryType, null, memoryEnabled, longTermExtractionEnabled), principal);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentMemorySessionPo requireOwned(String sessionId, RbacPrincipal principal) {
        return findOwned(sessionId, principal);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo requireUsableForChat(String sessionId, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (session.getStatus() == AgentMemorySessionStatus.ARCHIVED) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_ARCHIVED", "memory session is archived");
        }
        if (session.getStatus() == AgentMemorySessionStatus.DELETED || session.isDeleted()) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_DELETED", "memory session is deleted");
        }
        Instant now = Instant.now();
        if (session.getStatus() == AgentMemorySessionStatus.RELEASED) {
            session.setStatus(AgentMemorySessionStatus.ACTIVE);
        }
        session.setLastActiveAt(now);
        session.setUpdatedAt(now);
        return repository.save(session);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo update(String sessionId, AgentMemorySessionUpdate request, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (request != null) {
            if (request.title() != null) {
                session.setTitle(trimToNull(request.title()));
            }
            applyRuntimeSwitches(session, request.memoryEnabled(), request.longTermExtractionEnabled());
        }
        session.setUpdatedAt(Instant.now());
        return repository.save(session);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo release(String sessionId, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (session.getStatus() == AgentMemorySessionStatus.ARCHIVED) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_ARCHIVED", "memory session is archived");
        }
        if (session.getStatus() == AgentMemorySessionStatus.DELETED || session.isDeleted()) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_DELETED", "memory session is deleted");
        }
        Instant now = Instant.now();
        session.setStatus(AgentMemorySessionStatus.RELEASED);
        session.setReleasedAt(now);
        session.setUpdatedAt(now);
        return repository.save(session);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo restore(String sessionId, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (session.getStatus() == AgentMemorySessionStatus.DELETED || session.isDeleted()) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_DELETED", "memory session is deleted");
        }
        Instant now = Instant.now();
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setLastActiveAt(now);
        session.setUpdatedAt(now);
        return repository.save(session);
    }

    @Override
    @Transactional
    public AgentMemorySessionPo archive(String sessionId, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (session.getStatus() == AgentMemorySessionStatus.DELETED || session.isDeleted()) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_DELETED", "memory session is deleted");
        }
        Instant now = Instant.now();
        session.setStatus(AgentMemorySessionStatus.ARCHIVED);
        session.setArchivedAt(now);
        session.setUpdatedAt(now);
        return repository.save(session);
    }

    @Override
    @Transactional
    public void deleteArchived(String sessionId, RbacPrincipal principal) {
        AgentMemorySessionPo session = findOwned(sessionId, principal);
        if (session.getStatus() != AgentMemorySessionStatus.ARCHIVED) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_DELETE_REQUIRES_ARCHIVE",
                    "memory session delete requires archive");
        }
        Instant now = Instant.now();
        session.setStatus(AgentMemorySessionStatus.DELETED);
        session.setDeleted(true);
        session.setDeletedAt(now);
        session.setUpdatedAt(now);
        repository.save(session);
    }

    private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
        }
        return principal;
    }

    private AgentMemorySessionPo findOwned(String sessionId, RbacPrincipal principal) {
        RbacPrincipal safePrincipal = requirePrincipal(principal);
        if (!StringUtils.hasText(sessionId)) {
            throw new AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND", "memory session not found");
        }
        return repository.findBySessionIdAndUserIdAndDeletedFalse(sessionId, safePrincipal.userId())
                .orElseThrow(() -> new AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND",
                        "memory session not found"));
    }

    private void applyRuntimeSwitches(AgentMemorySessionPo session, Boolean memoryEnabled,
                                      Boolean longTermExtractionEnabled) {
        if (memoryEnabled != null) {
            session.setMemoryEnabled(memoryEnabled);
        }
        if (longTermExtractionEnabled != null) {
            session.setLongTermExtractionEnabled(longTermExtractionEnabled);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
