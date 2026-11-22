package com.shineunsigan.snsg.schedule.domain.constants;

import java.time.LocalDateTime;

/**
 * 모집 중 일정 목록의 정렬. 세 값 모두 예정(SCHEDULED)이고 시작 전인 일정이 대상이고, 필터는 함께 걸린다.
 * 정렬마다 커서의 정렬 키가 달라 다른 정렬에서 받은 커서는 해석하지 않는다(INVALID_CURSOR).
 */
public enum RecruitingScheduleSort {

    /** 시작 일시 빠른 순(같으면 id 순). 정원이 찬 일정도 포함한다. 기본값이다. */
    START_AT,

    /** 최근 열린 순. 개설 일시 늦은 순(같으면 id 역순). 정원이 찬 일정도 포함한다. */
    LATEST,

    /**
     * 마감 임박 순. 시작 날짜가 오늘·내일·모레(D-2까지)이고 정원이 차지 않은 일정만 온다.
     * 남은 자리 적은 순 → 시작 일시 빠른 순 → id 순. 남은 자리는 바뀌는 값이라 페이지 사이에 겹치거나 빠질 수 있다.
     */
    CLOSING_SOON;

    /** 마감 임박에 드는 마지막 시작 날짜가 오늘에서 며칠 뒤인지(모레). */
    private static final int CLOSING_SOON_LAST_DAY_OFFSET = 2;

    /**
     * 마감 임박에 드는 시작 시각의 상한(미만). 기준 시각의 날짜 + 3일 00:00이다.
     * 시각(지금 + 48시간)이 아니라 날짜 기준이라, 기준 시각이 그날 몇 시든 모레 23:59 시작까지 든다.
     */
    public static LocalDateTime closingSoonStartBefore(LocalDateTime now) {
        return now.toLocalDate().plusDays(CLOSING_SOON_LAST_DAY_OFFSET + 1).atStartOfDay();
    }
}
