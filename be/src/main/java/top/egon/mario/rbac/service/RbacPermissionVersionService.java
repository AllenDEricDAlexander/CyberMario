package top.egon.mario.rbac.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.po.RolePermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maintains role-level permission versions and derives user permission snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacPermissionVersionService {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public String resolvePermissionVersion(Collection<Long> effectiveRoleIds) {
        if (effectiveRoleIds == null || effectiveRoleIds.isEmpty()) {
            return digest("roles:");
        }
        Set<Long> roleIds = new LinkedHashSet<>(effectiveRoleIds);
        String snapshot = roleRepository.findByIdInAndDeletedFalse(roleIds).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .map(role -> role.getId() + ":" + role.getPermissionVersion())
                .collect(Collectors.joining("|", "roles:", ""));
        return digest(snapshot);
    }

    @Transactional
    public void bumpRolePermissionVersion(Long roleId) {
        if (roleId == null) {
            return;
        }
        bumpRolePermissionVersions(List.of(roleId));
    }

    @Transactional
    public void bumpRolePermissionVersions(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> cacheRoleIds = new LinkedHashSet<>(roleIds);
        List<RolePo> roles = roleRepository.findByIdInAndDeletedFalse(cacheRoleIds);
        roles.forEach(role -> role.setPermissionVersion(role.getPermissionVersion() + 1));
        roleRepository.saveAll(roles);
        LogUtil.info(log).log("rbac role permission version bumped, roleCount={}, updatedCount={}",
                cacheRoleIds.size(), roles.size());
    }

    @Transactional
    public void bumpRolesByPermissionIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        Set<Long> roleIds = new LinkedHashSet<>();
        for (Long permissionId : permissionIds) {
            rolePermissionRepository.findByPermissionId(permissionId).stream()
                    .map(RolePermissionPo::getRoleId)
                    .forEach(roleIds::add);
        }
        bumpRolePermissionVersions(roleIds);
    }

    @Transactional
    public void bumpAllRolePermissionVersions() {
        List<RolePo> roles = roleRepository.findAll().stream()
                .filter(role -> !role.isDeleted())
                .toList();
        roles.forEach(role -> role.setPermissionVersion(role.getPermissionVersion() + 1));
        roleRepository.saveAll(roles);
        LogUtil.info(log).log("rbac role permission version bumped, scope=all, updatedCount={}", roles.size());
    }

    private String digest(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("permission version digest algorithm is unavailable", e);
        }
    }

}
