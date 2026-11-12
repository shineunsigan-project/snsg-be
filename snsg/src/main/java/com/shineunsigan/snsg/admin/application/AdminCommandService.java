package com.shineunsigan.snsg.admin.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.admin.application.dto.command.SignUpAdminCommand;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCommandService {

    private final AdminRepository adminRepository;

    /**
     * 가입 신청을 저장한다. 역할은 ADMIN, 상태는 승인 대기다. 생년월일은 오늘(한국 시간) 기준으로 본다.
     * 아이디가 이미 쓰이면 저장하지 않는다. 동시 가입으로 인덱스를 위반해도 같은 409가 된다.
     */
    public void signUp(SignUpAdminCommand command, String passwordHash) {
        Admin admin = Admin.signUp(command.loginId(), passwordHash, command.name(), command.team(),
                command.birthDate(), LocalDate.now());
        validateLoginIdNotTaken(admin.getLoginId());
        adminRepository.save(admin);
    }

    private void validateLoginIdNotTaken(String loginId) {
        if (adminRepository.existsByLoginId(loginId)) {
            throw new BusinessException(AdminErrorCode.DUPLICATED_LOGIN_ID);
        }
    }
}
