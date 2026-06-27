package top.egon.mario.rbac.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.converter.RbacDtoConverter;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.request.RegisterRequest;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.RefreshTokenPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RefreshTokenRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacPermissionVersionService;
import top.egon.mario.rbac.service.RbacUserService;
import top.egon.mario.rbac.service.cache.RbacTokenCache;
import top.egon.mario.rbac.service.security.JwtClaims;
import top.egon.mario.rbac.service.security.JwtTokenPair;
import top.egon.mario.rbac.service.security.JwtTokenService;
import top.egon.mario.rbac.service.security.PasswordTransportEncryptionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Authentication application service for login, refresh, logout and token authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class RbacAuthApplication {

    private static final List<String> DEFAULT_REGISTER_ROLE_CODES = List.of("CHAT_BASIC", "RAG_USER",
            "AGENT_DASHBOARD_USER", "AGENT_MCP_USER");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RbacUserService rbacUserService;
    private final RbacEffectivePermissionService effectivePermissionService;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;
    private final RbacTokenCache tokenCache;
    private final RbacPermissionVersionService permissionVersionService;
    private final PasswordTransportEncryptionService passwordTransportEncryptionService;

    @Transactional
    public LoginResponse register(@Valid @NotNull RegisterRequest request, String ip, String userAgent) {
        String accountNo = normalizeAccountNo(request.accountNo());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String password = passwordTransportEncryptionService.decryptPassword(
                request.passwordKeyId(), request.encryptedPassword());
        validateRegisterPassword(password);
        if (userRepository.existsByAccountNoAndDeletedFalse(accountNo)) {
            throw new RbacException("RBAC_USER_ACCOUNT_NO_DUPLICATED", "account number already exists");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw new RbacException("RBAC_USER_USERNAME_DUPLICATED", "username already exists");
        }
        if (hasText(request.email()) && userRepository.existsByEmailAndDeletedFalse(request.email().trim())) {
            throw new RbacException("RBAC_USER_EMAIL_DUPLICATED", "email already exists");
        }
        if (hasText(request.mobile()) && userRepository.existsByMobileAndDeletedFalse(request.mobile().trim())) {
            throw new RbacException("RBAC_USER_MOBILE_DUPLICATED", "mobile already exists");
        }
        List<RolePo> defaultRoles = DEFAULT_REGISTER_ROLE_CODES.stream()
                .map(this::getDefaultRegisterRole)
                .toList();

        UserPo user = new UserPo();
        user.setAccountNo(accountNo);
        user.setUsername(username);
        user.setNickname(trimToNull(request.nickname()));
        user.setEmail(trimToNull(request.email()));
        user.setMobile(trimToNull(request.mobile()));
        user.setAvatarUrl(request.avatarUrl());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(RbacStatus.ENABLED);
        user.setLocked(false);
        user.setPasswordExpired(false);
        UserPo savedUser = userRepository.save(user);
        Instant now = Instant.now();
        userRoleRepository.saveAll(defaultRoles.stream()
                .map(role -> {
                    UserRolePo relation = new UserRolePo();
                    relation.setUserId(savedUser.getId());
                    relation.setRoleId(role.getId());
                    relation.setGrantedAt(now);
                    return relation;
                })
                .toList());

        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(savedUser.getId(), savedUser.getUsername());
        String refreshTokenHash = jwtTokenService.hashToken(tokenPair.refreshToken());
        saveRefreshToken(savedUser.getId(), tokenPair.refreshTokenId(), refreshTokenHash, tokenPair.refreshTokenExpiresInSeconds(), ip, userAgent);
        tokenCache.storeTokenPair(savedUser.getId(), tokenPair, refreshTokenHash);
        auditService.log(savedUser.getId(), "AUTH_REGISTER", "USER", savedUser.getId(), null, savedUser.getUsername(), ip, userAgent);
        LogUtil.info(log).log("registration succeeded, userId={}, accessTokenId={}, refreshTokenId={}",
                savedUser.getId(), tokenPair.accessTokenId(), tokenPair.refreshTokenId());
        return buildLoginResponse(savedUser, tokenPair);
    }

    @Transactional
    public LoginResponse login(@Valid @NotNull LoginRequest request, String ip, String userAgent) {
        UserPo user = findLoginUser(request.account())
                .orElseThrow(() -> new RbacException("AUTH_INVALID_CREDENTIALS", "account or password is invalid"));
        ensureUserCanLogin(user);
        String password = passwordTransportEncryptionService.decryptPassword(
                request.passwordKeyId(), request.encryptedPassword());
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.log(user.getId(), "AUTH_LOGIN_FAILED", "USER", user.getId(), null, null, ip, userAgent);
            LogUtil.warn(log).log("login rejected, reason=bad_credentials, userId={}", user.getId());
            throw new RbacException("AUTH_INVALID_CREDENTIALS", "account or password is invalid");
        }
        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(user.getId(), user.getUsername());
        String refreshTokenHash = jwtTokenService.hashToken(tokenPair.refreshToken());
        saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), refreshTokenHash, tokenPair.refreshTokenExpiresInSeconds(), ip, userAgent);
        tokenCache.storeTokenPair(user.getId(), tokenPair, refreshTokenHash);
        rbacUserService.markLoginSuccess(user.getId());
        auditService.log(user.getId(), "AUTH_LOGIN_SUCCESS", "USER", user.getId(), null, null, ip, userAgent);
        LogUtil.info(log).log("login succeeded, userId={}, accessTokenId={}, refreshTokenId={}",
                user.getId(), tokenPair.accessTokenId(), tokenPair.refreshTokenId());
        return buildLoginResponse(user, tokenPair);
    }

    @Transactional
    public LoginResponse refresh(@NotBlank String refreshToken, String ip, String userAgent) {
        JwtClaims claims = jwtTokenService.validate(refreshToken, "refresh");
        String refreshTokenHash = jwtTokenService.hashToken(refreshToken);
        tokenCache.findRefreshTokenHash(claims).ifPresent(cachedHash -> {
            if (!cachedHash.equals(refreshTokenHash)) {
                throw new RbacException("AUTH_TOKEN_INVALID", "refresh token is invalid");
            }
        });
        RefreshTokenPo oldToken = refreshTokenRepository.findByTokenId(claims.tokenId())
                .orElseThrow(() -> new RbacException("AUTH_TOKEN_INVALID", "refresh token is unknown"));
        if (oldToken.getRevokedAt() != null || oldToken.getExpiresAt().isBefore(Instant.now())
                || !oldToken.getTokenHash().equals(refreshTokenHash)) {
            tokenCache.evictRefreshToken(claims.tokenId());
            LogUtil.warn(log).log("refresh rejected, reason=token_state_invalid, userId={}, refreshTokenId={}",
                    claims.userId(), claims.tokenId());
            throw new RbacException("AUTH_TOKEN_INVALID", "refresh token is invalid");
        }
        UserPo user = userRepository.findByIdAndDeletedFalse(claims.userId())
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        ensureUserCanLogin(user);
        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(user.getId(), user.getUsername());
        oldToken.setRevokedAt(Instant.now());
        oldToken.setReplacedByTokenId(tokenPair.refreshTokenId());
        refreshTokenRepository.save(oldToken);
        tokenCache.evictRefreshToken(oldToken.getTokenId());
        String newRefreshTokenHash = jwtTokenService.hashToken(tokenPair.refreshToken());
        saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), newRefreshTokenHash, tokenPair.refreshTokenExpiresInSeconds(), ip, userAgent);
        tokenCache.storeTokenPair(user.getId(), tokenPair, newRefreshTokenHash);
        auditService.log(user.getId(), "AUTH_REFRESH", "USER", user.getId(), null, null, ip, userAgent);
        LogUtil.info(log).log("token refreshed, userId={}, oldRefreshTokenId={}, newRefreshTokenId={}",
                user.getId(), oldToken.getTokenId(), tokenPair.refreshTokenId());
        return buildLoginResponse(user, tokenPair);
    }

    @Transactional
    public void logout(@NotBlank String refreshToken) {
        JwtClaims claims = jwtTokenService.validate(refreshToken, "refresh");
        refreshTokenRepository.findByTokenId(claims.tokenId()).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            tokenCache.evictRefreshToken(claims.tokenId());
            LogUtil.info(log).log("logout completed, userId={}, refreshTokenId={}", claims.userId(), claims.tokenId());
        });
    }

    @Transactional(readOnly = true)
    public Authentication authenticateAccessToken(@NotBlank String accessToken) {
        JwtClaims claims = jwtTokenService.validate(accessToken, "access");
        if (!tokenCache.isAccessTokenActive(claims)) {
            LogUtil.warn(log).log("access token rejected, reason=inactive, userId={}, accessTokenId={}",
                    claims.userId(), claims.tokenId());
            throw new RbacException("AUTH_TOKEN_INVALID", "access token is inactive");
        }
        UserPo user = userRepository.findByIdAndDeletedFalse(claims.userId())
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        ensureUserCanLogin(user);
        EffectivePermissionResponse permissions = effectivePermissionService.getUserEffectivePermissions(user.getId());
        String permissionVersion = permissionVersionService.resolvePermissionVersion(permissions.roleIds());
        RbacPrincipal principal = new RbacPrincipal(user.getId(), user.getUsername(), permissions.roleCodes(),
                permissions.apiCodes(), permissionVersion);
        Set<SimpleGrantedAuthority> authorities = Stream.concat(permissions.roleCodes().stream(), permissions.apiCodes().stream())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        return new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);
    }

    @Transactional(readOnly = true)
    public LoginResponse currentUser(@NotNull Long userId) {
        UserPo user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        return buildLoginResponse(user, null);
    }

    private LoginResponse buildLoginResponse(UserPo user, JwtTokenPair tokenPair) {
        EffectivePermissionResponse permissions = effectivePermissionService.getUserEffectivePermissions(user.getId());
        String permissionVersion = permissionVersionService.resolvePermissionVersion(permissions.roleIds());
        UserResponse userResponse = rbacDtoConverter.toUserResponse(user);
        return LoginResponse.builder()
                .accessToken(tokenPair == null ? null : tokenPair.accessToken())
                .refreshToken(tokenPair == null ? null : tokenPair.refreshToken())
                .accessTokenExpiresInSeconds(tokenPair == null ? 0 : tokenPair.accessTokenExpiresInSeconds())
                .refreshTokenExpiresInSeconds(tokenPair == null ? 0 : tokenPair.refreshTokenExpiresInSeconds())
                .user(userResponse)
                .roleCodes(permissions.roleCodes())
                .menus(effectivePermissionService.getUserMenuTree(user.getId()))
                .buttonCodes(permissions.buttonCodes())
                .permissionCodes(permissions.apiCodes())
                .permissionVersion(permissionVersion)
                .build();
    }

    private void saveRefreshToken(Long userId, String tokenId, String tokenHash, long ttlSeconds, String ip, String userAgent) {
        RefreshTokenPo refreshToken = new RefreshTokenPo();
        refreshToken.setUserId(userId);
        refreshToken.setTokenId(tokenId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        refreshToken.setIp(ip);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setCreatedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);
    }

    private void ensureUserCanLogin(UserPo user) {
        if (user.getStatus() != RbacStatus.ENABLED || user.isLocked() || user.isPasswordExpired()) {
            throw new RbacException("AUTH_USER_DISABLED", "user cannot login");
        }
    }

    private RolePo getDefaultRegisterRole(String roleCode) {
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(roleCode)
                .orElseThrow(() -> new RbacException("RBAC_DEFAULT_ROLE_NOT_FOUND", "default register role is missing"));
        if (role.getStatus() != RbacStatus.ENABLED) {
            throw new RbacException("RBAC_DEFAULT_ROLE_DISABLED", "default register role is disabled");
        }
        return role;
    }

    private Optional<UserPo> findLoginUser(String account) {
        String normalized = normalizeAccountNo(account);
        if (normalized.contains("@")) {
            return userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalized);
        }
        return userRepository.findByAccountNoAndDeletedFalse(normalized);
    }

    private String normalizeAccountNo(String accountNo) {
        return accountNo.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRegisterPassword(String password) {
        if (password.length() < 8 || password.length() > 128) {
            throw new RbacException("RBAC_USER_PASSWORD_INVALID", "password length must be between 8 and 128");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

}
