package com.shineunsigan.snsg.user.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 비식별 대상 목록의 커서. 앞 페이지 마지막 대상의 (비식별 예정 시각, 사용자 id)이고,
 * 다음 페이지는 이 값보다 뒤의 대상이다.
 */
public record AnonymizeCursor(LocalDateTime anonymizeDueAt, UUID userId) {
}
