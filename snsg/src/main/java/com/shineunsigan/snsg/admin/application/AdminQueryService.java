package com.shineunsigan.snsg.admin.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.admin.application.dto.result.AdminCredentialResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminPageResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminSummaryResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.model.AdminCursor;
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
    private final AdminProperties adminProperties;

    /** 로그인 판정용. 상태와 상관없이 삭제되지 않은 계정을 찾는다. */
    public Optional<AdminCredentialResult> findCredential(String loginId) {
        return adminRepository.findByLoginId(loginId).map(AdminCredentialResult::from);
    }

    /** 인증 필터용. 승인된 어드민이면 역할을, 아니면(없음·삭제·승인 대기·거절·이용 중지) 빈 값을 준다. */
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

    /**
     * 어드민 목록(MASTER 전용). 삭제되지 않은 모든 어드민(MASTER 포함)을 가입 신청 시각, id 순으로 준다.
     * 한 페이지보다 한 건 더 읽어 다음 페이지가 있는지 정한다.
     *
     * @param status 이 상태만 준다. {@code null}이면 모든 상태
     * @param cursor 앞 페이지의 nextCursor. 없으면 처음부터. 해석할 수 없으면 INVALID_CURSOR
     * @param size   생략하면 기본 개수, 최대를 넘으면 최대, 1보다 작으면 1이다
     */
    public AdminPageResult list(AdminStatus status, String cursor, Integer size) {
        AdminCursor after = cursor == null || cursor.isEmpty() ? null : AdminCursor.decode(cursor);
        int pageSize = pageSize(size);
        List<Admin> admins = adminRepository.findAll(status, after, pageSize + 1);
        boolean hasNext = admins.size() > pageSize;
        List<Admin> page = hasNext ? admins.subList(0, pageSize) : admins;
        return new AdminPageResult(page.stream().map(AdminSummaryResult::from).toList(),
                hasNext ? nextCursor(page.get(pageSize - 1)) : null);
    }

    private int pageSize(Integer requested) {
        int size = requested == null ? adminProperties.pageSizeDefault() : requested;
        return Math.max(1, Math.min(size, adminProperties.pageSizeMax()));
    }

    private static String nextCursor(Admin last) {
        return new AdminCursor(last.getCreatedAt(), last.getId()).encode();
    }

    private Optional<Admin> findApproved(UUID adminId) {
        return adminRepository.findById(adminId).filter(Admin::isApproved);
    }
}
