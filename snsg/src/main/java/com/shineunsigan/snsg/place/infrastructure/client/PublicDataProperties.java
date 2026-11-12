package com.shineunsigan.snsg.place.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 연동.
 *
 * @param apiKey 일반 인증키(Decoding) 원문. 요청할 때 adapter가 인코딩한다
 */
@ConfigurationProperties(prefix = "public-data")
public record PublicDataProperties(String apiKey) {
}
