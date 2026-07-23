package top.egon.mario.rbac.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.activation.service.RbacAccountActivationTokenService;
import top.egon.mario.rbac.converter.RbacDtoConverter;
import top.egon.mario.rbac.dto.request.ChangeCurrentUserPasswordRequest;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.UpdateCurrentUserProfileRequest;
import top.egon.mario.rbac.dto.request.UpdateUserRequest;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacUserService;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User management service for account CRUD and direct role assignment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class RbacUserServiceImpl implements RbacUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom;
    private final RbacAccountActivationTokenService accountActivationTokenService;

    @Override
    @Transactional
    public UserResponse createPendingUser(CreateUserRequest request, Long actorUserId) {
        String accountNo = normalizeAccountNo(request.getAccountNo());
        String username = normalizeUsername(request.getUsername());
        if (userRepository.existsByAccountNoAndDeletedFalse(accountNo)) {
            throw new RbacException("RBAC_USER_ACCOUNT_NO_DUPLICATED", "account number already exists");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw new RbacException("RBAC_USER_USERNAME_DUPLICATED", "username already exists");
        }
        checkUniqueContact(request.getEmail(), request.getMobile());

        UserPo user = rbacDtoConverter.toUserPo(request);
        user.setAccountNo(accountNo);
        user.setUsername(username);
        user.setEmail(request.getEmail().trim());
        user.setMobile(trimToNull(request.getMobile()));
        byte[] placeholderBytes = new byte[32];
        secureRandom.nextBytes(placeholderBytes);
        String placeholder = Base64.getUrlEncoder().withoutPadding().encodeToString(placeholderBytes);
        user.setPasswordHash(passwordEncoder.encode(placeholder));
        user.setStatus(RbacStatus.ENABLED);
        user.setLocked(false);
        user.setPasswordExpired(true);
        user.setActivatedAt(null);
        UserPo savedUser = userRepository.save(user);
        replaceUserRoles(savedUser.getId(), request.getRoleIds(), actorUserId);
        auditService.log(actorUserId, "RBAC_USER_CREATE_PENDING", "USER", savedUser.getId(),
                null, savedUser.getUsername(), null, null);
        LogUtil.info(log).log("rbac pending user created, userId={}, actorUserId={}",
                savedUser.getId(), actorUserId);
        return rbacDtoConverter.toUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPo getUserPo(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return rbacDtoConverter.toUserResponse(getUserPo(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUserPage(Pageable pageable) {
        return userRepository.findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable)
                .map(rbacDtoConverter::toUserResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserPo user = getUserPo(userId);
        String newEmail = trimToNull(request.getEmail());
        if (newEmail != null && !newEmail.equals(user.getEmail())
                && userRepository.existsByEmailAndDeletedFalse(newEmail)) {
            throw new RbacException("RBAC_USER_EMAIL_DUPLICATED", "email already exists");
        }
        if (StringUtils.hasText(request.getMobile()) && !request.getMobile().equals(user.getMobile())
                && userRepository.existsByMobileAndDeletedFalse(request.getMobile())) {
            throw new RbacException("RBAC_USER_MOBILE_DUPLICATED", "mobile already exists");
        }
        boolean pending = user.getActivatedAt() == null;
        boolean disabling = request.getStatus() != null
                && rbacDtoConverter.toPoRbacStatus(request.getStatus()) != RbacStatus.ENABLED;
        boolean locking = Boolean.TRUE.equals(request.getLocked());
        if (pending && (!Objects.equals(user.getEmail(), newEmail) || disabling || locking)) {
            revokePendingActivation(user, null, "pending user profile or availability changed");
        }
        user.setNickname(request.getNickname());
        user.setEmail(newEmail);
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
        UserPo savedUser = userRepository.save(user);
        LogUtil.info(log).log("rbac user updated, userId={}", userId);
        return rbacDtoConverter.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(Long userId, UpdateCurrentUserProfileRequest request) {
        UserPo user = getUserPo(userId);
        checkUniqueContactForUser(user, request.email(), request.mobile());
        user.setNickname(request.nickname());
        user.setEmail(trimToNull(request.email()));
        user.setMobile(trimToNull(request.mobile()));
        user.setAvatarUrl(request.avatarUrl());
        UserPo savedUser = userRepository.save(user);
        LogUtil.info(log).log("rbac current user profile updated, userId={}", userId);
        return rbacDtoConverter.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public void changeCurrentUserPassword(Long userId, ChangeCurrentUserPasswordRequest request) {
        UserPo user = getUserPo(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new RbacException("RBAC_CURRENT_PASSWORD_INVALID", "current password is invalid");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new RbacException("RBAC_PASSWORD_CONFIRM_MISMATCH", "password confirmation does not match");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new RbacException("RBAC_PASSWORD_UNCHANGED", "new password must be different from current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordExpired(false);
        userRepository.save(user);
        LogUtil.info(log).log("rbac current user password changed, userId={}", userId);
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        UserPo user = getUserPo(userId);
        if (user.getActivatedAt() == null) {
            throw new RbacException("AUTH_USER_NOT_ACTIVATED",
                    "pending user password must be set through account activation");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordExpired(false);
        userRepository.save(user);
        LogUtil.info(log).log("rbac user password reset, userId={}", userId);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, top.egon.mario.rbac.dto.enums.RbacStatus status) {
        UserPo user = getUserPo(userId);
        if (status != top.egon.mario.rbac.dto.enums.RbacStatus.ENABLED) {
            revokePendingActivation(user, null, "pending user disabled");
        }
        user.setStatus(rbacDtoConverter.toPoRbacStatus(status));
        userRepository.save(user);
        LogUtil.info(log).log("rbac user status updated, userId={}, statusCode={}", userId, status.getCode());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long actorUserId) {
        UserPo user = getUserPo(userId);
        if (userId.equals(actorUserId)) {
            throw new RbacException("RBAC_USER_DELETE_SELF", "current user cannot be deleted");
        }
        if ("admin".equalsIgnoreCase(user.getAccountNo())) {
            throw new RbacException("RBAC_USER_BUILT_IN", "built-in administrator cannot be deleted");
        }
        revokePendingActivation(user, actorUserId, "pending user deleted");
        user.setDeleted(true);
        userRepository.save(user);
        auditService.log(actorUserId, "RBAC_USER_DELETE", "USER", userId, user.getUsername(), null, null, null);
        LogUtil.info(log).log("rbac user deleted, userId={}, actorUserId={}", userId, actorUserId);
    }

    @Override
    @Transactional
    public void markLoginSuccess(Long userId) {
        UserPo user = getUserPo(userId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    @Override
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
        LogUtil.info(log).log("rbac user roles replaced, userId={}, roleCount={}, actorUserId={}",
                userId, requestedRoleIds.size(), actorUserId);
        publishPermissionChanged("update user roles");
    }

    @Override
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

    private void checkUniqueContactForUser(UserPo user, String email, String mobile) {
        String trimmedEmail = trimToNull(email);
        if (trimmedEmail != null && !trimmedEmail.equals(user.getEmail())
                && userRepository.existsByEmailAndDeletedFalse(trimmedEmail)) {
            throw new RbacException("RBAC_USER_EMAIL_DUPLICATED", "email already exists");
        }
        String trimmedMobile = trimToNull(mobile);
        if (trimmedMobile != null && !trimmedMobile.equals(user.getMobile())
                && userRepository.existsByMobileAndDeletedFalse(trimmedMobile)) {
            throw new RbacException("RBAC_USER_MOBILE_DUPLICATED", "mobile already exists");
        }
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new RbacException("RBAC_USER_USERNAME_REQUIRED", "username is required");
        }
        return username.trim().toLowerCase();
    }

    private String normalizeAccountNo(String accountNo) {
        if (!StringUtils.hasText(accountNo)) {
            throw new RbacException("RBAC_USER_ACCOUNT_NO_REQUIRED", "account number is required");
        }
        return accountNo.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void revokePendingActivation(UserPo user, Long actorUserId, String reason) {
        if (user.getActivatedAt() == null) {
            accountActivationTokenService.revokeForUser(user.getId(), actorUserId, reason);
        }
    }

    private void publishPermissionChanged(String reason) {
        eventPublisher.publishEvent(new RbacPermissionChangedEvent(reason));
    }

}
