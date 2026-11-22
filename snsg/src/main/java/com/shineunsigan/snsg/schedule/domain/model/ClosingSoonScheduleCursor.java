package com.shineunsigan.snsg.schedule.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 모집 중 목록 마감 임박 순(CLOSING_SOON)의 커서. 앞 페이지 마지막 일정의 (남은 자리, 시작 시각, id)이고,
 * 다음 페이지는 이 값보다 뒤인 일정이다.
 * <p>
 * 원문 앞에 정렬 표식({@value #MARK})을 붙여 {@link ScheduleCursor}와 같은 URL-safe Base64(패딩 없음)로 준다.
 * 표식이 있어 다른 정렬의 커서와 서로 해석되지 않는다.
 */
public record ClosingSoonScheduleCursor(int remainingSeats, LocalDateTime startAt, UUID id) {

    private static final String MARK = "CLOSING_SOON";
    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = MARK + SEPARATOR + remainingSeats + SEPARATOR + startAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 해석할 수 없으면(Base64 아님, 표식이 다름, 항목 수가 다름, 숫자·날짜·UUID 형식 아님) INVALID_CURSOR다. */
    public static ClosingSoonScheduleCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(Pattern.quote(SEPARATOR), -1);
            if (parts.length != 4 || !parts[0].equals(MARK)) {
                throw new BusinessException(ScheduleErrorCode.INVALID_CURSOR);
            }
            return new ClosingSoonScheduleCursor(Integer.parseInt(parts[1]), LocalDateTime.parse(parts[2]),
                    UUID.fromString(parts[3]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(ScheduleErrorCode.INVALID_CURSOR);
        }
    }
}
