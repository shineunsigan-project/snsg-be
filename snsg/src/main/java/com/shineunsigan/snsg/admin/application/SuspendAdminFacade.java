package com.shineunsigan.snsg.admin.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 이용 중지. 상태 변경을 커밋한 뒤 그 어드민의 refresh 세션을 모두 지운다.
 * <p>
 * access token은 필터가 요청마다 상태를 보므로 다음 요청부터 막힌다. 재발급도 상태를 다시 보므로 세션을 지우지 않아도 막히지만,
 * 해제했을 때 중지 전 세션이 되살아나지 않게 지운다. 중지가 예외로 끝나면 세션을 지우지 않는다.
 * 커밋 뒤 세션 삭제가 실패해도 중지는 반영돼 있고, 재발급의 상태 확인이 안전망이다.
 */
@Service
@RequiredArgsConstructor
public class SuspendAdminFacade {

    private final AdminCommandService adminCommandService;
    private final AdminRefreshTokenStorePort adminRefreshTokenStorePort;

    public void suspend(UUID adminId) {
        adminCommandService.suspend(adminId);
        adminRefreshTokenStorePort.deleteAll(adminId);
    }
}
