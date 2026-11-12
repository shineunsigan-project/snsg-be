package com.shineunsigan.snsg.admin.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.admin.application.dto.command.AdminLoginCommand;
import com.shineunsigan.snsg.admin.application.dto.result.AdminCredentialResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.LoginAttemptPort;
import com.shineunsigan.snsg.admin.application.port.out.PasswordHashPort;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 로그인. 잠금 확인 → 아이디로 조회 → 비밀번호 대조 → 실패 횟수 초기화 → 상태 확인 → 발급 순서다.
 * <p>
 * 아이디가 없는 것과 비밀번호가 틀린 것은 같은 오류다. 아이디가 없어도 더미 해시와 대조해 응답 시간으로도
 * 계정이 있는지 드러나지 않게 한다. 승인 대기 여부는 비밀번호가 맞은 뒤에만 알려준다.
 */
@Service
@RequiredArgsConstructor
public class AdminLoginFacade {

    private final LoginAttemptPort loginAttemptPort;
    private final PasswordHashPort passwordHashPort;
    private final AdminQueryService adminQueryService;
    private final AdminTokenIssuer adminTokenIssuer;
    private final AdminProperties adminProperties;

    public AdminTokenResult login(AdminLoginCommand command) {
        String loginId = command.loginId();
        validateNotLocked(loginId);

        Optional<AdminCredentialResult> credential = adminQueryService.findCredential(loginId);
        if (!passwordMatches(command.password(), credential)) {
            throw failure(loginId);
        }
        loginAttemptPort.reset(loginId);

        AdminCredentialResult admin = credential.orElseThrow();
        validateApproved(admin);
        return adminTokenIssuer.issue(admin.id());
    }

    /** 잠긴 동안은 비밀번호를 대조하지 않고 횟수·잠금 시간도 늘리지 않는다. */
    private void validateNotLocked(String loginId) {
        if (loginAttemptPort.isLocked(loginId)) {
            throw new BusinessException(AdminErrorCode.ADMIN_LOGIN_LOCKED);
        }
    }

    private boolean passwordMatches(String rawPassword, Optional<AdminCredentialResult> credential) {
        if (!Admin.isComparablePassword(rawPassword)) {
            return false;
        }
        String passwordHash = credential.map(AdminCredentialResult::passwordHash).orElse(null);
        return passwordHashPort.matches(rawPassword, passwordHash);
    }

    /** 이번 실패로 잠기면 잠김을, 아니면 아이디·비밀번호 불일치를 알린다. */
    private BusinessException failure(String loginId) {
        boolean locked = loginAttemptPort.recordFailure(loginId, adminProperties.loginMaxFailures(),
                adminProperties.loginLockDuration());
        return new BusinessException(
                locked ? AdminErrorCode.ADMIN_LOGIN_LOCKED : AdminErrorCode.INVALID_ADMIN_CREDENTIALS);
    }

    private void validateApproved(AdminCredentialResult admin) {
        if (!admin.approved()) {
            throw new BusinessException(AdminErrorCode.ADMIN_PENDING_APPROVAL);
        }
    }
}
