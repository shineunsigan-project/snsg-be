package com.shineunsigan.snsg.admin.application.dto.command;

import java.time.LocalDate;

import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;

/**
 * 어드민 가입 신청. 역할·상태는 받지 않는다. 가입은 항상 ADMIN·승인 대기다.
 */
public record SignUpAdminCommand(String loginId, String password, String name, AdminTeam team, LocalDate birthDate) {
}
