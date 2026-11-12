package com.shineunsigan.snsg.admin.application.dto.result;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.model.Admin;

/**
 * 어드민 목록의 한 항목. 비밀번호 해시와 감사 컬럼(createdBy·updatedBy)은 담지 않는다.
 *
 * @param createdAt 가입 신청 시각(한국 시간)
 */
public record AdminSummaryResult(UUID id, String loginId, String name, AdminTeam team, LocalDate birthDate,
        AdminRole role, AdminStatus status, LocalDateTime createdAt) {

    public static AdminSummaryResult from(Admin admin) {
        return new AdminSummaryResult(admin.getId(), admin.getLoginId(), admin.getName(), admin.getTeam(),
                admin.getBirthDate(), admin.getRole(), admin.getStatus(), admin.getCreatedAt());
    }
}
