package com.shineunsigan.snsg.schedule.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleParticipantQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;

import lombok.RequiredArgsConstructor;

/**
 * 참여자 명단. 권한을 본 뒤 정렬된 확정 참여자를 읽고, 그 프로필을 user에서 한 번에 불러 채운다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인(user) 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class ListParticipantsFacade {

    private final ScheduleQueryService scheduleQueryService;
    private final UserPort userPort;

    /** 순서는 schedule이 정한 명단 순서 그대로다. */
    public List<ParticipantListItemResult> list(UUID scheduleId, UUID viewerId) {
        List<ScheduleParticipantQueryResult> participants = scheduleQueryService.findParticipants(scheduleId,
                viewerId);
        Map<UUID, ScheduleUserProfileResult> profiles = userPort.findProfiles(userIds(participants));
        return participants.stream()
                .map(participant -> ParticipantListItemResult.of(participant, profiles.get(participant.userId()),
                        viewerId))
                .toList();
    }

    private static List<UUID> userIds(List<ScheduleParticipantQueryResult> participants) {
        return participants.stream().map(ScheduleParticipantQueryResult::userId).toList();
    }
}
