package com.shineunsigan.snsg.admin.application.dto.result;

import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.model.Admin;

/**
 * 로그인 판정에 쓰는 값. application 밖으로 나가지 않는다.
 *
 * @param status 계정 상태. 비밀번호가 맞은 뒤에만 본다
 */
public record AdminCredentialResult(UUID id, String passwordHash, AdminStatus status) {

    public static AdminCredentialResult from(Admin admin) {
        return new AdminCredentialResult(admin.getId(), admin.getPasswordHash(), admin.getStatus());
    }
}
