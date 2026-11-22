package com.shineunsigan.snsg.schedule.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.schedule.application.dto.result.NextScheduleQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.NextScheduleResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;

import lombok.RequiredArgsConstructor;

/**
 * 홈 배너의 다가오는 내 일정. 일정을 읽은 뒤 명단 순서 앞 몇 명의 프로필을 user에서 한 번에 불러 사진을 채우고,
 * 지역 이름을 place에서 불러 채운다. 일정이 없으면 user·place를 부르지 않는다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인(user·place) 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class GetNextScheduleFacade {

    /** 배너에 겹쳐 그리는 참여자 사진 수. */
    private static final int PROFILE_IMAGE_COUNT = 3;

    private final ScheduleQueryService scheduleQueryService;
    private final UserPort userPort;
    private final RegionPort regionPort;

    public Optional<NextScheduleResult> get(UUID userId) {
        return scheduleQueryService.findNext(userId).map(this::complete);
    }

    private NextScheduleResult complete(NextScheduleQueryResult schedule) {
        ScheduleListItemResult item = new ScheduleListItemResult(schedule.card(),
                findRegionName(schedule.card().regionCode()));
        return new NextScheduleResult(item, schedule.placeName(), schedule.durationType(),
                findProfileImageUrls(schedule.confirmedUserIds()));
    }

    /** 명단 순서 앞 몇 명만 부른다. 확정 인원이 적으면 그 수만큼이다. */
    private List<String> findProfileImageUrls(List<UUID> confirmedUserIds) {
        List<UUID> userIds = confirmedUserIds.stream().limit(PROFILE_IMAGE_COUNT).toList();
        Map<UUID, ScheduleUserProfileResult> profiles = userPort.findProfiles(userIds);
        return userIds.stream().map(userId -> profiles.get(userId).profileImageUrl()).toList();
    }

    /** 지역 목록에 없는 코드는 빈 문자열이다. */
    private String findRegionName(String regionCode) {
        return regionPort.find(regionCode).map(ScheduleRegionResult::name).orElse("");
    }
}
