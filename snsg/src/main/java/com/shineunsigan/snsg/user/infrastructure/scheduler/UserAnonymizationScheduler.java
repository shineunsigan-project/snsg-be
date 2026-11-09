package com.shineunsigan.snsg.user.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.user.application.UserWithdrawalCommandService;
import com.shineunsigan.snsg.user.application.dto.result.AnonymizeTargetResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보관 기간이 지난 탈퇴 회원을 비식별한다.
 * <p>
 * 대상을 keyset 페이지로 끝까지 읽고, 회원마다 서비스를 따로 불러 트랜잭션을 나눈다.
 * 한 회원이 실패해도 나머지는 진행한다. 실패한 회원은 커서가 지나가 이번 실행에서는 다시 잡지 않고 다음 실행에서 다시 처리된다.
 * 감사 컬럼에 스케줄러가 남도록 실행 동안 SecurityContext의 주체를 {@link Auditor#SYSTEM_SCHEDULER}로 둔다.
 * 여러 서버에서 같은 회원을 동시에 비식별해도 같은 값을 써서 분산 락을 두지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAnonymizationScheduler {

    private final UserWithdrawalCommandService userWithdrawalCommandService;

    @Scheduled(cron = "${withdrawal.anonymize-cron}")
    public void anonymizeExpiredWithdrawals() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(Auditor.SYSTEM_SCHEDULER, null, List.of()));
        try {
            anonymizeAll(LocalDateTime.now());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void anonymizeAll(LocalDateTime now) {
        AnonymizeTargetResult after = null;
        List<AnonymizeTargetResult> page = userWithdrawalCommandService.findAnonymizeTargets(now, after);
        while (!page.isEmpty()) {
            page.forEach(this::anonymize);
            after = page.get(page.size() - 1);
            page = userWithdrawalCommandService.findAnonymizeTargets(now, after);
        }
    }

    private void anonymize(AnonymizeTargetResult target) {
        try {
            userWithdrawalCommandService.anonymize(target.userId());
        } catch (RuntimeException exception) {
            log.warn("탈퇴 회원 비식별 실패. 다음 실행에서 다시 처리한다. userId={}", target.userId(), exception);
        }
    }
}
