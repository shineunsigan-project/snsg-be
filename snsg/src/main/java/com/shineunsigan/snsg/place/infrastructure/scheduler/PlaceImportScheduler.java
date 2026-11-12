package com.shineunsigan.snsg.place.infrastructure.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.place.application.ImportPlacesFacade;

import lombok.RequiredArgsConstructor;

/**
 * 상가정보 장소 적재(JOB-04). 분기마다 한 번 돈다. 여러 서버의 동시 실행은 facade가 잠금으로 막는다.
 * 적재가 만들고 바꾼 행의 감사 컬럼에 스케줄러가 남도록 실행 동안 SecurityContext의 주체를
 * {@link Auditor#SYSTEM_SCHEDULER}로 둔다.
 */
@Component
@RequiredArgsConstructor
public class PlaceImportScheduler {

    private final ImportPlacesFacade importPlacesFacade;

    @Scheduled(cron = "${place.import-cron}")
    public void importPlaces() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(Auditor.SYSTEM_SCHEDULER, null, List.of()));
        try {
            importPlacesFacade.importPlaces();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
