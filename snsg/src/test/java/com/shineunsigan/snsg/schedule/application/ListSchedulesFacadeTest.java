package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardPageResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;

/**
 * 지역 이름 채우기(활성·비활성·없는 지역)와 지역 조회 횟수를 본다.
 */
@ExtendWith(MockitoExtension.class)
class ListSchedulesFacadeTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ScheduleQueryService scheduleQueryService;

    @Mock
    private RegionPort regionPort;

    @InjectMocks
    private ListSchedulesFacade listSchedulesFacade;

    @Test
    @DisplayName("활성·비활성 지역은 시군구 이름, 지역 목록에 없는 코드는 빈 문자열을 채운다. 지역 전체 조회는 한 번이다")
    void fillsRegionNames() {
        ListRecruitingSchedulesCommand command = new ListRecruitingSchedulesCommand(null, null, null, null, null,
                null);
        when(scheduleQueryService.findRecruiting(command)).thenReturn(new ScheduleCardPageResult(
                List.of(card("11440"), card("11410"), card("99999"), card("11440")), "next"));
        when(regionPort.findAll()).thenReturn(List.of(
                new ScheduleRegionResult("11440", "마포구", true),
                new ScheduleRegionResult("11410", "서대문구", false)));

        ScheduleListResult result = listSchedulesFacade.listRecruiting(command);

        assertThat(result.items()).extracting(ScheduleListItemResult::regionName)
                .containsExactly("마포구", "서대문구", "", "마포구");
        assertThat(result.nextCursor()).isEqualTo("next");
        verify(regionPort, times(1)).findAll();
        verify(regionPort, never()).find(any());
    }

    @Test
    @DisplayName("참여한·개설한 목록도 요청한 사용자로 일정을 읽고 지역 이름을 채운다")
    void myListsFillRegionNames() {
        when(scheduleQueryService.findParticipating(USER_ID, "c", 10))
                .thenReturn(new ScheduleCardPageResult(List.of(card("11440")), null));
        when(scheduleQueryService.findHosted(USER_ID, null, null))
                .thenReturn(new ScheduleCardPageResult(List.of(card("11410")), null));
        when(regionPort.findAll()).thenReturn(List.of(
                new ScheduleRegionResult("11440", "마포구", true),
                new ScheduleRegionResult("11410", "서대문구", true)));

        assertThat(listSchedulesFacade.listParticipating(USER_ID, "c", 10).items())
                .extracting(ScheduleListItemResult::regionName).containsExactly("마포구");
        assertThat(listSchedulesFacade.listHosted(USER_ID, null, null).items())
                .extracting(ScheduleListItemResult::regionName).containsExactly("서대문구");
    }

    @Test
    @DisplayName("일정이 0건이면 빈 items와 nextCursor null이고 지역을 조회하지 않는다")
    void emptyPage() {
        when(scheduleQueryService.findHosted(USER_ID, null, null))
                .thenReturn(new ScheduleCardPageResult(List.of(), null));

        ScheduleListResult result = listSchedulesFacade.listHosted(USER_ID, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        verifyNoInteractions(regionPort);
    }

    private static ScheduleCardResult card(String regionCode) {
        return new ScheduleCardResult(UUID.randomUUID(), UUID.randomUUID(), "보드게임", "보드게임 같이 해요", regionCode,
                LocalDateTime.of(2026, 11, 14, 19, 30), 1, 6, 5, DisplayStatus.RECRUITING,
                LocalDateTime.of(2026, 11, 10, 9, 0));
    }
}
