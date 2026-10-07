package com.shineunsigan.snsg.user.domain.model;

import java.util.Arrays;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

/**
 * 프로필 사진으로 허용하는 이미지 형식. 파일 앞부분의 바이트(매직 넘버)로 판별한다.
 * 클라이언트가 보낸 Content-Type이나 확장자는 믿지 않는다. 바꿔서 보낼 수 있기 때문이다.
 */
public enum ProfileImage {

    PNG("png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}),
    JPEG("jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    WEBP("webp", new byte[] {'R', 'I', 'F', 'F'});

    /** 5MB. 프로필 사진에 그 이상은 필요 없다. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final String extension;
    private final byte[] signature;

    ProfileImage(String extension, byte[] signature) {
        this.extension = extension;
        this.signature = signature;
    }

    public String extension() {
        return extension;
    }

    public static ProfileImage detect(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }
        return Arrays.stream(values())
                .filter(image -> image.matches(content))
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE));
    }

    private boolean matches(byte[] content) {
        if (content.length < signature.length) {
            return false;
        }
        return Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }
}
