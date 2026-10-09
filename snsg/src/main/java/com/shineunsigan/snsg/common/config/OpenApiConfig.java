package com.shineunsigan.snsg.common.config;

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
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

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
}
