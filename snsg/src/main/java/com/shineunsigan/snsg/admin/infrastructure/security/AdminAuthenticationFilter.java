package com.shineunsigan.snsg.admin.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.common.exception.BusinessException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 토큰을 확인해 principal에 adminId(UUID), 권한에 역할({@code ROLE_MASTER}·{@code ROLE_ADMIN})을 넣는다.
 * 어드민 보안 체인에만 들어간다.
 * <p>
 * 토큰이 유효해도 그 어드민이 삭제되지 않았고 승인 상태일 때만 인증을 채운다. 요청마다 DB에서 다시 보므로
 * 이용 중지 같은 상태 변경이 access token 만료를 기다리지 않고 바로 반영된다. 역할도 토큰이 아니라 DB에서 읽는다.
 * 아니면 익명으로 두어 보호 경로는 401이 된다.
 * <p>
 * 빈({@code @Component})으로 두지 않는다. 빈이면 Spring Boot가 서블릿 필터로도 모든 URL에 등록해 사용자 경로에서
 * 사용자 인증을 지우고, {@code @WebMvcTest}마다 이 필터의 의존을 요구한다. {@link AdminSecurityConfig}가 만든다.
 */
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AdminTokenPort adminTokenPort;
    private final AdminQueryService adminQueryService;

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
            UUID adminId = adminTokenPort.parseAdminId(token);
            adminQueryService.findApprovedRole(adminId)
                    .ifPresentOrElse(role -> setAuthentication(adminId, role), SecurityContextHolder::clearContext);
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    private void setAuthentication(UUID adminId, AdminRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(adminId, null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))));
    }
}
