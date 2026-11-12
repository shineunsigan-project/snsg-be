package com.shineunsigan.snsg.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

class AdminCursorTest {

    @Test
    @DisplayName("인코딩한 커서를 다시 해석하면 같은 값이고, 문자는 [A-Za-z0-9_-]뿐이다")
    void roundTrip() {
        AdminCursor cursor = new AdminCursor(LocalDateTime.of(2026, 11, 13, 10, 15, 30, 123456000),
                UUID.fromString("0192f3a4-5b6c-7d8e-9f01-23456789abcd"));

        String encoded = cursor.encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(AdminCursor.decode(encoded)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64!", "2026-11-13T10:15:30", "2026-11-13T10:15:30|not-uuid",
            "yesterday|0192f3a4-5b6c-7d8e-9f01-23456789abcd"})
    @DisplayName("해석할 수 없으면 INVALID_CURSOR: Base64 아님, 구분자 없음, UUID 형식 아님, 날짜 형식 아님")
    void invalid(String raw) {
        String value = raw.equals("not base64!") ? raw
                : Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> AdminCursor.decode(value))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AdminErrorCode.INVALID_CURSOR);
    }
}
