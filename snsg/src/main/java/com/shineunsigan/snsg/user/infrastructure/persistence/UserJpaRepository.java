package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.user.domain.model.User;

interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByProfileNicknameAndIsDeletedFalse(String nickname);
}
