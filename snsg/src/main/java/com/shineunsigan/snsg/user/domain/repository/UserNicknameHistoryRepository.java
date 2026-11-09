package com.shineunsigan.snsg.user.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;

/**
 * 조회는 삭제되지 않은 행만 본다.
 */
public interface UserNicknameHistoryRepository {

    UserNicknameHistory save(UserNicknameHistory history);

    /**
     * 사용자가 마지막으로 닉네임을 바꾼 시각. 가장 최근 {@code CHANGED} 이력의 {@code createdAt}이다.
     * 바꾼 적이 없으면 비어 있다.
     */
    Optional<LocalDateTime> findLastChangedAt(UUID userId);
}
