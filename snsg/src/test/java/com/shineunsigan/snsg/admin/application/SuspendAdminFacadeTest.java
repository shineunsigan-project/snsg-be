package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class SuspendAdminFacadeTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock
    private AdminCommandService adminCommandService;

    @Mock
    private AdminRefreshTokenStorePort adminRefreshTokenStorePort;

    @InjectMocks
    private SuspendAdminFacade facade;

    @Test
    @DisplayName("이용 중지가 성공하면 그 어드민의 세션을 모두 한 번 지운다")
    void deletesAllSessionsAfterSuspend() {
        facade.suspend(ADMIN_ID);

        verify(adminCommandService).suspend(ADMIN_ID);
        verify(adminRefreshTokenStorePort, times(1)).deleteAll(ADMIN_ID);
    }

    @ParameterizedTest
    @EnumSource(value = AdminErrorCode.class,
            names = {"ADMIN_NOT_FOUND", "MASTER_NOT_MODIFIABLE", "ADMIN_STATUS_CONFLICT"})
    @DisplayName("이용 중지가 예외로 끝나면 세션을 지우지 않는다")
    void keepsSessionsWhenSuspendFails(AdminErrorCode errorCode) {
        doThrow(new BusinessException(errorCode)).when(adminCommandService).suspend(ADMIN_ID);

        assertThatThrownBy(() -> facade.suspend(ADMIN_ID)).isInstanceOf(BusinessException.class);

        verifyNoInteractions(adminRefreshTokenStorePort);
    }
}
