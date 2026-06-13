package top.egon.mario.rbac.service.security.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.JwtClaims;
import top.egon.mario.rbac.service.security.JwtProperties;
import top.egon.mario.rbac.service.security.JwtTokenPair;
import top.egon.mario.rbac.service.security.JwtTokenService;

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
@Slf4j
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String USERNAME_CLAIM = "username";

    private final JwtProperties jwtProperties;
    private final SecretKeySpec secretKey;

    public JwtTokenServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public JwtTokenPair createTokenPair(Long userId, String username) {
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();
        LogUtil.debug(log).log("jwt token pair created, userId={}, accessTokenId={}, refreshTokenId={}",
                userId, accessTokenId, refreshTokenId);
        return new JwtTokenPair(
                createToken(userId, username, accessTokenId, "access", jwtProperties.accessTokenTtl()),
                createToken(userId, username, refreshTokenId, "refresh", jwtProperties.refreshTokenTtl()),
                accessTokenId,
                refreshTokenId,
                jwtProperties.accessTokenTtl().toSeconds(),
                jwtProperties.refreshTokenTtl().toSeconds()
        );
    }

    @Override
    public JwtClaims validate(String token, String expectedType) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secretKey))) {
                LogUtil.warn(log).log("jwt validation failed, reason=bad_signature, expectedType={}", expectedType);
                throw new RbacException("AUTH_TOKEN_INVALID", "token signature is invalid");
            }
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now())) {
                LogUtil.warn(log).log("jwt validation failed, reason=expired, expectedType={}", expectedType);
                throw new RbacException("AUTH_TOKEN_EXPIRED", "token expired");
            }
            String tokenType = claimsSet.getStringClaim(TOKEN_TYPE_CLAIM);
            if (!expectedType.equals(tokenType)) {
                LogUtil.warn(log).log("jwt validation failed, reason=type_mismatch, expectedType={}, actualType={}",
                        expectedType, tokenType);
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
            LogUtil.warn(log).log("jwt validation failed, reason=parse_error, expectedType={}", expectedType);
            throw new RbacException("AUTH_TOKEN_INVALID", "token is invalid");
        }
    }

    @Override
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            LogUtil.error(log).log("jwt token hash failed", e);
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
            LogUtil.error(log).log("jwt token creation failed, userId={}, tokenType={}", userId, tokenType, e);
            throw new RbacException("AUTH_TOKEN_CREATE_FAILED", "token creation failed");
        }
    }

}
