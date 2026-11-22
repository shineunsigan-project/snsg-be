package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.schedule.application.dto.result.NextScheduleQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.NextScheduleResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;

/**
 * 사진 순서·장수(명단 순서 앞 3명), 프로필·지역 포트 호출 횟수와 인자, 탈퇴자 경로, 없는 지역, 대상 없음의 흐름을 본다.
 * 명단 순서 규칙은 ScheduleTest가 본다.
 */
@ExtendWith(MockitoExtension.class)
class GetNextScheduleFacadeTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID FIRST_MEMBER_ID = UUID.randomUUID();
    private static final UUID WITHDRAWN_MEMBER_ID = UUID.randomUUID();
    private static final UUID FOURTH_MEMBER_ID = UUID.randomUUID();
    private static final String REGION_CODE = "11440";

    @Mock
    private ScheduleQueryService scheduleQueryService;

    @Mock
    private UserPort userPort;

    @Mock
    private RegionPort regionPort;

    @InjectMocks
    private GetNextScheduleFacade getNextScheduleFacade;

    @Test
    @DisplayName("명단 순서 앞 3명의 id로만 프로필 일괄 조회를 한 번 부르고, 사진을 그 순서로 채운다. 개설자가 첫 번째다")
    void firstThreeInListOrder() {
        when(scheduleQueryService.findNext(USER_ID))
                .thenReturn(Optional.of(schedule(List.of(HOST_ID, FIRST_MEMBER_ID, WITHDRAWN_MEMBER_ID,
                        FOURTH_MEMBER_ID))));
        when(userPort.findProfiles(any())).thenReturn(profiles());
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.of(new ScheduleRegionResult(REGION_CODE, "마포구", true)));

        NextScheduleResult result = getNextScheduleFacade.get(USER_ID).orElseThrow();

        ArgumentCaptor<Collection<UUID>> userIds = idsCaptor();
        verify(userPort, times(1)).findProfiles(userIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder(HOST_ID, FIRST_MEMBER_ID, WITHDRAWN_MEMBER_ID);
        assertThat(result.participantProfileImageUrls()).containsExactly("/images/profiles/host.png",
                "/images/profiles/first.png", "/images/default-profile.png");
        verify(regionPort, times(1)).find(REGION_CODE);
        assertThat(result.item().regionName()).isEqualTo("마포구");
        assertThat(result.placeName()).isEqualTo("보드게임카페 홍대점");
        assertThat(result.durationType()).isEqualTo(DurationType.NORMAL);
    }

    @Test
    @DisplayName("확정 인원이 3명보다 적으면 그 수만큼 사진이 온다")
    void fewerThanThree() {
        when(scheduleQueryService.findNext(USER_ID)).thenReturn(Optional.of(schedule(List.of(HOST_ID))));
        when(userPort.findProfiles(any())).thenReturn(profiles());
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.of(new ScheduleRegionResult(REGION_CODE, "마포구", true)));

        NextScheduleResult result = getNextScheduleFacade.get(USER_ID).orElseThrow();

        ArgumentCaptor<Collection<UUID>> userIds = idsCaptor();
        verify(userPort, times(1)).findProfiles(userIds.capture());
        assertThat(userIds.getValue()).containsExactly(HOST_ID);
        assertThat(result.participantProfileImageUrls()).containsExactly("/images/profiles/host.png");
    }

    @Test
    @DisplayName("지역 목록에 없는 코드면 지역 이름은 빈 문자열이다")
    void unknownRegion() {
        when(scheduleQueryService.findNext(USER_ID)).thenReturn(Optional.of(schedule(List.of(HOST_ID))));
        when(userPort.findProfiles(any())).thenReturn(profiles());
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.empty());

        assertThat(getNextScheduleFacade.get(USER_ID).orElseThrow().item().regionName()).isEmpty();
    }

    @Test
    @DisplayName("대상 일정이 없으면 빈 결과이고 user·place 포트를 부르지 않는다")
    void none() {
        when(scheduleQueryService.findNext(USER_ID)).thenReturn(Optional.empty());

        assertThat(getNextScheduleFacade.get(USER_ID)).isEmpty();
        verifyNoInteractions(userPort, regionPort);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<UUID>> idsCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private static NextScheduleQueryResult schedule(List<UUID> confirmedUserIds) {
        ScheduleCardResult card = new ScheduleCardResult(UUID.randomUUID(), UUID.randomUUID(), "보드게임",
                "보드게임 같이 해요", REGION_CODE, LocalDateTime.of(2026, 11, 14, 19, 30), confirmedUserIds.size(), 6,
                6 - confirmedUserIds.size(), DisplayStatus.RECRUITING, LocalDateTime.of(2026, 11, 10, 9, 0));
        return new NextScheduleQueryResult(card, "보드게임카페 홍대점", DurationType.NORMAL, confirmedUserIds);
    }

    private static Map<UUID, ScheduleUserProfileResult> profiles() {
        return Map.of(
                HOST_ID, new ScheduleUserProfileResult("민트초코", "/images/profiles/host.png", Gender.FEMALE, null,
                        false),
                FIRST_MEMBER_ID, new ScheduleUserProfileResult("초코민트", "/images/profiles/first.png", Gender.MALE,
                        null, false),
                WITHDRAWN_MEMBER_ID, new ScheduleUserProfileResult("탈퇴한 사용자", "/images/default-profile.png",
                        Gender.MALE, null, true));
    }
}
