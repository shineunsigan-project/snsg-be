package com.shineunsigan.snsg.admin.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 어드민 목록의 커서. 앞 페이지 마지막 어드민의 (가입 신청 시각, id)이고, 다음 페이지는 이 값보다 뒤의 어드민이다.
 * <p>
 * 클라이언트에는 URL-safe Base64(패딩 없음) 문자열로 준다. 문자가 {@code [A-Za-z0-9_-]}뿐이라 쿼리에 그대로 붙인다.
 * 클라이언트는 내용을 해석하지 않는다. 일정 목록 커서와 같은 형식이지만 다른 도메인 클래스라 따로 둔다.
 */
public record AdminCursor(LocalDateTime createdAt, UUID id) {

    private static final char SEPARATOR = '|';

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 해석할 수 없으면(Base64 아님, 구분자 없음, 날짜·UUID 형식 아님) INVALID_CURSOR다. */
    public static AdminCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw new BusinessException(AdminErrorCode.INVALID_CURSOR);
            }
            return new AdminCursor(LocalDateTime.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(AdminErrorCode.INVALID_CURSOR);
        }
    }
}
