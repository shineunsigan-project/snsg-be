package com.shineunsigan.snsg.user.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

/**
 * 로컬 디스크에 보관한다. 서버가 한 대인 동안만 쓸 수 있다.
 * 여러 대로 늘리거나 컨테이너를 새로 만들면 파일이 사라지므로, 운영 배포 전에 S3 어댑터로 바꾼다.
 * <p>
 * 파일명은 난수로 짓는다. 원본 이름을 쓰면 경로 조작이나 다른 사람 파일 덮어쓰기가 가능해진다.
 */
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    public static final String URL_PREFIX = "/images/profiles/";

    private final Path directory;

    public LocalFileStorageAdapter(FileStorageProperties properties) {
        this.directory = Path.of(properties.directory()).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content);
        } catch (IOException exception) {
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_STORE_FAILED);
        }
        return URL_PREFIX + fileName;
    }
}
