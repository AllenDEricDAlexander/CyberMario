package top.egon.mario.rbac.activation.store;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.model.StoredActivationToken;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Generates raw tokens and centralizes hash-only persistence, lookup, deletion, and revocation.
 */
@Component
@RequiredArgsConstructor
public class RbacOneTimeTokenStore {

    private static final int TOKEN_BYTES = 32;

    private final OneTimeTokenRepository tokenRepository;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public Optional<OneTimeTokenPo> lockCurrentForUser(Long userId) {
        return tokenRepository.findByUserAndPurposeForUpdate(
                userId, OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
    }

    public IssuedActivationToken replace(OneTimeTokenPo current, Long userId, String username,
                                         Long actorUserId, Duration ttl) {
        if (current != null) {
            tokenRepository.delete(current);
            tokenRepository.flush();
        }
        String rawToken = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        OneTimeTokenPo row = new OneTimeTokenPo();
        row.setUserId(userId);
        row.setPurpose(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
        row.setTokenHash(hash(rawToken));
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(now);
        row.setCreatedBy(actorUserId);
        tokenRepository.saveAndFlush(row);
        return new IssuedActivationToken(userId,
                new DefaultOneTimeToken(rawToken, username, expiresAt));
    }

    public Optional<StoredActivationToken> findForUpdate(String rawToken) {
        return tokenRepository.findByHashForUpdate(hash(rawToken), OneTimeTokenPurpose.ACCOUNT_ACTIVATION)
                .map(row -> new StoredActivationToken(row,
                        new DefaultOneTimeToken(rawToken, String.valueOf(row.getUserId()), row.getExpiresAt())));
    }

    public void delete(OneTimeTokenPo row) {
        tokenRepository.delete(row);
    }

    public boolean revoke(Long userId) {
        Optional<OneTimeTokenPo> current = lockCurrentForUser(userId);
        current.ifPresent(tokenRepository::delete);
        return current.isPresent();
    }

    public String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
