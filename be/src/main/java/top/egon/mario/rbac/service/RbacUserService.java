package top.egon.mario.rbac.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.rbac.converter.RbacDtoConverter;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.UpdateUserRequest;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User management service for account CRUD and direct role assignment.
 */
@Service
@RequiredArgsConstructor
public class RbacUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long actorUserId) {
        String username = normalizeUsername(request.getUsername());
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw new RbacException("RBAC_USER_USERNAME_DUPLICATED", "username already exists");
        }
        checkUniqueContact(request.getEmail(), request.getMobile());

        UserPo user = rbacDtoConverter.toUserPo(request);
        user.setUsername(username);
        user.setEmail(trimToNull(request.getEmail()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setPasswordHash(passwordEncoder.encode(request.getInitialPassword()));
        user.setStatus(request.getStatus() == null ? RbacStatus.ENABLED : rbacDtoConverter.toPoRbacStatus(request.getStatus()));
        UserPo savedUser = userRepository.save(user);
        replaceUserRoles(savedUser.getId(), request.getRoleIds(), actorUserId);
        auditService.log(actorUserId, "RBAC_USER_CREATE", "USER", savedUser.getId(), null, savedUser.getUsername(), null, null);
        return rbacDtoConverter.toUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserPo getUserPo(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return rbacDtoConverter.toUserResponse(getUserPo(userId));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUserPage(Pageable pageable) {
        return userRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
                .map(rbacDtoConverter::toUserResponse);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserPo user = getUserPo(userId);
        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new RbacException("RBAC_USER_EMAIL_DUPLICATED", "email already exists");
        }
        if (StringUtils.hasText(request.getMobile()) && !request.getMobile().equals(user.getMobile())
                && userRepository.existsByMobileAndDeletedFalse(request.getMobile())) {
            throw new RbacException("RBAC_USER_MOBILE_DUPLICATED", "mobile already exists");
        }
        user.setNickname(request.getNickname());
        user.setEmail(trimToNull(request.getEmail()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setAvatarUrl(request.getAvatarUrl());
        user.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            user.setStatus(rbacDtoConverter.toPoRbacStatus(request.getStatus()));
        }
        if (request.getLocked() != null) {
            user.setLocked(request.getLocked());
        }
        if (request.getPasswordExpired() != null) {
            user.setPasswordExpired(request.getPasswordExpired());
        }
        return rbacDtoConverter.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        UserPo user = getUserPo(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordExpired(false);
        userRepository.save(user);
    }

    @Transactional
    public void updateStatus(Long userId, top.egon.mario.rbac.dto.enums.RbacStatus status) {
        UserPo user = getUserPo(userId);
        user.setStatus(rbacDtoConverter.toPoRbacStatus(status));
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId, Long actorUserId) {
        UserPo user = getUserPo(userId);
        if (userId.equals(actorUserId)) {
            throw new RbacException("RBAC_USER_DELETE_SELF", "current user cannot be deleted");
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new RbacException("RBAC_USER_BUILT_IN", "built-in administrator cannot be deleted");
        }
        user.setDeleted(true);
        userRepository.save(user);
        auditService.log(actorUserId, "RBAC_USER_DELETE", "USER", userId, user.getUsername(), null, null, null);
    }

    @Transactional
    public void markLoginSuccess(Long userId) {
        UserPo user = getUserPo(userId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void replaceUserRoles(Long userId, Collection<Long> roleIds, Long actorUserId) {
        getUserPo(userId);
        Set<Long> requestedRoleIds = roleIds == null ? Set.of() : new HashSet<>(roleIds);
        validateRolesExist(requestedRoleIds);

        Set<Long> oldRoleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRolePo::getRoleId)
                .collect(Collectors.toSet());
        Set<Long> removedRoleIds = new HashSet<>(oldRoleIds);
        removedRoleIds.removeAll(requestedRoleIds);
        Set<Long> addedRoleIds = new HashSet<>(requestedRoleIds);
        addedRoleIds.removeAll(oldRoleIds);

        if (!removedRoleIds.isEmpty()) {
            userRoleRepository.deleteByUserIdAndRoleIdIn(userId, removedRoleIds);
        }
        Instant now = Instant.now();
        List<UserRolePo> addedRelations = addedRoleIds.stream()
                .map(roleId -> {
                    UserRolePo relation = new UserRolePo();
                    relation.setUserId(userId);
                    relation.setRoleId(roleId);
                    relation.setGrantedAt(now);
                    relation.setGrantedBy(actorUserId);
                    return relation;
                })
                .toList();
        userRoleRepository.saveAll(addedRelations);
        auditService.log(actorUserId, "RBAC_USER_ROLE_UPDATE", "USER", userId, oldRoleIds.toString(), requestedRoleIds.toString(), null, null);
    }

    @Transactional(readOnly = true)
    public Set<Long> getDirectRoleIds(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRolePo::getRoleId)
                .collect(Collectors.toSet());
    }

    private void validateRolesExist(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RolePo> roles = roleRepository.findByIdInAndDeletedFalse(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new RbacException("RBAC_ROLE_NOT_FOUND", "role not found");
        }
    }

    private void checkUniqueContact(String email, String mobile) {
        if (StringUtils.hasText(email) && userRepository.existsByEmailAndDeletedFalse(email.trim())) {
            throw new RbacException("RBAC_USER_EMAIL_DUPLICATED", "email already exists");
        }
        if (StringUtils.hasText(mobile) && userRepository.existsByMobileAndDeletedFalse(mobile.trim())) {
            throw new RbacException("RBAC_USER_MOBILE_DUPLICATED", "mobile already exists");
        }
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new RbacException("RBAC_USER_USERNAME_REQUIRED", "username is required");
        }
        return username.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
