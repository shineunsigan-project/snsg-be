package com.shineunsigan.snsg.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.CommonErrorCode;

/**
 * 활동·지역 목록의 개수 상한(중복 제거 후 20개)을 본다. 날짜 해석은 ScheduleQueryServiceTest가 본다.
 */
class RecruitingScheduleFilterTest {

    @Test
    @DisplayName("활동·지역을 각각 20개까지 보낼 수 있다")
    void twentyValuesAreAllowed() {
        RecruitingScheduleFilter filter = RecruitingScheduleFilter.of(ids(20), codes(20), null, null);

        assertThat(filter.categoryIds()).hasSize(20);
        assertThat(filter.regionCodes()).hasSize(20);
    }

    @Test
    @DisplayName("활동이 21개면 INVALID_REQUEST")
    void twentyOneCategoriesAreRejected() {
        assertInvalidRequest(() -> RecruitingScheduleFilter.of(ids(21), null, null, null));
    }

    @Test
    @DisplayName("지역이 21개면 INVALID_REQUEST")
    void twentyOneRegionsAreRejected() {
        assertInvalidRequest(() -> RecruitingScheduleFilter.of(null, codes(21), null, null));
    }

    @Test
    @DisplayName("중복을 빼고 20개면 보낸 값이 더 많아도 통과하고, 중복은 한 번만 남는다")
    void duplicatesAreCountedOnce() {
        List<UUID> categoryIds = new ArrayList<>(ids(20));
        categoryIds.add(categoryIds.get(0));
        List<String> regionCodes = new ArrayList<>(codes(20));
        regionCodes.addAll(codes(5));

        RecruitingScheduleFilter filter = RecruitingScheduleFilter.of(categoryIds, regionCodes, null, null);

        assertThat(filter.categoryIds()).hasSize(20).doesNotHaveDuplicates();
        assertThat(filter.regionCodes()).hasSize(20).doesNotHaveDuplicates();
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID()).toList();
    }

    private static List<String> codes(int count) {
        return IntStream.range(0, count).mapToObj(index -> String.valueOf(11000 + index)).toList();
    }

    private static void assertInvalidRequest(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
}
