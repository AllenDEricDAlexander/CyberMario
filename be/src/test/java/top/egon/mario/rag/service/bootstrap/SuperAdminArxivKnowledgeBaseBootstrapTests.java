package top.egon.mario.rag.service.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the protected arXiv knowledge base is provisioned for super administrators.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class SuperAdminArxivKnowledgeBaseBootstrapTests {

    @Autowired
    private SuperAdminArxivKnowledgeBaseBootstrap bootstrap;
    @Autowired
    private RagKnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @BeforeEach
    void setUp() {
        knowledgeBaseUserRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void bootstrapCreatesProtectedKnowledgeBaseAndGrantsSuperAdminManageAccess() {
        UserPo admin = createSuperAdmin();

        bootstrap.bootstrap();

        RagKnowledgeBasePo knowledgeBase = knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .orElseThrow();
        assertThat(knowledgeBase.getName()).isEqualTo("Super Admin arXiv");
        assertThat(knowledgeBase.getDescription()).contains("arXiv papers");
        RagKnowledgeBaseUserPo grant = knowledgeBaseUserRepository
                .findByKnowledgeBaseIdAndUserIdAndDeletedFalse(knowledgeBase.getId(), admin.getId())
                .orElseThrow();
        assertThat(grant.getAccessLevel()).isEqualTo(RagAccessLevel.MANAGE);
    }

    @Test
    void bootstrapIsIdempotentWhenKnowledgeBaseAlreadyExists() {
        UserPo admin = createSuperAdmin();
        bootstrap.bootstrap();
        RagKnowledgeBasePo first = knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .orElseThrow();

        bootstrap.bootstrap();

        assertThat(knowledgeBaseRepository.findAll())
                .filteredOn(knowledgeBase -> SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE
                        .equals(knowledgeBase.getCode()))
                .hasSize(1);
        assertThat(knowledgeBaseUserRepository.findByKnowledgeBaseIdAndUserIdAndDeletedFalse(first.getId(), admin.getId()))
                .isPresent();
    }

    @Test
    void bootstrapRebuildsKnowledgeBaseAfterDirectDatabaseDeletion() {
        createSuperAdmin();
        bootstrap.bootstrap();
        RagKnowledgeBasePo first = knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .orElseThrow();
        knowledgeBaseUserRepository.deleteAll();
        knowledgeBaseRepository.delete(first);

        bootstrap.bootstrap();

        RagKnowledgeBasePo rebuilt = knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .orElseThrow();
        assertThat(rebuilt.getId()).isNotEqualTo(first.getId());
    }

    private UserPo createSuperAdmin() {
        RolePo role = new RolePo();
        role.setRoleCode("SUPER_ADMIN");
        role.setRoleName("Super Administrator");
        role.setStatus(RbacStatus.ENABLED);
        role.setBuiltIn(true);
        role = roleRepository.save(role);

        UserPo user = new UserPo();
        user.setUsername("admin");
        user.setNickname("Administrator");
        user.setPasswordHash("password");
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);

        UserRolePo userRole = new UserRolePo();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setGrantedAt(Instant.now());
        userRoleRepository.save(userRole);
        return user;
    }

}
