package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 참여 전 활성 사용자 확인과, 실패하면 참여로 넘어가지 않는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class JoinScheduleFacadeTest {

    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserPort userPort;

    @Mock
    private ScheduleCommandService scheduleCommandService;

    @InjectMocks
    private JoinScheduleFacade joinScheduleFacade;

    @Test
    @DisplayName("탈퇴한 사용자면 USER_NOT_ACTIVE이고 참여를 부르지 않는다")
    void inactiveUser() {
        when(userPort.isActive(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> joinScheduleFacade.join(SCHEDULE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ScheduleErrorCode.USER_NOT_ACTIVE);
        verifyNoInteractions(scheduleCommandService);
    }

    @Test
    @DisplayName("활성 사용자면 요청한 일정 id와 사용자 id로 참여를 부른다")
    void activeUserJoins() {
        when(userPort.isActive(USER_ID)).thenReturn(true);

        joinScheduleFacade.join(SCHEDULE_ID, USER_ID);

        verify(scheduleCommandService).join(SCHEDULE_ID, USER_ID);
    }
}
