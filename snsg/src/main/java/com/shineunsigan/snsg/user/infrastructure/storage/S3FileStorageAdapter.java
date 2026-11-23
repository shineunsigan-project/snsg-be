package com.shineunsigan.snsg.user.infrastructure.storage;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.ProfileImage;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3에 보관한다(S3 모드, {@code file-storage.type=s3}). 사진은 CloudFront로 내려준다.
 * <p>
 * 돌려주는 경로와 DB 값은 디스크 저장과 같은 {@code /images/profiles/<uuid>.<ext>}다. S3 키는 그 경로에서 앞 슬래시를 뺀
 * {@code images/profiles/<uuid>.<ext>}라 CloudFront 주소의 경로와 같다. 파일명은 난수로 짓는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file-storage.type", havingValue = "s3")
public class S3FileStorageAdapter implements FileStoragePort {

    private static final String KEY_PREFIX = "images/profiles/";

    /**
     * 하루 동안 CloudFront·브라우저가 재사용한다. 파일명이 난수라 내용은 바뀌지 않지만, 지운 얼굴 사진이 캐시에 남는 기간을
     * 줄이려고 짧게 두고 {@code immutable}을 붙이지 않는다.
     */
    private static final String CACHE_CONTROL = "public, max-age=86400";

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorageAdapter(S3Client s3Client, S3StorageProperties s3StorageProperties) {
        this.s3Client = s3Client;
        this.bucket = s3StorageProperties.bucket();
    }

    @Override
    public String store(byte[] content, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        String key = KEY_PREFIX + fileName;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType(extension))
                .cacheControl(CACHE_CONTROL)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            // BusinessException은 원인을 싣지 않고 전역 처리도 로그를 남기지 않아 여기서 남긴다.
            // 사용자에게 500이 나가고 업로드가 막힌 상태라 error다(삭제 실패는 끝난 요청의 뒷정리라 warn).
            log.error("프로필 사진을 S3에 올리지 못했다. bucket={}, key={}", bucket, key, exception);
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_STORE_FAILED);
        }
        return ProfileImage.UPLOADED_PATH_PREFIX + fileName;
    }

    /**
     * 탈퇴한 사용자의 사진과 다른 사진으로 바꾼 이전 사진을 지운다.
     * <p>
     * 업로드 API가 만든 형태({@link ProfileImage#isUploadedPath})가 아니면 S3를 부르지 않는다. 사용자가 보낸 값으로
     * 우리 접두사 밖의 객체를 지울 수 없게 하기 위해서다. 그 위에 파일명만 떼어내 우리 접두사 아래 키로 지운다.
     */
    @Override
    public void delete(String path) {
        if (!ProfileImage.isUploadedPath(path)) {
            return;
        }
        String fileName = path.substring(ProfileImage.UPLOADED_PATH_PREFIX.length());
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(KEY_PREFIX + fileName)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            // 지우지 못해도 탈퇴·사진 교체는 이미 끝났다. 남은 객체는 DB에서 참조되지 않는다.
            log.warn("프로필 사진을 지우지 못했다. path={}", path, exception);
        }
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            default -> throw new IllegalArgumentException("프로필 사진으로 허용하지 않는 확장자다: " + extension);
        };
    }
}
