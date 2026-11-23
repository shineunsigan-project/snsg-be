package com.shineunsigan.snsg.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.ProfileImage;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3에 보내는 요청(버킷·키·Content-Type·Cache-Control)과 돌려주는 경로 형식, 실패 처리를 본다.
 * 삭제는 업로드 경로 형식인 값만 S3를 부르는지 본다. 형식 밖의 값으로 우리 접두사 밖의 객체가 지워지면 안 된다.
 * <p>
 * S3Client는 mock이다. 실제 AWS를 부르지 않는다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class S3FileStorageAdapterTest {

    private static final String BUCKET = "snsg-test-bucket";
    private static final String FILE_NAME = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";

    @Mock
    private S3Client s3Client;

    private S3FileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new S3FileStorageAdapter(s3Client, new S3StorageProperties(BUCKET, "ap-northeast-2",
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    @ParameterizedTest
    @CsvSource({"png, image/png", "jpg, image/jpeg"})
    @DisplayName("저장하면 버킷·images/profiles/ 키·Content-Type·하루 캐시로 올리고, 키와 같은 파일명의 업로드 경로를 준다")
    void storesToS3(String extension, String contentType) {
        String path = adapter.store(new byte[] {1, 2, 3}, extension);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest request = captor.getValue();
        assertThat(ProfileImage.isUploadedPath(path)).isTrue();
        assertThat(path).endsWith("." + extension);
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(path.substring(1)).startsWith("images/profiles/");
        assertThat(request.contentType()).isEqualTo(contentType);
        assertThat(request.cacheControl()).isEqualTo("public, max-age=86400");
    }

    @Test
    @DisplayName("S3 호출이 실패하면 버킷·키·원인을 ERROR 로그로 남기고(파일 내용은 빼고) PROFILE_IMAGE_STORE_FAILED")
    void storeFailure(CapturedOutput output) {
        String content = "secret-image-bytes";
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("unreachable"));

        assertThatThrownBy(() -> adapter.store(content.getBytes(StandardCharsets.UTF_8), "png"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.PROFILE_IMAGE_STORE_FAILED);
        assertThat(output).contains("ERROR")
                .contains("bucket=" + BUCKET)
                .contains("key=images/profiles/")
                .contains(SdkClientException.class.getName())
                .doesNotContain(content);
    }

    @Test
    @DisplayName("업로드 경로 형식이면 같은 파일명의 images/profiles/ 키를 지운다")
    void deletesUploadedPath() {
        adapter.delete("/images/profiles/" + FILE_NAME);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("images/profiles/" + FILE_NAME);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "/images/profiles/..",
            "/images/profiles/",
            "/images/profiles/a.png",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.gif",
            "/images/profiles/../" + FILE_NAME,
            "/images/other/" + FILE_NAME,
            "https://kakao.test/" + FILE_NAME})
    @DisplayName("업로드 경로 형식이 아니면 S3를 부르지 않고 예외도 없다")
    void ignoresOtherPaths(String path) {
        assertThatCode(() -> adapter.delete(path)).doesNotThrowAnyException();

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("삭제가 실패해도 예외를 던지지 않고 경로를 경고 로그로 남긴다")
    void deleteFailure(CapturedOutput output) {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("denied").statusCode(403).build());

        assertThatCode(() -> adapter.delete("/images/profiles/" + FILE_NAME)).doesNotThrowAnyException();
        assertThat(output).contains("WARN").contains("path=/images/profiles/" + FILE_NAME);
    }
}
