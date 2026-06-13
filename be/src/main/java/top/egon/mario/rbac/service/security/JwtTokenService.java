package top.egon.mario.rbac.service.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import top.egon.mario.rbac.service.RbacException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Signs, verifies and hashes RBAC JWT tokens.
 */
@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String USERNAME_CLAIM = "username";

    private final JwtProperties jwtProperties;
    private final SecretKeySpec secretKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public JwtTokenPair createTokenPair(Long userId, String username) {
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();
        return new JwtTokenPair(
                createToken(userId, username, accessTokenId, "access", jwtProperties.accessTokenTtl()),
                createToken(userId, username, refreshTokenId, "refresh", jwtProperties.refreshTokenTtl()),
                accessTokenId,
                refreshTokenId,
                jwtProperties.accessTokenTtl().toSeconds(),
                jwtProperties.refreshTokenTtl().toSeconds()
        );
    }

    public JwtClaims validate(String token, String expectedType) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secretKey))) {
                throw new RbacException("AUTH_TOKEN_INVALID", "token signature is invalid");
            }
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now())) {
                throw new RbacException("AUTH_TOKEN_EXPIRED", "token expired");
            }
            String tokenType = claimsSet.getStringClaim(TOKEN_TYPE_CLAIM);
            if (!expectedType.equals(tokenType)) {
                throw new RbacException("AUTH_TOKEN_INVALID", "token type is invalid");
            }
            return new JwtClaims(
                    Long.valueOf(claimsSet.getSubject()),
                    claimsSet.getStringClaim(USERNAME_CLAIM),
                    claimsSet.getJWTID(),
                    tokenType,
                    expirationTime.toInstant()
            );
        } catch (ParseException | JOSEException | NumberFormatException e) {
            throw new RbacException("AUTH_TOKEN_INVALID", "token is invalid");
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RbacException("AUTH_TOKEN_HASH_FAILED", "token hash failed");
        }
    }

    private String createToken(Long userId, String username, String tokenId, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(userId))
                .jwtID(tokenId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .claim(USERNAME_CLAIM, username)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        try {
            signedJWT.sign(new MACSigner(secretKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RbacException("AUTH_TOKEN_CREATE_FAILED", "token creation failed");
        }
    }

}
