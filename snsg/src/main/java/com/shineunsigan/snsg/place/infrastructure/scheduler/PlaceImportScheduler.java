package com.shineunsigan.snsg.place.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.place.application.ImportPlacesFacade;

import lombok.RequiredArgsConstructor;

/**
 * 상가정보 장소 적재(JOB-04). 분기마다 한 번 돈다. 여러 서버의 동시 실행은 facade가 잠금으로 막는다.
 */
@Component
@RequiredArgsConstructor
public class PlaceImportScheduler {

    private final ImportPlacesFacade importPlacesFacade;

    @Scheduled(cron = "${place.import-cron}")
    public void importPlaces() {
        importPlacesFacade.importPlaces();
    }
}
