package com.shineunsigan.snsg.admin.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 경로({@code /api/admin/**})의 보안 체인. 사용자 체인보다 먼저 적용되고, 이 경로에는 사용자 체인이 돌지 않는다.
 * 그래서 사용자 토큰으로는 어드민 경로를, 어드민 토큰으로는 사용자 경로를 통과할 수 없다.
 * <p>
 * 공개 경로는 여기에만 적고 <b>HTTP 메서드와 경로를 정확히</b> 지정한다. 그 밖의 어드민 경로는 모두 인증이 필요하다.
 * {@code POST /api/admin/v1/auth/tokens}(재발급)는 공개지만 {@code DELETE}(로그아웃)는 인증이 필요하다.
 */
@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private static final String ADMIN_PATHS = "/api/admin/**";

    private final CorsConfigurationSource corsConfigurationSource;
    private final AdminTokenPort adminTokenPort;
    private final AdminQueryService adminQueryService;
    private final AdminAuthenticationEntryPoint adminAuthenticationEntryPoint;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(ADMIN_PATHS)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/admin/v1/auth/signup", "/api/admin/v1/auth/login",
                                "/api/admin/v1/auth/tokens").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(adminAuthenticationEntryPoint))
                .addFilterBefore(new AdminAuthenticationFilter(adminTokenPort, adminQueryService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
