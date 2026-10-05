package com.shineunsigan.snsg.auth.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.common.exception.BusinessException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization 헤더의 토큰을 확인해 principal에 userId(UUID)를 넣는다.
 * 이 값이 {@code @AuthenticationPrincipal UUID userId}로 들어가고 감사 컬럼의 주체가 된다.
 * <p>
 * 토큰이 없거나 틀려도 여기서는 막지 않는다. 익명으로 통과시키고 보호된 경로면 Security가 막는다.
 * 그래야 비로그인도 볼 수 있는 경로에 낡은 토큰이 섞여 와도 조회가 된다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenPort tokenPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        resolveToken(request).ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(PREFIX.length()));
    }

    private void authenticate(String token) {
        try {
            UUID userId = tokenPort.parseUserId(token);
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
