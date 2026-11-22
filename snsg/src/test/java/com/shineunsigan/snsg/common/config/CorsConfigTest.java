package com.shineunsigan.snsg.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 어드민 경로와 사용자 경로가 서로의 출처를 허용하지 않는지 실제 {@link CorsConfig}로 본다.
 */
class CorsConfigTest {

    private static final String USER_ORIGIN = "http://localhost:3000";
    private static final String ADMIN_ORIGIN = "http://localhost:3001";

    @Test
    @DisplayName("어드민 경로는 어드민 출처만, 사용자 경로는 사용자 출처만 허용하고 둘 다 credentials를 허용한다")
    void separatesOrigins() {
        CorsConfigurationSource source = source(List.of(ADMIN_ORIGIN));

        CorsConfiguration admin = configurationFor(source, "/api/admin/v1/auth/login");
        CorsConfiguration user = configurationFor(source, "/api/v1/users/me");

        assertThat(admin.getAllowedOrigins()).containsExactly(ADMIN_ORIGIN);
        assertThat(user.getAllowedOrigins()).containsExactly(USER_ORIGIN);
        assertThat(admin.getAllowCredentials()).isTrue();
        assertThat(user.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("어드민 출처가 비어 있으면 어드민 경로에 허용 출처가 없다")
    void emptyAdminOrigins() {
        CorsConfiguration admin = configurationFor(source(List.of()), "/api/admin/v1/auth/login");

        assertThat(admin.getAllowedOrigins()).isNullOrEmpty();
        assertThat(admin.checkOrigin(USER_ORIGIN)).isNull();
        assertThat(admin.checkOrigin(ADMIN_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("어드민 출처 설정이 없으면(null) 빈 목록으로 본다")
    void missingAdminOrigins() {
        CorsConfiguration admin = configurationFor(source(null), "/api/admin/v1/admins/me");

        assertThat(admin.checkOrigin(USER_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("어드민 경로는 장소 수정(PUT)을 허용하고 기존 메서드도 계속 허용한다")
    void adminAllowsPut() {
        CorsConfiguration admin = configurationFor(source(List.of(ADMIN_ORIGIN)),
                "/api/admin/v1/places/" + UUID.randomUUID());

        assertThat(admin.checkHttpMethod(HttpMethod.PUT)).isNotNull();
        assertThat(admin.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("사용자 경로는 PUT을 허용하지 않고 허용 메서드가 그대로다")
    void userDisallowsPut() {
        CorsConfiguration user = configurationFor(source(List.of(ADMIN_ORIGIN)), "/api/v1/users/me");

        assertThat(user.checkHttpMethod(HttpMethod.PUT)).isNull();
        assertThat(user.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }

    private CorsConfigurationSource source(List<String> adminOrigins) {
        return new CorsConfig(new CorsProperties(List.of(USER_ORIGIN), adminOrigins)).corsConfigurationSource();
    }

    private CorsConfiguration configurationFor(CorsConfigurationSource source, String path) {
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
        assertThat(configuration).isNotNull();
        return configuration;
    }
}
