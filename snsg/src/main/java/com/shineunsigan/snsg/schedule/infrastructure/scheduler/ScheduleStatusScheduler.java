package com.shineunsigan.snsg.schedule.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.schedule.application.ScheduleCommandService;

import lombok.RequiredArgsConstructor;

/**
 * 일정 상태 전환. 두 전환은 각자의 트랜잭션에서 돈다(트랜잭션은 서비스 메서드가 건다).
 * 시작 시각이 :00/:30만 허용되고 완료도 :00/:30이라 cron으로 매시 0분·30분에 돈다.
 * cron은 한 실행이 끝난 뒤 다음 시각을 계산해 같은 서버에서 실행이 겹치지 않는다.
 * 조건부 일괄 UPDATE라 여러 서버에서 돌아도 결과가 같아 분산 락을 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleStatusScheduler {

    private final ScheduleCommandService scheduleCommandService;

    @Scheduled(cron = "${schedule.status-transition-cron}")
    public void closeStartedRecruitments() {
        scheduleCommandService.closeStartedRecruitments();
    }

    @Scheduled(cron = "${schedule.status-transition-cron}")
    public void completeFinishedSchedules() {
        scheduleCommandService.completeFinishedSchedules();
    }
}
