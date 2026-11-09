package com.shineunsigan.snsg.schedule.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.CommonErrorCode;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 모집 중 목록의 필터. null인 조건은 걸지 않는다.
 * 목록 조건은 같은 조건 안에서 OR, 조건 사이는 AND다. 목록이 비어 있으면(빈 값만 보낸 경우) 맞는 일정이 없다.
 *
 * @param categoryIds 활동 id. null이면 활동 조건 없음
 * @param regionCodes 지역 코드. null이면 지역 조건 없음
 * @param startFrom   시작 시각이 이 시각 이상. null이면 날짜 조건 없음
 * @param startBefore 시작 시각이 이 시각 미만. startFrom과 함께 null이거나 함께 있다
 */
public record RecruitingScheduleFilter(List<UUID> categoryIds, List<String> regionCodes,
        LocalDateTime startFrom, LocalDateTime startBefore) {

    /**
     * 활동·지역 목록 하나에 보낼 수 있는 값의 최대 개수(중복 제거 후).
     * 활동·지역은 각각 수 개뿐이라 정상 화면에서는 닿지 않는다. 공개 API라 값을 아주 많이 보내면
     * IN 조건의 바인드 파라미터가 DB 한도를 넘어 500이 나므로, 남용을 막으려고 요청 단계에서 끊는다.
     */
    private static final int MAX_FILTER_VALUES = 20;

    /**
     * 시작 날짜 범위는 양 끝을 포함한다. 하나만 오면 그날 하루다. 끝 날짜가 시작 날짜보다 앞서면 INVALID_DATE_FILTER다.
     * 목록의 빈 값(null, 공백)은 어떤 일정과도 맞지 않는 값이라 뺀다. 중복도 뺀다.
     * 빈 값·중복을 뺀 목록이 {@value #MAX_FILTER_VALUES}개를 넘으면 INVALID_REQUEST다. 날짜를 먼저 본다.
     */
    public static RecruitingScheduleFilter of(List<UUID> categoryIds, List<String> regionCodes,
            LocalDate startDate, LocalDate endDate) {
        LocalDate from = startDate != null ? startDate : endDate;
        LocalDate to = endDate != null ? endDate : startDate;
        if (from != null && to.isBefore(from)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_DATE_FILTER);
        }
        return new RecruitingScheduleFilter(limited(withoutBlankIds(categoryIds)),
                limited(withoutBlankCodes(regionCodes)),
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay());
    }

    /** 빈 목록인 조건이 있으면 맞는 일정이 없다. */
    public boolean matchesNothing() {
        return (categoryIds != null && categoryIds.isEmpty()) || (regionCodes != null && regionCodes.isEmpty());
    }

    private static List<UUID> withoutBlankIds(List<UUID> ids) {
        return ids == null ? null : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<String> withoutBlankCodes(List<String> codes) {
        return codes == null ? null
                : codes.stream().filter(code -> code != null && !code.isBlank()).distinct().toList();
    }

    private static <T> List<T> limited(List<T> values) {
        if (values != null && values.size() > MAX_FILTER_VALUES) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return values;
    }
}
