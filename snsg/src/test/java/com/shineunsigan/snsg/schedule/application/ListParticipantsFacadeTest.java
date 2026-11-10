package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleAgeGroupResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleParticipantQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 프로필 일괄 조회 횟수와 대상, 순서 유지, isHost·isMe, 탈퇴자 값, 없는 일정·권한 없음의 흐름을 본다.
 * 권한 판정과 명단 순서 규칙은 ScheduleTest가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ListParticipantsFacadeTest {

    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID WITHDRAWN_MEMBER_ID = UUID.randomUUID();

    @Mock
    private ScheduleQueryService scheduleQueryService;

    @Mock
    private UserPort userPort;

    @InjectMocks
    private ListParticipantsFacade listParticipantsFacade;

    @Test
    @DisplayName("명단의 모든 사용자 id로 프로필 일괄 조회를 한 번 부르고, schedule이 정한 순서 그대로 채운다")
    void fillsInOrder() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, MEMBER_ID)).thenReturn(participants());
        when(userPort.findProfiles(any())).thenReturn(profiles());

        List<ParticipantListItemResult> result = listParticipantsFacade.list(SCHEDULE_ID, MEMBER_ID);

        ArgumentCaptor<Collection<UUID>> userIds = idsCaptor();
        verify(userPort, times(1)).findProfiles(userIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder(HOST_ID, MEMBER_ID, WITHDRAWN_MEMBER_ID);
        assertThat(result).extracting(ParticipantListItemResult::nickname)
                .containsExactly("민트초코", "탈퇴한 사용자", "초코민트");
    }

    @Test
    @DisplayName("isHost는 개설자 항목만, isMe는 보는 사람 항목만 true다")
    void hostAndMeFlags() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, MEMBER_ID)).thenReturn(participants());
        when(userPort.findProfiles(any())).thenReturn(profiles());

        List<ParticipantListItemResult> result = listParticipantsFacade.list(SCHEDULE_ID, MEMBER_ID);

        assertThat(result).extracting(ParticipantListItemResult::isHost).containsExactly(true, false, false);
        assertThat(result).extracting(ParticipantListItemResult::isMe).containsExactly(false, false, true);
    }

    @Test
    @DisplayName("개설자가 보면 개설자 항목이 isHost·isMe 모두 true다")
    void hostViewsOwnList() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, HOST_ID)).thenReturn(participants());
        when(userPort.findProfiles(any())).thenReturn(profiles());

        List<ParticipantListItemResult> result = listParticipantsFacade.list(SCHEDULE_ID, HOST_ID);

        assertThat(result.get(0).isHost()).isTrue();
        assertThat(result.get(0).isMe()).isTrue();
        assertThat(result).extracting(ParticipantListItemResult::isMe).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("탈퇴자 항목은 user가 준 가린 값(탈퇴한 사용자·기본 이미지·연령대 없음·성별 유지)과 withdrawn = true다")
    void withdrawnMember() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, MEMBER_ID)).thenReturn(participants());
        when(userPort.findProfiles(any())).thenReturn(profiles());

        ParticipantListItemResult withdrawn = listParticipantsFacade.list(SCHEDULE_ID, MEMBER_ID).get(1);

        assertThat(withdrawn.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(withdrawn.profileImageUrl()).isEqualTo("/images/default-profile.png");
        assertThat(withdrawn.ageGroup()).isNull();
        assertThat(withdrawn.gender()).isEqualTo(Gender.MALE);
        assertThat(withdrawn.withdrawn()).isTrue();
    }

    @Test
    @DisplayName("일정이 없으면 SCHEDULE_NOT_FOUND이고 user 포트를 부르지 않는다")
    void notFound() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, MEMBER_ID))
                .thenThrow(new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        assertError(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
        verifyNoInteractions(userPort);
    }

    @Test
    @DisplayName("볼 수 없는 사람이면 PARTICIPANT_LIST_FORBIDDEN이고 user 포트를 부르지 않는다")
    void forbidden() {
        when(scheduleQueryService.findParticipants(SCHEDULE_ID, MEMBER_ID))
                .thenThrow(new BusinessException(ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN));

        assertError(ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN);
        verifyNoInteractions(userPort);
    }

    private void assertError(ScheduleErrorCode errorCode) {
        assertThatThrownBy(() -> listParticipantsFacade.list(SCHEDULE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<UUID>> idsCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    /** schedule이 정한 순서: 개설자, 탈퇴한 멤버, 멤버. */
    private static List<ScheduleParticipantQueryResult> participants() {
        return List.of(new ScheduleParticipantQueryResult(HOST_ID, true),
                new ScheduleParticipantQueryResult(WITHDRAWN_MEMBER_ID, false),
                new ScheduleParticipantQueryResult(MEMBER_ID, false));
    }

    private static Map<UUID, ScheduleUserProfileResult> profiles() {
        return Map.of(
                HOST_ID, new ScheduleUserProfileResult("민트초코", "/images/profiles/host.png", Gender.FEMALE,
                        new ScheduleAgeGroupResult(20, AgePhase.LATE), false),
                MEMBER_ID, new ScheduleUserProfileResult("초코민트", "/images/profiles/member.png", Gender.MALE,
                        new ScheduleAgeGroupResult(30, AgePhase.EARLY), false),
                WITHDRAWN_MEMBER_ID, new ScheduleUserProfileResult("탈퇴한 사용자", "/images/default-profile.png",
                        Gender.MALE, null, true));
    }
}
