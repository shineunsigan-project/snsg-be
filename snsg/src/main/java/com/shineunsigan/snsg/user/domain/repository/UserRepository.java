package com.shineunsigan.snsg.user.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.User;

/**
 * 조회는 삭제되지 않은 행만 본다. 삭제된 행까지 봐야 하는 메서드는 이름에 드러낸다.
 */
public interface UserRepository {

    /**
     * 바로 DB에 반영한다. 닉네임 유니크 인덱스를 위반하면(동시 가입 경합)
     * {@code BusinessException(DUPLICATED_NICKNAME)}을 던진다.
     */
    User save(User user);

    Optional<User> findById(UUID id);

    /**
     * 대소문자를 무시하고 비교한다. 탈퇴한 사용자의 닉네임은 보지 않는다.
     */
    boolean existsByNickname(String nickname);
}
