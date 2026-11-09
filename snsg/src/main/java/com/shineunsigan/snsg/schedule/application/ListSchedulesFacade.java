package com.shineunsigan.snsg.schedule.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardPageResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;

import lombok.RequiredArgsConstructor;

/**
 * 일정 카드 목록. 모집 중·참여한·개설한 세 범위가 한 행위다.
 * 일정을 읽은 뒤 지역 이름을 place 도메인에서 한 번에 불러 채운다(지역 코드 → 이름, 비활성 지역 포함).
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인(place) 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class ListSchedulesFacade {

    private final ScheduleQueryService scheduleQueryService;
    private final RegionPort regionPort;

    public ScheduleListResult listRecruiting(ListRecruitingSchedulesCommand command) {
        return withRegionNames(scheduleQueryService.findRecruiting(command));
    }

    public ScheduleListResult listParticipating(UUID userId, String cursor, Integer size) {
        return withRegionNames(scheduleQueryService.findParticipating(userId, cursor, size));
    }

    public ScheduleListResult listHosted(UUID userId, String cursor, Integer size) {
        return withRegionNames(scheduleQueryService.findHosted(userId, cursor, size));
    }

    /** 지역 목록에 없는 코드는 빈 문자열이다. 일정이 없으면 지역을 부르지 않는다. */
    private ScheduleListResult withRegionNames(ScheduleCardPageResult page) {
        if (page.items().isEmpty()) {
            return new ScheduleListResult(List.of(), page.nextCursor());
        }
        Map<String, String> regionNames = findRegionNames();
        List<ScheduleListItemResult> items = page.items().stream()
                .map(card -> new ScheduleListItemResult(card, regionNames.getOrDefault(card.regionCode(), "")))
                .toList();
        return new ScheduleListResult(items, page.nextCursor());
    }

    private Map<String, String> findRegionNames() {
        return regionPort.findAll().stream()
                .collect(Collectors.toMap(ScheduleRegionResult::code, ScheduleRegionResult::name));
    }
}
