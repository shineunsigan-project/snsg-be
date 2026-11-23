package com.shineunsigan.snsg.user.infrastructure.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필 사진을 두는 S3 버킷.
 * <p>
 * 검증을 달지 않는다. {@code @ConfigurationPropertiesScan}이 모든 모드에서 바인딩하므로, 값이 없는 local에서도 떠야 한다.
 * 빈 값 거부는 S3 모드에서만 만들어지는 {@link S3StorageConfig}가 한다.
 *
 * @param bucket 버킷 이름
 * @param region 버킷 리전
 * @param connectTimeout 연결 시간 초과
 * @param socketTimeout 응답(소켓 읽기) 시간 초과
 * @param apiCallTimeout 재시도를 포함한 호출 전체 시간 초과. 사진 삭제는 커밋 뒤 요청 스레드에서 돌아 이 값이 응답 지연의 상한이다
 */
@ConfigurationProperties(prefix = "file-storage.s3")
public record S3StorageProperties(String bucket, String region, Duration connectTimeout, Duration socketTimeout,
                                  Duration apiCallTimeout) {
}
