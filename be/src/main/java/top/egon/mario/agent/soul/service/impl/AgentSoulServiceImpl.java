package top.egon.mario.agent.soul.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.soul.dto.request.AgentSoulMdUpdateRequest;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdResponse;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdVersionResponse;
import top.egon.mario.agent.soul.po.AgentSoulMdVersionPo;
import top.egon.mario.agent.soul.po.enums.AgentSoulChangeType;
import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;
import top.egon.mario.agent.soul.repository.AgentSoulMdVersionRepository;
import top.egon.mario.agent.soul.service.AgentSoulDefaults;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Manages current-user Agent SoulMD document and immutable previous snapshots.
 */
@Service
public class AgentSoulServiceImpl implements AgentSoulService {

    private final UserRepository userRepository;
    private final AgentSoulMdVersionRepository versionRepository;

    public AgentSoulServiceImpl(UserRepository userRepository, AgentSoulMdVersionRepository versionRepository) {
        this.userRepository = userRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentSoulMdResponse currentSoul(RbacPrincipal principal) {
        UserPo user = requireUser(principal);
        return response(user, currentMarkdown(user));
    }

    @Override
    @Transactional
    public AgentSoulMdResponse updateManual(AgentSoulMdUpdateRequest request, RbacPrincipal principal) {
        if (request == null) {
            throw new AgentException("AGENT_SOUL_REQUEST_REQUIRED", "SoulMD update request is required");
        }
        UserPo user = requireUser(principal);
        String current = currentMarkdown(user);
        String next = normalizeMarkdown(request.contentMarkdown());
        boolean enabled = request.enabled();
        if (next.equals(current) && enabled == user.isSoulMdEnabled()) {
            return response(user, current);
        }
        archiveCurrent(user, AgentSoulChangeType.MANUAL_EDIT, "Manual SoulMD edit",
                null, null, null, null, null, null, null);
        Instant now = Instant.now();
        user.setSoulMd(next);
        user.setSoulMdEnabled(enabled);
        user.setSoulMdChars(next.length());
        user.setSoulMdVersionNo(Math.max(user.getSoulMdVersionNo(), 1) + 1);
        user.setSoulMdUpdatedAt(now);
        UserPo saved = userRepository.save(user);
        return response(saved, next);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentSoulMdVersionResponse> versions(RbacPrincipal principal) {
        UserPo user = requireUser(principal);
        return versionRepository.findByUserIdOrderByVersionNoDesc(user.getId()).stream()
                .map(AgentSoulMdVersionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String userSoulPromptForChat(RbacPrincipal principal) {
        UserPo user = requireUser(principal);
        if (!user.isSoulMdEnabled()) {
            return "";
        }
        return AgentSoulDefaults.userSoulPrompt(currentMarkdown(user));
    }

    protected AgentSoulMdVersionPo archiveCurrent(UserPo user, AgentSoulChangeType changeType, String changeSummary,
                                                  AgentSoulSourceType sourceType, String sourceSessionId,
                                                  String sourceMessageIds, String modelProvider, String modelName,
                                                  String requestId, String traceId) {
        String current = currentMarkdown(user);
        AgentSoulMdVersionPo version = new AgentSoulMdVersionPo();
        version.setUserId(user.getId());
        version.setUsername(user.getUsername());
        version.setVersionNo(Math.max(user.getSoulMdVersionNo(), 1));
        version.setContentMarkdown(current);
        version.setContentChars(current.length());
        version.setChangeType(changeType);
        version.setChangeSummary(changeSummary);
        version.setSourceType(sourceType);
        version.setSourceSessionId(sourceSessionId);
        version.setSourceMessageIds(sourceMessageIds);
        version.setModelProvider(modelProvider);
        version.setModelName(modelName);
        version.setRequestId(requestId);
        version.setTraceId(traceId);
        version.setCreatedAt(Instant.now());
        return versionRepository.save(version);
    }

    private AgentSoulMdResponse response(UserPo user, String markdown) {
        return new AgentSoulMdResponse(
                markdown,
                user.isSoulMdEnabled(),
                markdown.length(),
                AgentSoulDefaults.MAX_SOUL_MD_CHARS,
                Math.max(user.getSoulMdVersionNo(), 1),
                user.getSoulMdUpdatedAt()
        );
    }

    private String currentMarkdown(UserPo user) {
        return StringUtils.hasText(user.getSoulMd()) ? user.getSoulMd().trim() : AgentSoulDefaults.DEFAULT_SOUL_MD;
    }

    private String normalizeMarkdown(String markdown) {
        String normalized = StringUtils.hasText(markdown) ? markdown.trim() : AgentSoulDefaults.DEFAULT_SOUL_MD;
        if (normalized.length() > AgentSoulDefaults.MAX_SOUL_MD_CHARS) {
            throw new AgentException("AGENT_SOUL_TOO_LARGE", "SoulMD must be at most 50000 characters");
        }
        return normalized;
    }

    private UserPo requireUser(RbacPrincipal principal) {
        RbacPrincipal safePrincipal = requirePrincipal(principal);
        return userRepository.findByIdAndDeletedFalse(safePrincipal.userId())
                .orElseThrow(() -> new AgentException("AGENT_SOUL_USER_NOT_FOUND", "SoulMD user not found"));
    }

    private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentException("AGENT_SOUL_UNAUTHENTICATED", "SoulMD requires an authenticated user");
        }
        return principal;
    }
}
