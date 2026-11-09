package com.shineunsigan.snsg.user.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.AnonymizeCursor;
import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;

/**
 * 조회는 삭제되지 않은 행만 본다.
 */
public interface UserWithdrawalRepository {

    UserWithdrawal save(UserWithdrawal withdrawal);

    Optional<UserWithdrawal> findByUserId(UUID userId);

    /**
     * 아직 비식별하지 않았고 비식별 예정 시각이 {@code now} 이전(같음 포함)인 탈퇴 기록.
     * (비식별 예정 시각, 사용자 id) 순서로 {@code after} 다음부터 {@code size}건을 읽는다.
     *
     * @param after 첫 페이지면 {@code null}
     */
    List<UserWithdrawal> findAnonymizeTargets(LocalDateTime now, AnonymizeCursor after, int size);
}
