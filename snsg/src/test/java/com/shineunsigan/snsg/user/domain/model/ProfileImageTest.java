package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

class ProfileImageTest {

    @Test
    @DisplayName("JPG와 PNG는 바이트로 판별해 받는다")
    void detectsJpegAndPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        assertThat(ProfileImage.detect(png)).isEqualTo(ProfileImage.PNG);
        assertThat(ProfileImage.detect(jpeg)).isEqualTo(ProfileImage.JPEG);
    }

    @Test
    @DisplayName("WebP(RIFF 시그니처)는 INVALID_PROFILE_IMAGE")
    void rejectsWebp() {
        byte[] webp = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

        assertThatThrownBy(() -> ProfileImage.detect(webp))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.jpg"})
    @DisplayName("접두사 + 소문자 UUID + png·jpg는 업로드 경로다")
    void uploadedPath(String path) {
        assertThat(ProfileImage.isUploadedPath(path)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "/images/profiles/..",
            "/images/profiles/",
            "/images/profiles/../0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png",
            "/images/profiles/a/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.jpeg",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.gif",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.PNG",
            "/images/profiles/0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9.png",
            "/images/profiles/a.png",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png?x=1",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png\n",
            "/images/default-profile.png",
            "https://kakao.test/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png"})
    @DisplayName("하위 경로·..·빈 파일명·다른 확장자·대문자·UUID가 아닌 이름·뒤에 붙은 문자는 업로드 경로가 아니다")
    void notUploadedPath(String path) {
        assertThat(ProfileImage.isUploadedPath(path)).isFalse();
    }
}
