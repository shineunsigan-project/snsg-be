package com.shineunsigan.snsg.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저에서 호출을 허용할 출처. 쿠키를 주고받으므로 {@code *}를 쓸 수 없고 정확히 적어야 한다.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
