package top.egon.mario.rbac.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.po.ButtonApiPo;
import top.egon.mario.rbac.po.ButtonPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.enums.ButtonApiRelationType;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.AuditLogRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RefreshTokenRepository;
import top.egon.mario.rbac.repository.RoleInheritanceRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacPermissionServiceTests {

    @Autowired
    private RbacPermissionService permissionService;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private ButtonRepository buttonRepository;
    @Autowired
    private ButtonApiRepository buttonApiRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private RoleInheritanceRepository roleInheritanceRepository;
    @Autowired
    private ApiRepository apiRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        userRoleRepository.deleteAll();
        roleInheritanceRepository.deleteAll();
        buttonApiRepository.deleteAll();
        apiRepository.deleteAll();
        buttonRepository.deleteAll();
        menuRepository.deleteAll();
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createPermissionRejectsPublicAdminApi() {
        PermissionRequest request = apiRequest("api:rbac:user:list", "/api/admin/users");
        request.getApi().setPublicFlag(true);

        assertThatThrownBy(() -> permissionService.createPermission(request, 1L))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("public API path is not allowed");
    }

    @Test
    void updatePermissionRejectsTypeChangeWhenReferencedByButtonApiMapping() {
        PermissionPo apiPermission = permissionRepository.save(permission("api:rbac:user:list", PermissionType.API));
        PermissionPo buttonPermission = permissionRepository.save(permission("btn:system:user:list", PermissionType.BUTTON));
        ButtonPo button = new ButtonPo();
        button.setPermissionId(buttonPermission.getId());
        button.setMenuPermissionId(buttonPermission.getId());
        button.setButtonKey("list");
        buttonRepository.save(button);
        ButtonApiPo link = new ButtonApiPo();
        link.setButtonPermissionId(buttonPermission.getId());
        link.setApiPermissionId(apiPermission.getId());
        link.setRelationType(ButtonApiRelationType.CALLS);
        link.setCreatedAt(Instant.now());
        buttonApiRepository.save(link);

        PermissionRequest request = menuRequest("menu:rbac:user");

        assertThatThrownBy(() -> permissionService.updatePermission(apiPermission.getId(), request, 1L))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("permission is still referenced");
    }

    @Test
    void getApiPermissionPageReturnsPagedApiPermissionsWithDraftAndDisabledRecords() {
        permissionService.createPermission(apiRequest("api:rbac:user:list", "/api/admin/users"), 1L);
        PermissionRequest disabledApi = apiRequest("api:rbac:user:delete", "/api/admin/users/*");
        disabledApi.setStatus(top.egon.mario.rbac.dto.enums.PermissionStatus.DISABLED);
        permissionService.createPermission(disabledApi, 1L);
        PermissionRequest draftApi = apiRequest("api:rbac:user:export", "/api/admin/users/export");
        draftApi.setStatus(top.egon.mario.rbac.dto.enums.PermissionStatus.DRAFT);
        permissionService.createPermission(draftApi, 1L);
        permissionRepository.save(permission("menu:rbac:user", PermissionType.MENU));

        assertThat(permissionService.getApiPermissionPage(PageRequest.of(0, 10)).getContent())
                .extracting(PermissionResponse::getPermCode)
                .containsExactlyInAnyOrder("api:rbac:user:list", "api:rbac:user:delete", "api:rbac:user:export");
    }

    private PermissionPo permission(String code, PermissionType type) {
        PermissionPo permission = new PermissionPo();
        permission.setPermCode(code);
        permission.setPermName(code);
        permission.setPermType(type);
        permission.setStatus(PermissionStatus.ENABLED);
        return permission;
    }

    private PermissionRequest apiRequest(String code, String urlPattern) {
        PermissionRequest request = new PermissionRequest();
        request.setPermCode(code);
        request.setPermName(code);
        request.setPermType(top.egon.mario.rbac.dto.enums.PermissionType.API);
        PermissionRequest.Api api = new PermissionRequest.Api();
        api.setHttpMethod("GET");
        api.setUrlPattern(urlPattern);
        request.setApi(api);
        return request;
    }

    private PermissionRequest menuRequest(String code) {
        PermissionRequest request = new PermissionRequest();
        request.setPermCode(code);
        request.setPermName(code);
        request.setPermType(top.egon.mario.rbac.dto.enums.PermissionType.MENU);
        PermissionRequest.Menu menu = new PermissionRequest.Menu();
        menu.setRoutePath("/rbac/users");
        request.setMenu(menu);
        return request;
    }

}
