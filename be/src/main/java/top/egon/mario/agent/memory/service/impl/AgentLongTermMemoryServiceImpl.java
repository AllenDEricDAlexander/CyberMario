package top.egon.mario.agent.memory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryStatus;
import top.egon.mario.agent.memory.repository.AgentLongTermMemoryRepository;
import top.egon.mario.agent.memory.repository.AgentLongTermMemoryVersionRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryDefaults;
import top.egon.mario.agent.memory.service.AgentMemoryException;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Default service for one Markdown long-term memory document per user scope.
 */
@Service
public class AgentLongTermMemoryServiceImpl implements AgentLongTermMemoryService {

    private final AgentLongTermMemoryRepository memoryRepository;
    private final AgentLongTermMemoryVersionRepository versionRepository;

    public AgentLongTermMemoryServiceImpl(AgentLongTermMemoryRepository memoryRepository,
                                          AgentLongTermMemoryVersionRepository versionRepository) {
        this.memoryRepository = memoryRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional
    public AgentLongTermMemoryPo getOrCreateUserAgentMemory(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
        }
        return getOrCreate(principal.userId(), principal.username(), AgentLongTermMemoryScopeType.USER_AGENT);
    }

    @Override
    @Transactional
    public AgentLongTermMemoryPo getOrCreate(Long userId, String username, AgentLongTermMemoryScopeType scopeType) {
        if (userId == null) {
            throw new AgentMemoryException("AGENT_MEMORY_USER_REQUIRED", "memory user is required");
        }
        AgentLongTermMemoryScopeType safeScopeType = scopeType == null
                ? AgentLongTermMemoryScopeType.USER_AGENT
                : scopeType;
        return memoryRepository.findByUserIdAndScopeTypeAndDeletedFalse(userId, safeScopeType)
                .orElseGet(() -> createDefault(userId, username, safeScopeType));
    }

    @Override
    @Transactional
    public AgentLongTermMemoryPo merge(AgentLongTermMemoryMergeRequest request) {
        if (request == null || request.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_USER_REQUIRED", "memory user is required");
        }
        String merged = request.mergedMarkdown() == null ? "" : request.mergedMarkdown().trim();
        if (merged.length() > AgentMemoryDefaults.LONG_TERM_MARKDOWN_MAX_CHARS) {
            throw new AgentMemoryException("AGENT_LONG_TERM_MEMORY_TOO_LARGE",
                    "long-term memory exceeds 20000 characters");
        }
        AgentLongTermMemoryPo memory = getOrCreate(request.userId(), request.username(), request.scopeType());
        memory.setContentMarkdown(merged);
        memory.setContentChars(merged.length());
        memory.setUpdatedAt(Instant.now());
        AgentLongTermMemoryPo saved = memoryRepository.save(memory);
        AgentLongTermMemoryVersionPo version = saveVersion(saved, merged, request.changeSummary(),
                request.sourceSessionIds(), request.sourceMessageIds(), request.requestId(), request.traceId());
        saved.setActiveVersionId(version.getId());
        return memoryRepository.save(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentLongTermMemoryVersionPo> userAgentVersions(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
        }
        return versions(principal.userId(), AgentLongTermMemoryScopeType.USER_AGENT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentLongTermMemoryVersionPo> versions(Long userId, AgentLongTermMemoryScopeType scopeType) {
        AgentLongTermMemoryScopeType safeScopeType = scopeType == null
                ? AgentLongTermMemoryScopeType.USER_AGENT
                : scopeType;
        return memoryRepository.findByUserIdAndScopeTypeAndDeletedFalse(userId, safeScopeType)
                .map(memory -> versionRepository.findByMemoryIdOrderByVersionNoDesc(memory.getId()))
                .orElseGet(List::of);
    }

    private AgentLongTermMemoryPo createDefault(Long userId, String username, AgentLongTermMemoryScopeType scopeType) {
        Instant now = Instant.now();
        String markdown = AgentMemoryDefaults.DEFAULT_USER_MEMORY_MARKDOWN.trim();
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setUserId(userId);
        memory.setUsername(username);
        memory.setScopeType(scopeType);
        memory.setContentMarkdown(markdown);
        memory.setContentChars(markdown.length());
        memory.setStatus(AgentLongTermMemoryStatus.ACTIVE);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        AgentLongTermMemoryPo saved = memoryRepository.save(memory);
        AgentLongTermMemoryVersionPo version = saveVersion(saved, markdown, "create default memory",
                null, null, null, null);
        saved.setActiveVersionId(version.getId());
        return memoryRepository.save(saved);
    }

    private AgentLongTermMemoryVersionPo saveVersion(AgentLongTermMemoryPo memory, String markdown, String changeSummary,
                                                     String sourceSessionIds, String sourceMessageIds,
                                                     String requestId, String traceId) {
        int versionNo = versionRepository.findByMemoryIdOrderByVersionNoDesc(memory.getId()).stream()
                .mapToInt(AgentLongTermMemoryVersionPo::getVersionNo)
                .max()
                .orElse(0) + 1;
        AgentLongTermMemoryVersionPo version = new AgentLongTermMemoryVersionPo();
        version.setMemoryId(memory.getId());
        version.setVersionNo(versionNo);
        version.setContentMarkdown(markdown);
        version.setContentChars(markdown.length());
        version.setChangeSummary(changeSummary);
        version.setSourceSessionIds(sourceSessionIds);
        version.setSourceMessageIds(sourceMessageIds);
        version.setRequestId(requestId);
        version.setTraceId(traceId);
        version.setCreatedAt(Instant.now());
        return versionRepository.save(version);
    }
}
