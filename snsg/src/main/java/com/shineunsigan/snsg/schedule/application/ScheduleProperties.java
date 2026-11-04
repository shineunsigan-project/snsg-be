package com.shineunsigan.snsg.schedule.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.shineunsigan.snsg.schedule.domain.model.ScheduleCreationRule;

/**
 * 일정 정책.
 *
 * @param capacityMin     개설자 포함 최소 인원
 * @param capacityMax     개설자 포함 최대 인원
 * @param minLeadTime     시작은 지금 + 이 시간 이후. 같으면 허용한다
 * @param maxAdvanceDays  시작 날짜는 오늘 + 이 일수 이내
 * @param overlapWindow   내가 확정 참여한 일정과 시작 시각이 이 시간 미만으로 떨어지면 겹친다. 같으면 겹치지 않는다
 * @param completionAfter 시작 후 이 시간이 지나면 완료로 본다. 같으면 완료다
 */
@ConfigurationProperties(prefix = "schedule")
public record ScheduleProperties(int capacityMin, int capacityMax, Duration minLeadTime, int maxAdvanceDays,
        Duration overlapWindow, Duration completionAfter) {

    public ScheduleCreationRule creationRule() {
        return new ScheduleCreationRule(capacityMin, capacityMax, minLeadTime, maxAdvanceDays);
    }
}
