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

class LatestScheduleCursorTest {

    @Test
    @DisplayName("만든 커서를 다시 읽으면 같은 (개설 시각, id)이고, 문자열은 URL-safe 문자만 쓴다")
    void encodeAndDecode() {
        LatestScheduleCursor cursor = new LatestScheduleCursor(LocalDateTime.of(2026, 11, 14, 19, 30, 15, 123_456_000),
                UUID.fromString("ffffffff-ffff-7fff-bfff-ffffffffffff"));

        String encoded = cursor.encode();

        assertThat(encoded).matches("^[A-Za-z0-9_-]+$");
        assertThat(LatestScheduleCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    @DisplayName("시작 일시 순·마감 임박 순의 커서는 최근 열린 순 커서로 해석하지 않는다(INVALID_CURSOR)")
    void otherSortCursors() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 14, 19, 30);
        UUID id = UUID.randomUUID();

        assertInvalidCursor(new ScheduleCursor(at, id).encode());
        assertInvalidCursor(new ClosingSoonScheduleCursor(3, at, id).encode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LATEST|2026-11-14T19:30",
            "LATEST|2026-11-14T19:30|not-a-uuid",
            "LATEST|not-a-date|01a1c3d4-0000-7000-8000-000000000001",
            "LATEST|2026-11-14T19:30|01a1c3d4-0000-7000-8000-000000000001|x",
            "OTHER|2026-11-14T19:30|01a1c3d4-0000-7000-8000-000000000001"})
    @DisplayName("표식이 다르거나 항목 수가 다르거나 날짜·UUID 형식이 아니면 INVALID_CURSOR")
    void malformedContent(String raw) {
        assertInvalidCursor(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Base64가 아닌 문자열이면 INVALID_CURSOR")
    void notBase64() {
        assertInvalidCursor("not base64!");
    }

    private static void assertInvalidCursor(String value) {
        assertThatThrownBy(() -> LatestScheduleCursor.decode(value))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ScheduleErrorCode.INVALID_CURSOR);
    }
}
