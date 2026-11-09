package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserNicknameHistoryRepositoryImpl implements UserNicknameHistoryRepository {

    private final UserNicknameHistoryJpaRepository userNicknameHistoryJpaRepository;

    @Override
    public UserNicknameHistory save(UserNicknameHistory history) {
        return userNicknameHistoryJpaRepository.save(history);
    }

    @Override
    public Optional<LocalDateTime> findLastChangedAt(UUID userId) {
        return userNicknameHistoryJpaRepository.findLastCreatedAt(userId, NicknameHistoryReason.CHANGED);
    }
}
