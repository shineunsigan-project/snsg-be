package com.shineunsigan.snsg.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.shineunsigan.snsg.user.application.ProfileImageCdnProperties;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.presentation.ProfileImageRedirectController;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * 저장소 모드({@code file-storage.type})에 따라 한쪽 빈만 만들어지는지, S3 모드에서 버킷·CloudFront 주소가 비면
 * 기동이 실패하는지 본다. 같은 {@code /images/profiles/**}를 디스크 핸들러와 리다이렉트 컨트롤러가 함께 잡으면 안 된다.
 * <p>
 * 앱 전체 대신 저장소 관련 클래스만 올린다. 설정 record는 앱의 {@code @ConfigurationPropertiesScan}처럼 모든 모드에서
 * 등록한다. S3Client는 만들기만 하고 요청을 보내지 않는다.
 */
class S3StorageConfigTest {

    private static final String BUCKET = "file-storage.s3.bucket=snsg-test-bucket";
    private static final String CLOUDFRONT_URL = "file-storage.cloudfront.url=https://cdn.example.test";
    private static final String REGION = "file-storage.s3.region=ap-northeast-2";
    /** 그 키를 넣지 않은 경우. 파라미터가 속성 문자열 하나라, 저장소와 관계없는 키를 대신 넣는다. */
    private static final String NOT_SET = "file-storage.not-set=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ScannedProperties.class, LocalFileStorageAdapter.class,
                    ProfileImageResourceConfig.class, S3StorageConfig.class, S3FileStorageAdapter.class,
                    ProfileImageRedirectController.class)
            .withBean(UserQueryService.class, () -> mock(UserQueryService.class))
            .withPropertyValues("file-storage.s3.connect-timeout=2s", "file-storage.s3.socket-timeout=5s",
                    "file-storage.s3.api-call-timeout=7s");

    @Test
    @DisplayName("local 모드면 디스크 저장과 디스크 핸들러만 있고 S3·리다이렉트 빈은 없다")
    void localMode() {
        runner.withPropertyValues("file-storage.type=local", "file-storage.directory=build/test-files", REGION,
                        BUCKET, CLOUDFRONT_URL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(FileStoragePort.class)).hasSize(1)
                            .allSatisfy((name, bean) -> assertThat(bean).isInstanceOf(LocalFileStorageAdapter.class));
                    assertThat(context).hasSingleBean(ProfileImageResourceConfig.class)
                            .doesNotHaveBean(ProfileImageRedirectController.class)
                            .doesNotHaveBean(S3FileStorageAdapter.class)
                            .doesNotHaveBean(S3Client.class);
                });
    }

    @Test
    @DisplayName("local 모드면 버킷·CloudFront 주소가 빈 값이어도 뜬다")
    void localModeWithoutS3Settings() {
        runner.withPropertyValues("file-storage.type=local", "file-storage.directory=build/test-files", REGION,
                        "file-storage.s3.bucket=", "file-storage.cloudfront.url=")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(LocalFileStorageAdapter.class));
    }

    @Test
    @DisplayName("s3 모드면 S3 저장과 리다이렉트 컨트롤러만 있고 디스크 저장·디스크 핸들러는 없다. S3Client에 호출 전체 시간 초과가 걸린다")
    void s3Mode() {
        runner.withPropertyValues("file-storage.type=s3", REGION, BUCKET, CLOUDFRONT_URL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(FileStoragePort.class)).hasSize(1)
                            .allSatisfy((name, bean) -> assertThat(bean).isInstanceOf(S3FileStorageAdapter.class));
                    assertThat(context).hasSingleBean(ProfileImageRedirectController.class)
                            .hasSingleBean(S3Client.class)
                            .doesNotHaveBean(ProfileImageResourceConfig.class)
                            .doesNotHaveBean(LocalFileStorageAdapter.class);
                    assertThat(context.getBean(S3Client.class).serviceClientConfiguration().overrideConfiguration()
                            .apiCallTimeout()).contains(Duration.ofSeconds(7));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"file-storage.directory=${FILE_STORAGE_DIRECTORY}", NOT_SET})
    @DisplayName("s3 모드면 디스크 디렉터리가 없거나 자리표시자 글자 그대로여도 뜨고 디스크 쪽 빈은 없다")
    void s3ModeIgnoresDirectory(String directory) {
        runner.withPropertyValues("file-storage.type=s3", REGION, BUCKET, CLOUDFRONT_URL, directory)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(LocalFileStorageAdapter.class)
                        .doesNotHaveBean(ProfileImageResourceConfig.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file-storage.s3.bucket=", NOT_SET})
    @DisplayName("s3 모드에서 버킷이 없거나 비어 있으면 기동이 실패하고 메시지에 키 이름이 있다")
    void s3ModeRequiresBucket(String bucket) {
        runner.withPropertyValues("file-storage.type=s3", REGION, CLOUDFRONT_URL, bucket)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("FILE_STORAGE_S3_BUCKET"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file-storage.cloudfront.url=", NOT_SET})
    @DisplayName("s3 모드에서 CloudFront 주소가 없거나 비어 있으면 기동이 실패하고 메시지에 키 이름이 있다")
    void s3ModeRequiresCloudfrontUrl(String cloudfrontUrl) {
        runner.withPropertyValues("file-storage.type=s3", REGION, BUCKET, cloudfrontUrl)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("FILE_STORAGE_CLOUDFRONT_URL"));
    }

    /** 앱의 {@code @ConfigurationPropertiesScan}처럼 모드와 상관없이 record를 등록한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({FileStorageProperties.class, S3StorageProperties.class,
            ProfileImageCdnProperties.class})
    static class ScannedProperties {
    }
}
