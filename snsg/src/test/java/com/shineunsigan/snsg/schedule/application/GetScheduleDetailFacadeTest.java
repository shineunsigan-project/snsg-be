package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleAgeGroupResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 프로필 일괄 조회 횟수와 대상, 개설자·참여자 요약·지역 이름 채우기, 없는 일정의 흐름을 본다.
 * 요약 계산 규칙은 ParticipantSummaryTest, 역할·동작 판정은 ScheduleTest가 본다.
 */
@ExtendWith(MockitoExtension.class)
class GetScheduleDetailFacadeTest {

    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final UUID VIEWER_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID WITHDRAWN_MEMBER_ID = UUID.randomUUID();
    private static final String REGION_CODE = "11440";

    @Mock
    private ScheduleQueryService scheduleQueryService;

    @Mock
    private UserPort userPort;

    @Mock
    private RegionPort regionPort;

    @InjectMocks
    private GetScheduleDetailFacade getScheduleDetailFacade;

    @Test
    @DisplayName("개설자와 확정 참여자 id를 모아 프로필 일괄 조회를 한 번 부르고, 개설자·참여자 요약·지역 이름을 채운다")
    void fillsDetail() {
        when(scheduleQueryService.findDetail(SCHEDULE_ID, VIEWER_ID))
                .thenReturn(detail(List.of(HOST_ID, MEMBER_ID, WITHDRAWN_MEMBER_ID)));
        when(userPort.findProfiles(any())).thenReturn(Map.of(
                HOST_ID, profile("민트초코", Gender.FEMALE, new ScheduleAgeGroupResult(20, AgePhase.LATE)),
                MEMBER_ID, profile("초코민트", Gender.MALE, new ScheduleAgeGroupResult(30, AgePhase.EARLY)),
                WITHDRAWN_MEMBER_ID, withdrawn(Gender.MALE)));
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.of(new ScheduleRegionResult(REGION_CODE, "마포구", true)));

        ScheduleDetailResult result = getScheduleDetailFacade.get(SCHEDULE_ID, VIEWER_ID);

        ArgumentCaptor<Collection<UUID>> userIds = idsCaptor();
        verify(userPort, times(1)).findProfiles(userIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID, WITHDRAWN_MEMBER_ID);
        assertThat(result.host().nickname()).isEqualTo("민트초코");
        assertThat(result.host().withdrawn()).isFalse();
        assertThat(result.participantSummary().count()).isEqualTo(3);
        assertThat(result.participantSummary().maleCount()).isEqualTo(2);
        assertThat(result.participantSummary().femaleCount()).isEqualTo(1);
        assertThat(result.participantSummary().minAgeGroup()).isEqualTo(new ScheduleAgeGroupResult(20, AgePhase.LATE));
        assertThat(result.participantSummary().maxAgeGroup())
                .isEqualTo(new ScheduleAgeGroupResult(30, AgePhase.EARLY));
        assertThat(result.regionName()).isEqualTo("마포구");
        assertThat(result.schedule().id()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    @DisplayName("탈퇴한 개설자는 user가 준 가린 값(탈퇴한 사용자·기본 이미지)과 withdrawn = true다")
    void withdrawnHost() {
        when(scheduleQueryService.findDetail(SCHEDULE_ID, null)).thenReturn(detail(List.of(HOST_ID)));
        when(userPort.findProfiles(any())).thenReturn(Map.of(HOST_ID, withdrawn(Gender.FEMALE)));
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.of(new ScheduleRegionResult(REGION_CODE, "마포구", true)));

        ScheduleDetailResult result = getScheduleDetailFacade.get(SCHEDULE_ID, null);

        assertThat(result.host().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(result.host().profileImageUrl()).isEqualTo("/images/default-profile.png");
        assertThat(result.host().withdrawn()).isTrue();
        assertThat(result.participantSummary().minAgeGroup()).isNull();
        assertThat(result.participantSummary().maxAgeGroup()).isNull();
    }

    @Test
    @DisplayName("지역을 찾을 수 없으면 지역 이름은 빈 문자열이다")
    void missingRegion() {
        when(scheduleQueryService.findDetail(SCHEDULE_ID, VIEWER_ID)).thenReturn(detail(List.of(HOST_ID)));
        when(userPort.findProfiles(any())).thenReturn(Map.of(HOST_ID,
                profile("민트초코", Gender.FEMALE, new ScheduleAgeGroupResult(20, AgePhase.LATE))));
        when(regionPort.find(REGION_CODE)).thenReturn(Optional.empty());

        assertThat(getScheduleDetailFacade.get(SCHEDULE_ID, VIEWER_ID).regionName()).isEmpty();
    }

    @Test
    @DisplayName("일정이 없으면 SCHEDULE_NOT_FOUND이고 user·region 포트를 부르지 않는다")
    void notFound() {
        when(scheduleQueryService.findDetail(SCHEDULE_ID, VIEWER_ID))
                .thenThrow(new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        assertThatThrownBy(() -> getScheduleDetailFacade.get(SCHEDULE_ID, VIEWER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
        verifyNoInteractions(userPort, regionPort);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<UUID>> idsCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private static ScheduleDetailQueryResult detail(List<UUID> confirmedUserIds) {
        return new ScheduleDetailQueryResult(SCHEDULE_ID, "보드게임 같이 해요", "처음 오시는 분도 환영해요.",
                LocalDateTime.of(2026, 11, 14, 19, 30), LocalDateTime.of(2026, 11, 10, 9, 0), DurationType.NORMAL,
                UUID.randomUUID(), "보드게임", HOST_ID, confirmedUserIds, confirmedUserIds.size(), 6,
                6 - confirmedUserIds.size(),
                new ScheduleDetailQueryResult.Place("보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
                        new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), REGION_CODE),
                DisplayStatus.RECRUITING, ViewerRole.NON_PARTICIPANT, List.of(ScheduleAction.JOIN), null);
    }

    private static ScheduleUserProfileResult profile(String nickname, Gender gender,
            ScheduleAgeGroupResult ageGroup) {
        return new ScheduleUserProfileResult(nickname, "/images/profiles/" + nickname + ".png", gender, ageGroup,
                false);
    }

    private static ScheduleUserProfileResult withdrawn(Gender gender) {
        return new ScheduleUserProfileResult("탈퇴한 사용자", "/images/default-profile.png", gender, null, true);
    }
}
