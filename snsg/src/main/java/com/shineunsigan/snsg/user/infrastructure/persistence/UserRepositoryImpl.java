package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final String NICKNAME_INDEX = "uix_users_nickname";

    private final UserJpaRepository userJpaRepository;

    /**
     * 인덱스 위반이 이 호출에서 드러나도록 바로 flush한다. 커밋 때 드러나면 500이 된다.
     * 다른 제약 위반은 그대로 던진다.
     */
    @Override
    public User save(User user) {
        try {
            return userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (violatesNicknameIndex(exception)) {
                throw new BusinessException(UserErrorCode.DUPLICATED_NICKNAME);
            }
            throw exception;
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByNicknameIgnoreCase(nickname);
    }

    private boolean violatesNicknameIndex(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && NICKNAME_INDEX.equals(violation.getConstraintName());
    }
}
