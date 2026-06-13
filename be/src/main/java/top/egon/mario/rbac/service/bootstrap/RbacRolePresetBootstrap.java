package top.egon.mario.rbac.service.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.service.RbacPermissionVersionService;
import top.egon.mario.rbac.service.bootstrap.RbacRolePresetBootstrapProperties.SyncMode;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds reusable role presets without overwriting manually adjusted roles by default.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RbacRolePresetBootstrap implements ApplicationRunner {

    private static final String OWNER_APP = "rbac";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final RbacRolePresetBootstrapProperties properties;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RbacPermissionVersionService permissionVersionService;
    private final ApplicationEventPublisher eventPublisher;
    private final RbacRolePresetCatalog catalog = new RbacRolePresetCatalog();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    @Transactional
    public void bootstrap() {
        if (!properties.enabled()) {
            LogUtil.debug(log).log("rbac role preset bootstrap skipped, enabled=false");
            return;
        }

        boolean changed = false;
        for (RbacRolePresetCatalog.RolePresetSeed seed : catalog.roles()) {
            changed = ensureRolePreset(seed) || changed;
        }
        if (changed) {
            eventPublisher.publishEvent(new RbacPermissionChangedEvent("bootstrap preset roles"));
        }
        LogUtil.info(log).log("rbac role preset bootstrap completed, changed={}", changed);
    }

    private boolean ensureRolePreset(RbacRolePresetCatalog.RolePresetSeed seed) {
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(seed.roleCode()).orElse(null);
        boolean created = role == null;
        if (created) {
            role = new RolePo();
            role.setRoleCode(seed.roleCode());
            role.setStatus(RbacStatus.ENABLED);
            role.setBuiltIn(false);
            role.setManaged(true);
        } else if (!role.isManaged()) {
            return false;
        }
        role.setRoleName(seed.roleName());
        role.setSortNo(seed.sortNo());
        role.setDescription(seed.description());
        role.setOwnerApp(OWNER_APP);
        role.setSourceType(RbacResourceSource.CATALOG.name());
        role.setSourceKey(OWNER_APP + ":" + seed.roleCode());
        role.setSyncHash(digest(seed));
        role.setLastSyncedAt(Instant.now());
        role = roleRepository.save(role);
        boolean grantsChanged = false;
        if (created || properties.syncMode() == SyncMode.FORCE_SYNC) {
            grantsChanged = grantPermissions(role, seed);
        }
        if (grantsChanged) {
            permissionVersionService.bumpRolePermissionVersion(role.getId());
        }
        return created || grantsChanged;
    }

    private boolean grantPermissions(RolePo role, RbacRolePresetCatalog.RolePresetSeed seed) {
        Map<Long, RolePermissionPo> grantsByPermissionId = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .collect(Collectors.toMap(RolePermissionPo::getPermissionId, Function.identity()));
        boolean changed = false;
        for (String permissionCode : seed.permissionCodes()) {
            PermissionPo permission = permissionRepository.findByPermCodeAndDeletedFalse(permissionCode).orElse(null);
            if (permission == null || grantsByPermissionId.containsKey(permission.getId())) {
                continue;
            }
            RolePermissionPo grant = new RolePermissionPo();
            grant.setRoleId(role.getId());
            grant.setPermissionId(permission.getId());
            grant.setGrantedAt(Instant.now());
            rolePermissionRepository.save(grant);
            changed = true;
        }
        return changed;
    }

    private String digest(RbacRolePresetCatalog.RolePresetSeed seed) {
        String source = String.join("|",
                seed.roleCode(),
                seed.roleName(),
                seed.description(),
                Integer.toString(seed.sortNo()),
                String.join(",", seed.permissionCodes()));
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("rbac role preset digest algorithm is unavailable", e);
        }
    }

}
