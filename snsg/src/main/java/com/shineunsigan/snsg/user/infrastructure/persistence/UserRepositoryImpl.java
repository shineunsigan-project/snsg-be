package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByProfileNicknameAndIsDeletedFalse(nickname);
    }
}
