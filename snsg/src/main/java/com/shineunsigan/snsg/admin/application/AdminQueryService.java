package com.shineunsigan.snsg.admin.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.admin.application.dto.result.AdminCredentialResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 어드민은 삭제되지 않았고 승인된 경우에만 인증을 통과한다. 요청마다 여기서 다시 본다.
 * 이용 중지 같은 상태 변경이 access token 만료를 기다리지 않고 바로 반영된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {

    private final AdminRepository adminRepository;

    /** 로그인 판정용. 상태와 상관없이 삭제되지 않은 계정을 찾는다. */
    public Optional<AdminCredentialResult> findCredential(String loginId) {
        return adminRepository.findByLoginId(loginId).map(AdminCredentialResult::from);
    }

    /** 인증 필터용. 승인된 어드민이면 역할을, 아니면(없음·삭제·승인 대기) 빈 값을 준다. */
    public Optional<AdminRole> findApprovedRole(UUID adminId) {
        return findApproved(adminId).map(Admin::getRole);
    }

    public boolean isApproved(UUID adminId) {
        return findApproved(adminId).isPresent();
    }

    /**
     * 내 정보. 인증 필터가 승인된 어드민만 통과시키므로 보통은 있다.
     * 그 사이 승인이 풀렸거나 삭제됐으면 인증이 없는 것과 같이 401이다.
     */
    public AdminResult getMe(UUID adminId) {
        return findApproved(adminId)
                .map(AdminResult::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
    }

    private Optional<Admin> findApproved(UUID adminId) {
        return adminRepository.findById(adminId).filter(Admin::isApproved);
    }
}
