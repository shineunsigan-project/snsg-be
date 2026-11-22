package com.shineunsigan.snsg.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

/**
 * 쿠키를 주고받아야 하므로 {@code allowCredentials=true}이고, 그래서 허용 출처에 {@code *}를 쓸 수 없다.
 * 클라이언트는 요청에 credentials를 포함해야 쿠키가 전송된다.
 * <p>
 * 어드민 경로와 사용자 경로의 허용 출처를 나눈다. refresh 쿠키가 {@code SameSite=None}이라, 사용자 앱 출처가 어드민 경로를
 * credentials로 부를 수 있으면 사용자 앱의 XSS 한 번으로 그 브라우저의 어드민 세션이 재발급될 수 있다.
 * 두 패턴은 겹치지 않게 등록한다. 겹치면 먼저 등록한 패턴 하나만 적용된다.
 * <p>
 * PUT은 어드민 경로에만 허용한다. PUT을 쓰는 API는 어드민 장소 수정뿐이다.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private static final String USER_PATHS = "/api/v1/**";
    private static final String ADMIN_PATHS = "/api/admin/**";
    private static final List<String> USER_METHODS = List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ADMIN_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ADMIN_PATHS, configuration(corsProperties.adminAllowedOrigins(), ADMIN_METHODS));
        source.registerCorsConfiguration(USER_PATHS, configuration(corsProperties.allowedOrigins(), USER_METHODS));
        return source;
    }

    private CorsConfiguration configuration(List<String> allowedOrigins, List<String> allowedMethods) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }
}
