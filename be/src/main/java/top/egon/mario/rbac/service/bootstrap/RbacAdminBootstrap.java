package top.egon.mario.rbac.service.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds the built-in administrator role, API permission and user when explicitly enabled.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
@Slf4j
public class RbacAdminBootstrap implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ADMIN_API_PERMISSION_CODE = "api:rbac:admin:*";
    private static final String ADMIN_API_PATTERN = "/api/admin/**";
    private static final String CHAT_API_PERMISSION_CODE = "api:chat:stream";
    private static final String CHAT_API_PATTERN = "/demo/chat/stream";
    private static final String AUTH_SELF_API_PERMISSION_CODE = "api:rbac:auth:self";
    private static final String AUTH_SELF_API_PATTERN = "/api/auth/**";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final RbacAdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ApiRepository apiRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    @Transactional
    public void bootstrap() {
        if (!properties.enabled()) {
            LogUtil.debug(log).log("rbac admin bootstrap skipped, enabled=false");
            return;
        }

        UserPo admin = ensureAdminUser();
        RolePo role = ensureSuperAdminRole();
        PermissionPo permission = ensureAdminApiPermission();
        PermissionPo chatPermission = ensureChatApiPermission();
        PermissionPo authSelfPermission = ensureAuthSelfApiPermission();
        ensureAdminApi(permission);
        ensureChatApi(chatPermission);
        ensureAuthSelfApi(authSelfPermission);
        ensureUserRole(admin, role);
        ensureRolePermission(role, permission);
        ensureRolePermission(role, chatPermission);
        ensureRolePermission(role, authSelfPermission);
        grantAllPermissions(role);
        eventPublisher.publishEvent(new RbacPermissionChangedEvent("bootstrap super administrator"));
        LogUtil.info(log).log("rbac admin bootstrap completed, username={}, roleCode={}",
                admin.getUsername(), role.getRoleCode());
    }

    private UserPo ensureAdminUser() {
        UserPo user = userRepository.findByUsernameAndDeletedFalse(properties.username())
                .orElseGet(this::newAdminUser);
        user.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : "Administrator");
        user.setStatus(RbacStatus.ENABLED);
        user.setLocked(false);
        return userRepository.save(user);
    }

    private UserPo newAdminUser() {
        validateBootstrapPassword();
        UserPo user = new UserPo();
        user.setUsername(properties.username());
        user.setNickname("Administrator");
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setStatus(RbacStatus.ENABLED);
        user.setPasswordExpired(properties.requirePasswordChange());
        user.setRemark("Built-in RBAC super administrator");
        return user;
    }

    private RolePo ensureSuperAdminRole() {
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(SUPER_ADMIN_ROLE_CODE)
                .orElseGet(RolePo::new);
        role.setRoleCode(SUPER_ADMIN_ROLE_CODE);
        role.setRoleName("Super Administrator");
        role.setStatus(RbacStatus.ENABLED);
        role.setSortNo(0);
        role.setBuiltIn(true);
        role.setDescription("Built-in role with all RBAC administration APIs");
        return roleRepository.save(role);
    }

    private PermissionPo ensureAdminApiPermission() {
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(ADMIN_API_PERMISSION_CODE)
                .orElseGet(PermissionPo::new);
        permission.setPermCode(ADMIN_API_PERMISSION_CODE);
        permission.setPermName("RBAC Administration APIs");
        permission.setPermType(PermissionType.API);
        permission.setStatus(PermissionStatus.ENABLED);
        permission.setSortNo(0);
        permission.setDescription("Wildcard API permission for RBAC administration endpoints");
        markManagedPermission(permission, "rbac");
        return permissionRepository.save(permission);
    }

    private PermissionPo ensureChatApiPermission() {
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(CHAT_API_PERMISSION_CODE)
                .orElseGet(PermissionPo::new);
        permission.setPermCode(CHAT_API_PERMISSION_CODE);
        permission.setPermName("Agent Chat Stream API");
        permission.setPermType(PermissionType.API);
        permission.setStatus(PermissionStatus.ENABLED);
        permission.setSortNo(1);
        permission.setDescription("Chat streaming endpoint for authenticated CyberMario users");
        markManagedPermission(permission, "chat");
        return permissionRepository.save(permission);
    }

    private PermissionPo ensureAuthSelfApiPermission() {
        PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(AUTH_SELF_API_PERMISSION_CODE)
                .orElseGet(PermissionPo::new);
        permission.setPermCode(AUTH_SELF_API_PERMISSION_CODE);
        permission.setPermName("RBAC 认证自助接口");
        permission.setPermType(PermissionType.API);
        permission.setStatus(PermissionStatus.ENABLED);
        permission.setSortNo(2);
        permission.setDescription("Authenticated user endpoints for logout and current-session permission refresh");
        markManagedPermission(permission, "rbac");
        return permissionRepository.save(permission);
    }

    private void markManagedPermission(PermissionPo permission, String ownerApp) {
        if (permission.isManaged()) {
            permission.setLastSeenAt(Instant.now());
            return;
        }
        permission.setManaged(true);
        permission.setOwnerApp(ownerApp);
        permission.setSourceType(RbacResourceSource.CATALOG.name());
        permission.setSourceKey(ownerApp + ":" + permission.getPermCode());
        permission.setSyncHash(digest(permission.getPermCode() + "|" + permission.getPermName() + "|" + permission.getDescription()));
        permission.setLastSyncedAt(Instant.now());
        permission.setLastSeenAt(Instant.now());
    }

    private void ensureAdminApi(PermissionPo permission) {
        ApiPo api = apiRepository.findById(permission.getId()).orElseGet(ApiPo::new);
        api.setPermissionId(permission.getId());
        api.setHttpMethod("ANY");
        api.setUrlPattern(ADMIN_API_PATTERN);
        api.setMatcherType(ApiMatcherType.ANT);
        api.setPublicFlag(false);
        api.setServiceTag("rbac");
        api.setOperationName("RBAC administration APIs");
        api.setRiskLevel(ApiRiskLevel.HIGH);
        api.setLastScannedAt(Instant.now());
        apiRepository.save(api);
    }

    private void ensureChatApi(PermissionPo permission) {
        ApiPo api = apiRepository.findById(permission.getId()).orElseGet(ApiPo::new);
        api.setPermissionId(permission.getId());
        api.setHttpMethod("POST");
        api.setUrlPattern(CHAT_API_PATTERN);
        api.setMatcherType(ApiMatcherType.EXACT);
        api.setPublicFlag(false);
        api.setServiceTag("chat");
        api.setOperationName("Agent chat stream");
        api.setRiskLevel(ApiRiskLevel.MEDIUM);
        api.setLastScannedAt(Instant.now());
        apiRepository.save(api);
    }

    private void ensureAuthSelfApi(PermissionPo permission) {
        ApiPo api = apiRepository.findById(permission.getId()).orElseGet(ApiPo::new);
        api.setPermissionId(permission.getId());
        api.setHttpMethod("ANY");
        api.setUrlPattern(AUTH_SELF_API_PATTERN);
        api.setMatcherType(ApiMatcherType.ANT);
        api.setPublicFlag(false);
        api.setServiceTag("rbac");
        api.setOperationName("RBAC authentication self-service APIs");
        api.setRiskLevel(ApiRiskLevel.MEDIUM);
        api.setLastScannedAt(Instant.now());
        apiRepository.save(api);
    }

    private void ensureUserRole(UserPo admin, RolePo role) {
        boolean exists = userRoleRepository.findByUserId(admin.getId()).stream()
                .anyMatch(relation -> role.getId().equals(relation.getRoleId()));
        if (exists) {
            return;
        }
        UserRolePo relation = new UserRolePo();
        relation.setUserId(admin.getId());
        relation.setRoleId(role.getId());
        relation.setGrantedAt(Instant.now());
        userRoleRepository.save(relation);
    }

    private void ensureRolePermission(RolePo role, PermissionPo permission) {
        boolean exists = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .anyMatch(relation -> permission.getId().equals(relation.getPermissionId()));
        if (exists) {
            return;
        }
        RolePermissionPo relation = new RolePermissionPo();
        relation.setRoleId(role.getId());
        relation.setPermissionId(permission.getId());
        relation.setGrantedAt(Instant.now());
        rolePermissionRepository.save(relation);
    }

    private void grantAllPermissions(RolePo role) {
        Set<Long> grantedPermissionIds = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(RolePermissionPo::getPermissionId)
                .collect(Collectors.toSet());
        permissionRepository.findAll().stream()
                .filter(permission -> !permission.isDeleted())
                .filter(permission -> !grantedPermissionIds.contains(permission.getId()))
                .forEach(permission -> {
                    ensureRolePermission(role, permission);
                    grantedPermissionIds.add(permission.getId());
                });
    }

    private void validateBootstrapPassword() {
        if (!StringUtils.hasText(properties.password())) {
            throw new IllegalStateException("RBAC bootstrap administrator password is required when bootstrap is enabled");
        }
        if (properties.password().length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("RBAC bootstrap administrator password must contain at least 12 characters");
        }
    }

    private String digest(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("rbac bootstrap digest algorithm is unavailable", e);
        }
    }

}
