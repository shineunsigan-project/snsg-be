package com.shineunsigan.snsg.admin.application;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.admin.application.dto.command.SignUpAdminCommand;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 가입 신청과 MASTER의 상태 변경(승인·거절·이용 중지·해제).
 * <p>
 * 상태 변경은 대상 행을 잠근 뒤 판정한다. 두 MASTER가 같은 대상을 동시에 처리하면 뒤 요청은 앞 커밋을 기다렸다가
 * 바뀐 상태를 보고 ADMIN_STATUS_CONFLICT가 된다. 엔티티를 읽어 바꾸므로 {@code updated_by}에 처리한 MASTER의 adminId가 남는다.
 * 판정 순서는 대상 조회(ADMIN_NOT_FOUND) → MASTER인지(MASTER_NOT_MODIFIABLE) → 전이 가능한지(ADMIN_STATUS_CONFLICT)다.
 */
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

    public void approve(UUID adminId) {
        findForUpdate(adminId).approve();
    }

    public void reject(UUID adminId) {
        findForUpdate(adminId).reject();
    }

    /** 세션 삭제는 하지 않는다. Redis는 트랜잭션 밖에서 {@link SuspendAdminFacade}가 지운다. */
    public void suspend(UUID adminId) {
        findForUpdate(adminId).suspend();
    }

    public void unsuspend(UUID adminId) {
        findForUpdate(adminId).unsuspend();
    }

    private void validateLoginIdNotTaken(String loginId) {
        if (adminRepository.existsByLoginId(loginId)) {
            throw new BusinessException(AdminErrorCode.DUPLICATED_LOGIN_ID);
        }
    }

    /** 이 트랜잭션에서 대상을 처음 읽는 조회다. 잠금 없이 먼저 읽으면 잠근 뒤에도 옛 상태를 본다. */
    private Admin findForUpdate(UUID adminId) {
        return adminRepository.findByIdForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_NOT_FOUND));
    }
}
