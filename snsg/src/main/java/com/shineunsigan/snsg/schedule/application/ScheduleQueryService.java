package com.shineunsigan.snsg.schedule.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardPageResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;
import com.shineunsigan.snsg.schedule.domain.model.RecruitingScheduleFilter;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleCursor;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * 일정 목록 3종(모집 중, 참여한, 개설한). 커서 페이지로 읽어 카드로 바꾼다.
 * <p>
 * 한 페이지보다 한 건 더 읽어 다음 페이지가 있는지 정한다. 총 건수가 페이지 크기의 배수여도 빈 페이지를 한 번 더 부르지 않게 하기 위해서다.
 * 지금 시각은 요청마다 한 번 잡아 쿼리 조건과 표시 상태에 함께 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleQueryService {

    private final ScheduleRepository scheduleRepository;
    private final ActivityCategoryRepository activityCategoryRepository;
    private final ScheduleProperties scheduleProperties;

    /** 날짜 범위를 커서보다 먼저 본다. 둘 다 틀리면 INVALID_DATE_FILTER다. */
    public ScheduleCardPageResult findRecruiting(ListRecruitingSchedulesCommand command) {
        RecruitingScheduleFilter filter = RecruitingScheduleFilter.of(command.categoryIds(), command.regionCodes(),
                command.startDate(), command.endDate());
        ScheduleCursor after = decodeCursor(command.cursor());
        int size = pageSize(command.size());
        LocalDateTime now = LocalDateTime.now();
        return toPage(scheduleRepository.findRecruiting(filter, now, after, size + 1), size, now);
    }

    public ScheduleCardPageResult findParticipating(UUID userId, String cursor, Integer size) {
        ScheduleCursor after = decodeCursor(cursor);
        int pageSize = pageSize(size);
        LocalDateTime now = LocalDateTime.now();
        return toPage(scheduleRepository.findParticipating(userId, startAfter(now), after, pageSize + 1), pageSize,
                now);
    }

    public ScheduleCardPageResult findHosted(UUID userId, String cursor, Integer size) {
        ScheduleCursor after = decodeCursor(cursor);
        int pageSize = pageSize(size);
        LocalDateTime now = LocalDateTime.now();
        return toPage(scheduleRepository.findHosted(userId, startAfter(now), after, pageSize + 1), pageSize, now);
    }

    private static ScheduleCursor decodeCursor(String cursor) {
        return cursor == null || cursor.isEmpty() ? null : ScheduleCursor.decode(cursor);
    }

    /** 생략하면 기본 개수, 최대를 넘으면 최대, 1보다 작으면 1이다. */
    private int pageSize(Integer requested) {
        int size = requested == null ? scheduleProperties.pageSizeDefault() : requested;
        return Math.max(1, Math.min(size, scheduleProperties.pageSizeMax()));
    }

    /** 시작 + 완료 기준 시간이 지금보다 뒤인 일정만 남기는 기준. 시작이 이 시각보다 뒤여야 한다. */
    private LocalDateTime startAfter(LocalDateTime now) {
        return now.minus(scheduleProperties.completionAfter());
    }

    private ScheduleCardPageResult toPage(List<Schedule> schedules, int size, LocalDateTime now) {
        boolean hasNext = schedules.size() > size;
        List<Schedule> page = hasNext ? schedules.subList(0, size) : schedules;
        Map<UUID, String> categoryNames = findCategoryNames(page);
        List<ScheduleCardResult> items = page.stream()
                .map(schedule -> toCard(schedule, categoryNames, now))
                .toList();
        return new ScheduleCardPageResult(items, hasNext ? nextCursor(page.get(size - 1)) : null);
    }

    /** 읽은 일정의 활동을 한 번에 찾는다. 비활성 활동도 이름을 준다. */
    private Map<UUID, String> findCategoryNames(List<Schedule> schedules) {
        if (schedules.isEmpty()) {
            return Map.of();
        }
        List<UUID> categoryIds = schedules.stream().map(Schedule::getCategoryId).distinct().toList();
        return activityCategoryRepository.findAllNotDeletedByIds(categoryIds).stream()
                .collect(Collectors.toMap(ActivityCategory::getId, ActivityCategory::getName));
    }

    private ScheduleCardResult toCard(Schedule schedule, Map<UUID, String> categoryNames, LocalDateTime now) {
        return new ScheduleCardResult(schedule.getId(), schedule.getCategoryId(),
                categoryNames.getOrDefault(schedule.getCategoryId(), ""), schedule.getTitle(),
                schedule.getPlace().getRegionCode(), schedule.getStartAt(), schedule.getConfirmedCount(),
                schedule.getCapacity(), schedule.getCapacity() - schedule.getConfirmedCount(),
                schedule.displayStatus(now, scheduleProperties.completionAfter()), schedule.getCreatedAt());
    }

    private static String nextCursor(Schedule last) {
        return new ScheduleCursor(last.getStartAt(), last.getId()).encode();
    }
}
