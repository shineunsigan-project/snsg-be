package com.shineunsigan.snsg.user.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.user.application.UserWithdrawalCommandService;
import com.shineunsigan.snsg.user.application.dto.result.AnonymizeTargetResult;

@ExtendWith(MockitoExtension.class)
class UserAnonymizationSchedulerTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 1, 1, 4, 0);

    @Mock
    private UserWithdrawalCommandService userWithdrawalCommandService;

    @InjectMocks
    private UserAnonymizationScheduler scheduler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static AnonymizeTargetResult target() {
        return new AnonymizeTargetResult(UUID.randomUUID(), DUE);
    }

    @Test
    @DisplayName("가운데 회원의 비식별이 실패해도 나머지 회원은 비식별한다")
    void failureOfOneDoesNotStopOthers() {
        AnonymizeTargetResult first = target();
        AnonymizeTargetResult failing = target();
        AnonymizeTargetResult third = target();
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(first, failing, third));
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), eq(third)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("boom")).when(userWithdrawalCommandService).anonymize(failing.userId());

        scheduler.anonymizeExpiredWithdrawals();

        verify(userWithdrawalCommandService).anonymize(first.userId());
        verify(userWithdrawalCommandService).anonymize(failing.userId());
        verify(userWithdrawalCommandService).anonymize(third.userId());
    }

    @Test
    @DisplayName("두 번째 페이지는 첫 페이지 마지막 행을 커서로 읽고, 빈 페이지에서 멈춘다. 기준 시각은 모든 페이지에 같다")
    void readsPagesWithCursorUntilEmpty() {
        AnonymizeTargetResult a = target();
        AnonymizeTargetResult b = target();
        AnonymizeTargetResult c = target();
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(a, b));
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), eq(b)))
                .thenReturn(List.of(c));
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), eq(c)))
                .thenReturn(List.of());

        scheduler.anonymizeExpiredWithdrawals();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        InOrder order = inOrder(userWithdrawalCommandService);
        order.verify(userWithdrawalCommandService).findAnonymizeTargets(now.capture(), isNull());
        order.verify(userWithdrawalCommandService).anonymize(a.userId());
        order.verify(userWithdrawalCommandService).anonymize(b.userId());
        order.verify(userWithdrawalCommandService).findAnonymizeTargets(now.capture(), eq(b));
        order.verify(userWithdrawalCommandService).anonymize(c.userId());
        order.verify(userWithdrawalCommandService).findAnonymizeTargets(now.capture(), eq(c));
        order.verifyNoMoreInteractions();
        assertThat(now.getAllValues()).containsOnly(now.getAllValues().get(0));
    }

    @Test
    @DisplayName("비식별하는 동안 인증 주체가 SYSTEM_SCHEDULER이고, 실패가 있어도 끝나면 SecurityContext를 비운다")
    void runsAsSchedulerAndClearsContext() {
        AnonymizeTargetResult ok = target();
        AnonymizeTargetResult failing = target();
        List<Object> principals = new ArrayList<>();
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(ok, failing));
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), eq(failing)))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            principals.add(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(userWithdrawalCommandService).anonymize(ok.userId());
        doAnswer(invocation -> {
            principals.add(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            throw new IllegalStateException("boom");
        }).when(userWithdrawalCommandService).anonymize(failing.userId());

        scheduler.anonymizeExpiredWithdrawals();

        assertThat(principals).containsExactly(Auditor.SYSTEM_SCHEDULER, Auditor.SYSTEM_SCHEDULER);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("대상 조회가 예외를 던져도 SecurityContext를 비운다")
    void clearsContextWhenLookupFails() {
        when(userWithdrawalCommandService.findAnonymizeTargets(any(LocalDateTime.class), isNull()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(scheduler::anonymizeExpiredWithdrawals).isInstanceOf(IllegalStateException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
