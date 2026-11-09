package com.shineunsigan.snsg.schedule.domain.model;

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

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

class ScheduleCursorTest {

    @Test
    @DisplayName("만든 커서를 다시 읽으면 같은 (시작 시각, id)이고, 문자열은 URL-safe 문자만 쓴다")
    void encodeAndDecode() {
        ScheduleCursor cursor = new ScheduleCursor(LocalDateTime.of(2026, 11, 14, 19, 30),
                UUID.fromString("ffffffff-ffff-7fff-bfff-ffffffffffff"));

        String encoded = cursor.encode();

        assertThat(encoded).matches("^[A-Za-z0-9_-]+$");
        assertThat(ScheduleCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    @DisplayName("초·나노초가 있는 시작 시각도 그대로 되돌아온다")
    void encodeAndDecodeWithNanos() {
        ScheduleCursor cursor = new ScheduleCursor(LocalDateTime.of(2026, 11, 14, 19, 30, 15, 123_456_000),
                UUID.randomUUID());

        assertThat(ScheduleCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64!", "abc+/=="})
    @DisplayName("Base64가 아닌 문자열이면 INVALID_CURSOR")
    void notBase64(String value) {
        assertInvalidCursor(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-14T19:30",
            "2026-11-14T19:30|not-a-uuid",
            "not-a-date|01a1c3d4-0000-7000-8000-000000000001",
            "2026-13-14T19:30|01a1c3d4-0000-7000-8000-000000000001",
            "|"})
    @DisplayName("Base64지만 구분자가 없거나 날짜·UUID 형식이 아니면 INVALID_CURSOR")
    void malformedContent(String raw) {
        assertInvalidCursor(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertInvalidCursor(String value) {
        assertThatThrownBy(() -> ScheduleCursor.decode(value))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ScheduleErrorCode.INVALID_CURSOR);
    }
}
