package com.shineunsigan.snsg.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.User;

/**
 * 조회는 삭제되지 않은 행만 본다. 삭제된 행까지 봐야 하는 메서드는 이름에 드러낸다.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    boolean existsByNickname(String nickname);
}
