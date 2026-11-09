package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;

interface UserWithdrawalJpaRepository extends JpaRepository<UserWithdrawal, UUID> {

    Optional<UserWithdrawal> findByUserIdAndIsDeletedFalse(UUID userId);
}
