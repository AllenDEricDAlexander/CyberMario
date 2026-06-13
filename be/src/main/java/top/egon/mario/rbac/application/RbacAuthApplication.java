package top.egon.mario.rbac.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.rbac.converter.RbacDtoConverter;
import top.egon.mario.rbac.dto.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.LoginRequest;
import top.egon.mario.rbac.dto.LoginResponse;
import top.egon.mario.rbac.dto.UserResponse;
import top.egon.mario.rbac.po.RefreshTokenPo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RefreshTokenRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacAuditService;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacUserService;
import top.egon.mario.rbac.service.security.JwtClaims;
import top.egon.mario.rbac.service.security.JwtTokenPair;
import top.egon.mario.rbac.service.security.JwtTokenService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Authentication application service for login, refresh, logout and token authentication.
 */
@Service
@RequiredArgsConstructor
public class RbacAuthApplication {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RbacUserService rbacUserService;
    private final RbacEffectivePermissionService effectivePermissionService;
    private final RbacDtoConverter rbacDtoConverter;
    private final RbacAuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        UserPo user = userRepository.findByUsernameAndDeletedFalse(request.username().trim().toLowerCase())
                .orElseThrow(() -> new RbacException("AUTH_INVALID_CREDENTIALS", "username or password is invalid"));
        ensureUserCanLogin(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user.getId(), "AUTH_LOGIN_FAILED", "USER", user.getId(), null, null, ip, userAgent);
            throw new RbacException("AUTH_INVALID_CREDENTIALS", "username or password is invalid");
        }
        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(user.getId(), user.getUsername());
        saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshToken(), tokenPair.refreshTokenExpiresInSeconds(), ip, userAgent);
        rbacUserService.markLoginSuccess(user.getId());
        auditService.log(user.getId(), "AUTH_LOGIN_SUCCESS", "USER", user.getId(), null, null, ip, userAgent);
        return buildLoginResponse(user, tokenPair);
    }

    @Transactional
    public LoginResponse refresh(String refreshToken, String ip, String userAgent) {
        JwtClaims claims = jwtTokenService.validate(refreshToken, "refresh");
        RefreshTokenPo oldToken = refreshTokenRepository.findByTokenId(claims.tokenId())
                .orElseThrow(() -> new RbacException("AUTH_TOKEN_INVALID", "refresh token is unknown"));
        if (oldToken.getRevokedAt() != null || oldToken.getExpiresAt().isBefore(Instant.now())
                || !oldToken.getTokenHash().equals(jwtTokenService.hashToken(refreshToken))) {
            throw new RbacException("AUTH_TOKEN_INVALID", "refresh token is invalid");
        }
        UserPo user = userRepository.findByIdAndDeletedFalse(claims.userId())
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        ensureUserCanLogin(user);
        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(user.getId(), user.getUsername());
        oldToken.setRevokedAt(Instant.now());
        oldToken.setReplacedByTokenId(tokenPair.refreshTokenId());
        refreshTokenRepository.save(oldToken);
        saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshToken(), tokenPair.refreshTokenExpiresInSeconds(), ip, userAgent);
        auditService.log(user.getId(), "AUTH_REFRESH", "USER", user.getId(), null, null, ip, userAgent);
        return buildLoginResponse(user, tokenPair);
    }

    @Transactional
    public void logout(String refreshToken) {
        JwtClaims claims = jwtTokenService.validate(refreshToken, "refresh");
        refreshTokenRepository.findByTokenId(claims.tokenId()).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public Authentication authenticateAccessToken(String accessToken) {
        JwtClaims claims = jwtTokenService.validate(accessToken, "access");
        UserPo user = userRepository.findByIdAndDeletedFalse(claims.userId())
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        ensureUserCanLogin(user);
        EffectivePermissionResponse permissions = effectivePermissionService.getUserEffectivePermissions(user.getId());
        RbacPrincipal principal = new RbacPrincipal(user.getId(), user.getUsername(), permissions.roleCodes(), permissions.apiCodes());
        Set<SimpleGrantedAuthority> authorities = Stream.concat(permissions.roleCodes().stream(), permissions.apiCodes().stream())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        return new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);
    }

    @Transactional(readOnly = true)
    public LoginResponse currentUser(Long userId) {
        UserPo user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
        return buildLoginResponse(user, null);
    }

    private LoginResponse buildLoginResponse(UserPo user, JwtTokenPair tokenPair) {
        EffectivePermissionResponse permissions = effectivePermissionService.getUserEffectivePermissions(user.getId());
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
                .build();
    }

    private void saveRefreshToken(Long userId, String tokenId, String token, long ttlSeconds, String ip, String userAgent) {
        RefreshTokenPo refreshToken = new RefreshTokenPo();
        refreshToken.setUserId(userId);
        refreshToken.setTokenId(tokenId);
        refreshToken.setTokenHash(jwtTokenService.hashToken(token));
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

}
