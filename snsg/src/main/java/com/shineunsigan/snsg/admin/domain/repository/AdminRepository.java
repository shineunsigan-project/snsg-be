package com.shineunsigan.snsg.admin.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.model.Admin;

/**
 * 조회는 삭제되지 않은 행만 본다.
 */
public interface AdminRepository {

    /**
     * 바로 DB에 반영한다. 아이디 유니크 인덱스를 위반하면(동시 가입 경합)
     * {@code BusinessException(DUPLICATED_LOGIN_ID)}을 던진다.
     */
    Admin save(Admin admin);

    Optional<Admin> findById(UUID id);

    /** 아이디는 입력한 문자열 그대로 비교한다. */
    Optional<Admin> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
