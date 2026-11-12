package com.shineunsigan.snsg.admin.application;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.admin.application.dto.command.SignUpAdminCommand;
import com.shineunsigan.snsg.admin.application.port.out.PasswordHashPort;
import com.shineunsigan.snsg.admin.domain.model.Admin;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 가입 신청. 비밀번호 해시는 포트가 만들고 저장은 서비스가 한다.
 * 토큰을 주지 않는다. MASTER가 승인해야 로그인할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SignUpAdminFacade {

    private final PasswordHashPort passwordHashPort;
    private final AdminCommandService adminCommandService;

    /**
     * 비밀번호 형식을 먼저 본다. 틀린 비밀번호로는 해시를 만들지 않는다.
     */
    public void signUp(SignUpAdminCommand command) {
        Admin.validatePassword(command.password());
        String passwordHash = passwordHashPort.hash(command.password());
        adminCommandService.signUp(command, passwordHash);
    }
}
