package com.shineunsigan.snsg.user.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shineunsigan.snsg.user.application.ProfileImageCdnProperties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 모드({@code file-storage.type=s3})의 S3 클라이언트.
 * <p>
 * 버킷과 CloudFront 주소가 비어 있으면 기동을 멈춘다. 두 record는 모든 모드에서 바인딩되므로 record가 아니라
 * S3 모드에서만 만들어지는 이 설정에서 거부한다. 빈 기본값({@code ${...:}})을 두어 자리표시자가 글자 그대로
 * 바인딩되지 않게 했으므로, 운영에서 환경변수가 빠지면 빈 값으로 들어와 여기서 걸린다.
 * <p>
 * 자격 증명은 AWS SDK 기본 체인으로 얻는다. 운영은 ECS task role이다. 키를 설정으로 받지 않는다.
 * <p>
 * 시간 초과(연결·응답·호출 전체)를 여기서 건다. 재시도 정책은 SDK 기본값이다.
 */
@Configuration
@ConditionalOnProperty(name = "file-storage.type", havingValue = "s3")
public class S3StorageConfig {

    public S3StorageConfig(S3StorageProperties s3StorageProperties, ProfileImageCdnProperties profileImageCdnProperties) {
        requireValue(s3StorageProperties.bucket(), "FILE_STORAGE_S3_BUCKET(file-storage.s3.bucket)");
        requireValue(profileImageCdnProperties.url(), "FILE_STORAGE_CLOUDFRONT_URL(file-storage.cloudfront.url)");
    }

    @Bean
    public S3Client s3Client(S3StorageProperties s3StorageProperties) {
        return S3Client.builder()
                .region(Region.of(s3StorageProperties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(s3StorageProperties.connectTimeout())
                        .socketTimeout(s3StorageProperties.socketTimeout()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(s3StorageProperties.apiCallTimeout())
                        .build())
                .build();
    }

    /** 메시지에는 키 이름만 넣고 값은 넣지 않는다. */
    private static void requireValue(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("S3 저장소 설정이 비어 있다: " + key);
        }
    }
}
