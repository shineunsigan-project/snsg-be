package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;

interface UserNicknameHistoryJpaRepository extends JpaRepository<UserNicknameHistory, UUID> {

    @Query("""
            SELECT MAX(h.createdAt) FROM UserNicknameHistory h
            WHERE h.user.id = :userId AND h.reason = :reason AND h.isDeleted = false""")
    Optional<LocalDateTime> findLastCreatedAt(@Param("userId") UUID userId,
            @Param("reason") NicknameHistoryReason reason);

    List<UserNicknameHistory> findByUserId(UUID userId);
}
