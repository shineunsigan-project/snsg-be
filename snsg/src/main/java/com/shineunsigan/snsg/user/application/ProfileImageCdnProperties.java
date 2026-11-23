package com.shineunsigan.snsg.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드한 프로필 사진을 내려주는 CloudFront 주소. S3 모드에서 사진 경로 요청을 이 주소로 리다이렉트한다.
 * <p>
 * 리다이렉트 주소를 만드는 {@code UserQueryService}(application)와 S3 모드에서 빈 값을 거부하는
 * {@code S3StorageConfig}(infrastructure)가 함께 읽어 application에 둔다.
 * <p>
 * 검증을 달지 않는다. {@code @ConfigurationPropertiesScan}이 모든 모드에서 바인딩하므로, 값이 없는 local에서도 떠야 한다.
 * 빈 값 거부는 S3 모드에서만 만들어지는 {@code S3StorageConfig}가 한다.
 *
 * @param url 예: {@code https://dxxxx.cloudfront.net}. 끝 슬래시 없이 둔다
 */
@ConfigurationProperties(prefix = "file-storage.cloudfront")
public record ProfileImageCdnProperties(String url) {
}
