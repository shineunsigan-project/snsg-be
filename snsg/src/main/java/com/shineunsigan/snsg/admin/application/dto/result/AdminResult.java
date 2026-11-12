package com.shineunsigan.snsg.admin.application.dto.result;

import java.time.LocalDate;
import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.model.Admin;

/**
 * 어드민 정보. 비밀번호 해시와 상태는 담지 않는다.
 */
public record AdminResult(UUID id, String loginId, String name, AdminTeam team, LocalDate birthDate, AdminRole role) {

    public static AdminResult from(Admin admin) {
        return new AdminResult(admin.getId(), admin.getLoginId(), admin.getName(), admin.getTeam(),
                admin.getBirthDate(), admin.getRole());
    }
}
