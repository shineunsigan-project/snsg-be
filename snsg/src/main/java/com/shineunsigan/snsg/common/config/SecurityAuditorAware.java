package com.shineunsigan.snsg.common.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shineunsigan.snsg.common.entity.Auditor;

/**
 * 감사 컬럼에 넣을 주체를 SecurityContext에서 꺼낸다.
 * 인증된 요청이면 사용자 식별자, 아니면 {@link Auditor#SYSTEM}이다.
 * 일정 상태 전환 스케줄러의 일괄 UPDATE는 JPA Auditing을 거치지 않아 여기를 지나지 않는다.
 * 그 쿼리가 updated_by에 {@link Auditor#SYSTEM_SCHEDULER}를 직접 넣는다.
 */
class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(Auditor.SYSTEM);
        }
        Object principal = authentication.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return Optional.of(Auditor.SYSTEM);
        }
        return Optional.of(principal.toString());
    }
}
