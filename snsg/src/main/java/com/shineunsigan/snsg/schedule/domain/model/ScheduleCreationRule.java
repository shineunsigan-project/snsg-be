package com.shineunsigan.snsg.schedule.domain.model;

import java.time.Duration;

/**
 * 개설할 때 지키는 설정값. 도메인은 설정을 읽지 않으므로 서비스가 설정에서 꺼내 넘긴다.
 *
 * @param capacityMin    개설자 포함 최소 인원
 * @param capacityMax    개설자 포함 최대 인원
 * @param minLeadTime    시작까지 남아야 하는 최소 시간. 같으면 허용한다
 * @param maxAdvanceDays 시작 날짜가 오늘에서 며칠 뒤까지 될 수 있는지. 그날 23:30까지 허용한다
 */
public record ScheduleCreationRule(int capacityMin, int capacityMax, Duration minLeadTime, int maxAdvanceDays) {
}
