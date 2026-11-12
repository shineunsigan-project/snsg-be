package com.shineunsigan.snsg.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * 모든 API가 기본적으로 access token을 요구한다고 선언한다.
 * 토큰 없이 부르는 엔드포인트만 메서드에 {@code @SecurityRequirements}를 달아 이 요구를 지운다.
 * <p>
 * 이 선언이 없으면 문서만 보고는 어떤 API에 토큰이 필요한지 알 수 없고, Swagger에 Authorize 버튼도 생기지 않는다.
 * <p>
 * 스펙은 사용자용과 어드민용 두 그룹으로 나눈다. 사용자용에는 어드민 경로가 나오지 않는다.
 * 그룹마다 {@code /v3/api-docs.yaml/{group}}에서 받는다.
 * <p>
 * 일반 사용자는 어드민의 어떤 정보도 볼 수 없어야 하므로, 서버가 내주는 문서(그룹 없는 스펙, swagger-ui, 사용자 그룹)에는
 * 어드민 경로가 나오지 않는다. 그룹 없는 스펙은 {@code springdoc.paths-to-exclude}가 거른다.
 * 어드민 그룹은 {@code openapi.admin-group-enabled=true}일 때만 생긴다. 어느 프로파일에서도 켜지 않고,
 * {@code docs/openapi-admin.yaml}을 만드는 {@code OpenApiSpecTest}만 켠다. FE에는 그 파일로만 전한다.
 */
@Configuration
public class OpenApiConfig {

    public static final String USER_GROUP = "user";
    public static final String ADMIN_GROUP = "admin";

    private static final String BEARER = "bearerAuth";
    private static final String ADMIN_PATHS = "/api/admin/**";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("snsg API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                로그인·가입·재발급 응답 본문의 accessToken을 넣는다. Bearer는 빼고 토큰 값만 넣는다.
                                refresh token은 HttpOnly 쿠키라 여기에 넣지 않는다.""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group(USER_GROUP)
                .pathsToExclude(ADMIN_PATHS)
                .build();
    }

    /**
     * 어드민 스펙은 제목과 bearer 설명이 다르다. 사용자 그룹과 함께 쓰는 객체를 고치지 않도록 새 객체로 바꿔 끼운다.
     */
    @Bean
    @ConditionalOnProperty(name = "openapi.admin-group-enabled", havingValue = "true")
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group(ADMIN_GROUP)
                .pathsToMatch(ADMIN_PATHS)
                .addOpenApiCustomizer(openApi -> openApi
                        .info(new Info().title("snsg admin API").version("v1"))
                        .components(new Components()
                                .schemas(openApi.getComponents().getSchemas())
                                .addSecuritySchemes(BEARER, new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                어드민 로그인·재발급 응답 본문의 accessToken을 넣는다. Bearer는 빼고 토큰 값만 넣는다.
                                                사용자 앱의 토큰은 받지 않는다. refresh token은 HttpOnly 쿠키라 여기에 넣지 않는다."""))))
                .build();
    }
}
