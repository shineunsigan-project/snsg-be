package com.shineunsigan.snsg.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저에서 호출을 허용할 출처. 쿠키를 주고받으므로 {@code *}를 쓸 수 없고 정확히 적어야 한다.
 *
 * @param allowedOrigins      사용자 앱 출처. 사용자 경로에만 쓴다
 * @param adminAllowedOrigins 어드민 앱 출처. 어드민 경로에만 쓴다. 비어 있으면 어드민 경로를 브라우저에서 부를 수 없다
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins, List<String> adminAllowedOrigins) {

    public CorsProperties {
        adminAllowedOrigins = adminAllowedOrigins == null ? List.of() : adminAllowedOrigins;
    }
}
