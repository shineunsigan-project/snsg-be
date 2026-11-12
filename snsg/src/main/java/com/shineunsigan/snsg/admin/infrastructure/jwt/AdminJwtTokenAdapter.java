package com.shineunsigan.snsg.admin.infrastructure.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.application.dto.result.AdminRefreshTokenClaims;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 어드민 토큰. 사용자 토큰과 다른 키({@code admin-jwt.secret})로 서명해, 사용자 토큰은 여기서 서명 검증부터 실패한다.
 * <p>
 * access와 refresh는 같은 키로 서명하고 세션 claim(sid)의 유무로 가른다. 사용자 토큰과 같은 방식이다.
 * 만료 시각은 application이 정해 넘긴다(세션 절대 만료). refresh의 만료가 곧 세션 만료라 재발급 때 그대로 읽어 돌려준다.
 */
@Component
public class AdminJwtTokenAdapter implements AdminTokenPort {

    /** 세션 ID claim. OIDC의 표준 이름을 따른다. */
    static final String SESSION_ID_CLAIM = "sid";

    private final SecretKey key;

    public AdminJwtTokenAdapter(AdminJwtProperties adminJwtProperties) {
        this.key = Keys.hmacShaKeyFor(adminJwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issueAccessToken(UUID adminId, Instant expiresAt) {
        return builder(adminId, expiresAt).compact();
    }

    @Override
    public String issueRefreshToken(UUID adminId, String sessionId, Instant expiresAt) {
        return builder(adminId, expiresAt)
                .claim(SESSION_ID_CLAIM, sessionId)
                .compact();
    }

    @Override
    public UUID parseAdminId(String token) {
        try {
            Claims claims = parse(token);
            if (claims.get(SESSION_ID_CLAIM) != null) {
                // refresh token을 access 자리에 쓰지 못하게 한다. 수명이 훨씬 길다.
                throw new BusinessException(AdminErrorCode.INVALID_ADMIN_ACCESS_TOKEN);
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_ACCESS_TOKEN);
        }
    }

    @Override
    public AdminRefreshTokenClaims parseRefreshToken(String token) {
        try {
            Claims claims = parse(token);
            String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
            if (sessionId == null || sessionId.isBlank()) {
                throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
            }
            return new AdminRefreshTokenClaims(UUID.fromString(claims.getSubject()), sessionId,
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private JwtBuilder builder(UUID adminId, Instant expiresAt) {
        return Jwts.builder()
                .subject(adminId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key);
    }
}
