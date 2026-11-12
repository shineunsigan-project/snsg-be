package com.shineunsigan.snsg.admin.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.model.AdminCursor;

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

    /**
     * 상태를 바꾸기 전에 그 어드민 행을 {@code SELECT … FOR UPDATE}로 잠그고 읽는다. 트랜잭션 안에서 부르고,
     * 그 트랜잭션에서 이 행을 처음 읽는 조회여야 한다. 같은 대상을 동시에 처리하면 뒤 요청은 앞 커밋을 기다린다.
     */
    Optional<Admin> findByIdForUpdate(UUID id);

    /** 아이디는 입력한 문자열 그대로 비교한다. */
    Optional<Admin> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /**
     * 어드민 목록. (가입 신청 시각, id) 오름차순으로 {@code after} 뒤부터 최대 {@code limit}건을 읽는다.
     *
     * @param status 이 상태만 읽는다. {@code null}이면 모든 상태
     * @param after  앞 페이지의 커서. {@code null}이면 처음부터
     */
    List<Admin> findAll(AdminStatus status, AdminCursor after, int limit);
}
