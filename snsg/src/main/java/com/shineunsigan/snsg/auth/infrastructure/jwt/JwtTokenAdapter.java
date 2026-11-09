package com.shineunsigan.snsg.auth.infrastructure.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.dto.result.RefreshTokenClaims;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * access와 refresh는 같은 키로 서명한다. 세션 claim(sid)의 유무로 둘을 가른다.
 * refresh에만 sid가 있으므로, sid가 있는 토큰은 access로 받지 않고 sid가 없는 토큰은 refresh로 받지 않는다.
 */
@Component
public class JwtTokenAdapter implements TokenPort {

    /** 세션 ID claim. OIDC의 표준 이름을 따른다. */
    static final String SESSION_ID_CLAIM = "sid";

    private final SecretKey key;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenAdapter(JwtProperties jwtProperties, AuthProperties authProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = authProperties.accessTokenValidity();
        this.refreshTokenValidity = authProperties.refreshTokenValidity();
    }

    @Override
    public String issueAccessToken(UUID userId) {
        return builder(userId, accessTokenValidity).compact();
    }

    @Override
    public String issueRefreshToken(UUID userId, String sessionId) {
        return builder(userId, refreshTokenValidity)
                .claim(SESSION_ID_CLAIM, sessionId)
                .compact();
    }

    @Override
    public UUID parseUserId(String token) {
        try {
            Claims claims = parse(token);
            if (claims.get(SESSION_ID_CLAIM) != null) {
                // refresh token을 access 자리에 쓰지 못하게 한다. 수명이 훨씬 길다.
                throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    @Override
    public RefreshTokenClaims parseRefreshToken(String token) {
        try {
            Claims claims = parse(token);
            String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
            if (sessionId == null || sessionId.isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            return new RefreshTokenClaims(UUID.fromString(claims.getSubject()), sessionId);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private JwtBuilder builder(UUID userId, Duration validity) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + validity.toMillis());
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key);
    }
}
