package com.shineunsigan.snsg.user.application;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.user.application.port.in.UploadProfileImageUseCase;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.domain.model.ProfileImage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadProfileImageFacade implements UploadProfileImageUseCase {

    private final FileStoragePort fileStoragePort;

    /**
     * 사진은 바이트를 보고 형식을 판별한다. 클라이언트가 보낸 확장자나 Content-Type은 믿지 않는다.
     * 저장만 하므로 트랜잭션을 걸지 않는다.
     */
    @Override
    public String upload(byte[] content) {
        ProfileImage image = ProfileImage.detect(content);
        return fileStoragePort.store(content, image.extension());
    }
}
