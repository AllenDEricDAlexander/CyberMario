package top.egon.mario.rag.service.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;

import java.util.List;

/**
 * Prepares the protected knowledge base used by the arXiv agent tool.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
@RequiredArgsConstructor
@Slf4j
public class SuperAdminArxivKnowledgeBaseBootstrap implements ApplicationRunner {

    public static final String SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE = "super-admin-arxiv";

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String KNOWLEDGE_BASE_NAME = "Super Admin arXiv";
    private static final String KNOWLEDGE_BASE_DESCRIPTION = "arXiv papers collected by CyberMario agent tools";

    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    @Transactional
    public void bootstrap() {
        RagKnowledgeBasePo knowledgeBase = knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .orElseGet(this::createKnowledgeBase);
        grantSuperAdmins(knowledgeBase.getId());
    }

    private RagKnowledgeBasePo createKnowledgeBase() {
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode(SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE);
        knowledgeBase.setName(KNOWLEDGE_BASE_NAME);
        knowledgeBase.setDescription(KNOWLEDGE_BASE_DESCRIPTION);
        knowledgeBase.setStatus(RagKnowledgeBaseStatus.ENABLED);
        RagKnowledgeBasePo saved = knowledgeBaseRepository.save(knowledgeBase);
        LogUtil.info(log).log("super admin arxiv knowledge base bootstrapped, knowledgeBaseId={}", saved.getId());
        return saved;
    }

    private void grantSuperAdmins(Long knowledgeBaseId) {
        RolePo superAdminRole = roleRepository.findByRoleCodeAndDeletedFalse(SUPER_ADMIN_ROLE_CODE).orElse(null);
        if (superAdminRole == null) {
            LogUtil.debug(log).log("super admin arxiv knowledge base grant skipped, reason=role_missing");
            return;
        }
        List<UserRolePo> userRoles = userRoleRepository.findByRoleId(superAdminRole.getId());
        userRoles.forEach(userRole -> upsertManageGrant(knowledgeBaseId, userRole.getUserId()));
    }

    private void upsertManageGrant(Long knowledgeBaseId, Long userId) {
        RagKnowledgeBaseUserPo grant = knowledgeBaseUserRepository
                .findByKnowledgeBaseIdAndUserIdAndDeletedFalse(knowledgeBaseId, userId)
                .orElseGet(RagKnowledgeBaseUserPo::new);
        grant.setKnowledgeBaseId(knowledgeBaseId);
        grant.setUserId(userId);
        grant.setAccessLevel(RagAccessLevel.MANAGE);
        grant.setDeleted(false);
        knowledgeBaseUserRepository.save(grant);
    }

}
