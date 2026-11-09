package com.shineunsigan.snsg.schedule.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantSummaryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleHostResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.model.ParticipantProfile;
import com.shineunsigan.snsg.schedule.domain.model.ParticipantSummary;

import lombok.RequiredArgsConstructor;

/**
 * 일정 상세. 일정을 읽은 뒤 개설자·확정 참여자의 프로필을 user에서 한 번에 불러 개설자와 참여자 요약을 채우고,
 * 지역 이름을 place에서 불러 채운다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인(user·place) 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class GetScheduleDetailFacade {

    private final ScheduleQueryService scheduleQueryService;
    private final UserPort userPort;
    private final RegionPort regionPort;

    /** @param viewerId 로그인하지 않았으면 {@code null} */
    public ScheduleDetailResult get(UUID scheduleId, UUID viewerId) {
        ScheduleDetailQueryResult schedule = scheduleQueryService.findDetail(scheduleId, viewerId);
        Map<UUID, ScheduleUserProfileResult> profiles = userPort.findProfiles(userIds(schedule));
        return new ScheduleDetailResult(schedule, ScheduleHostResult.from(profiles.get(schedule.hostUserId())),
                summarize(schedule.confirmedUserIds(), profiles), findRegionName(schedule.place().regionCode()));
    }

    /** 개설자는 확정 참여자에 들어 있지만, 개설자 프로필이 반드시 있도록 함께 넣는다. */
    private static Set<UUID> userIds(ScheduleDetailQueryResult schedule) {
        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(schedule.hostUserId());
        userIds.addAll(schedule.confirmedUserIds());
        return userIds;
    }

    private static ParticipantSummaryResult summarize(List<UUID> confirmedUserIds,
            Map<UUID, ScheduleUserProfileResult> profiles) {
        List<ParticipantProfile> participants = confirmedUserIds.stream()
                .map(userId -> profiles.get(userId).toParticipantProfile())
                .toList();
        return ParticipantSummaryResult.from(ParticipantSummary.of(participants));
    }

    /** 지역 목록에 없는 코드는 빈 문자열이다. */
    private String findRegionName(String regionCode) {
        return regionPort.find(regionCode).map(ScheduleRegionResult::name).orElse("");
    }
}
