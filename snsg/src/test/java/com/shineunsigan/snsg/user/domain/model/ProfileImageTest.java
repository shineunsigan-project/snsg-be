package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
