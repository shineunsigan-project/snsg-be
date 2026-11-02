package com.shineunsigan.snsg.user.infrastructure.persistence;

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
     */
    @Query("""
            select count(u) > 0 from User u
            where lower(u.profile.nickname) = lower(:nickname) and u.isDeleted = false""")
    boolean existsByNicknameIgnoreCase(@Param("nickname") String nickname);
}
