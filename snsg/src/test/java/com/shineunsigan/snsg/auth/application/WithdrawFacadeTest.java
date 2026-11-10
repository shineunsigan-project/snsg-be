package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;
import com.shineunsigan.snsg.auth.application.port.out.SchedulePort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 남은 일정으로 막히면 user 탈퇴를 부르지 않고, 막히지 않으면 그 사용자를 탈퇴시키는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawFacadeTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private SchedulePort schedulePort;

    @Mock
    private UserPort userPort;

    @InjectMocks
    private WithdrawFacade withdrawFacade;

    @Test
    @DisplayName("탈퇴를 막는 일정이 있으면 WITHDRAWAL_BLOCKED이고 user 탈퇴를 부르지 않는다")
    void blockedWithdrawal() {
        when(schedulePort.checkWithdrawable(USER_ID))
                .thenReturn(new WithdrawalEligibilityResult(false, LocalDateTime.of(2027, 3, 13, 19, 0)));

        assertThatThrownBy(() -> withdrawFacade.withdraw(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.WITHDRAWAL_BLOCKED);

        verify(userPort, never()).withdraw(any());
    }

    @Test
    @DisplayName("탈퇴할 수 있으면 그 사용자로 user 탈퇴를 한 번 부른다")
    void withdrawableUser() {
        when(schedulePort.checkWithdrawable(USER_ID)).thenReturn(new WithdrawalEligibilityResult(true, null));

        withdrawFacade.withdraw(USER_ID);

        verify(userPort, times(1)).withdraw(USER_ID);
    }
}
