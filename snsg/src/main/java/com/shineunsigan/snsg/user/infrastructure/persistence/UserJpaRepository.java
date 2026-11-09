package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shineunsigan.snsg.user.domain.model.User;

interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndIsDeletedFalse(UUID id);

    /**
     * 유니크 인덱스({@code LOWER(nickname)})와 같은 기준으로 비교한다.
     * 닉네임 이력은 엔티티로 매핑하지 않았으므로 native 쿼리로 함께 본다.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM users
                           WHERE LOWER(nickname) = LOWER(:nickname) AND is_deleted = FALSE)
                OR EXISTS (SELECT 1 FROM user_nickname_histories
                           WHERE LOWER(nickname) = LOWER(:nickname) AND reserved_until > :now AND is_deleted = FALSE)""",
            nativeQuery = true)
    boolean isNicknameTaken(@Param("nickname") String nickname, @Param("now") LocalDateTime now);
}
