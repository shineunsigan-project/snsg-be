package com.shineunsigan.snsg.user.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;

/**
 * 조회는 삭제되지 않은 행만 본다. 삭제된 행까지 봐야 하는 메서드는 이름에 드러낸다.
 */
public interface UserNicknameHistoryRepository {

    UserNicknameHistory save(UserNicknameHistory history);

    /**
     * 사용자가 마지막으로 닉네임을 바꾼 시각. 가장 최근 {@code CHANGED} 이력의 {@code createdAt}이다.
     * 바꾼 적이 없으면 비어 있다.
     */
    Optional<LocalDateTime> findLastChangedAt(UUID userId);

    /**
     * 사용자의 닉네임 이력을 삭제된 행까지 모두 읽는다. 탈퇴 회원 비식별에만 쓴다.
     * 닉네임 판정에는 쓰지 않는다. 삭제된 이력은 보호하지 않는다.
     */
    List<UserNicknameHistory> findAllByUserIdIncludingDeleted(UUID userId);
}
