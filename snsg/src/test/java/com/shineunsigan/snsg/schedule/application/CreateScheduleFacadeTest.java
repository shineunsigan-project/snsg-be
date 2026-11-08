package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.application.port.out.PlacePort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;

/**
 * 개설 전 확인 순서(활성 사용자 → 활동 → 장소)와 실패 시 저장으로 넘어가지 않는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class CreateScheduleFacadeTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID PLACE_ID = UUID.randomUUID();
    private static final CreateScheduleCommand COMMAND = new CreateScheduleCommand(USER_ID, CATEGORY_ID, PLACE_ID,
            "보드게임 같이 해요", DurationType.NORMAL, "처음 오시는 분도 환영해요.", LocalDateTime.of(2026, 11, 11, 19, 30), 6);

    @Mock
    private UserPort userPort;

    @Mock
    private PlacePort placePort;

    @Mock
    private ActivityCategoryQueryService activityCategoryQueryService;

    @Mock
    private ScheduleCommandService scheduleCommandService;

    @InjectMocks
    private CreateScheduleFacade createScheduleFacade;

    @Test
    @DisplayName("탈퇴한 사용자면 USER_NOT_ACTIVE이고 활동·장소를 조회하지 않는다")
    void inactiveUser() {
        when(userPort.isActive(USER_ID)).thenReturn(false);

        assertError(ScheduleErrorCode.USER_NOT_ACTIVE);
        verifyNoInteractions(activityCategoryQueryService, placePort, scheduleCommandService);
    }

    @Test
    @DisplayName("없거나 비활성인 활동이면 INVALID_CATEGORY이고 장소를 조회하지 않는다")
    void inactiveCategory() {
        when(userPort.isActive(USER_ID)).thenReturn(true);
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID)).thenReturn(Optional.empty());

        assertError(ScheduleErrorCode.INVALID_CATEGORY);
        verifyNoInteractions(placePort, scheduleCommandService);
    }

    @Test
    @DisplayName("노출 대상 장소가 없으면 INVALID_PLACE")
    void placeNotFound() {
        when(userPort.isActive(USER_ID)).thenReturn(true);
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID))
                .thenReturn(Optional.of(ActivityPlaceType.BOARD_GAME_CAFE));
        when(placePort.find(PLACE_ID)).thenReturn(Optional.empty());

        assertError(ScheduleErrorCode.INVALID_PLACE);
        verifyNoInteractions(scheduleCommandService);
    }

    @Test
    @DisplayName("장소 유형이 활동의 장소 유형과 다르면 INVALID_PLACE")
    void placeTypeMismatch() {
        when(userPort.isActive(USER_ID)).thenReturn(true);
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID))
                .thenReturn(Optional.of(ActivityPlaceType.BOARD_GAME_CAFE));
        when(placePort.find(PLACE_ID)).thenReturn(Optional.of(place(ActivityPlaceType.ESCAPE_ROOM)));

        assertError(ScheduleErrorCode.INVALID_PLACE);
        verifyNoInteractions(scheduleCommandService);
    }

    @Test
    @DisplayName("확인을 모두 통과하면 장소 값을 사본으로 넘겨 저장하고 일정 id를 돌려준다")
    void createsWithPlaceSnapshot() {
        UUID scheduleId = UUID.randomUUID();
        when(userPort.isActive(USER_ID)).thenReturn(true);
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID))
                .thenReturn(Optional.of(ActivityPlaceType.BOARD_GAME_CAFE));
        when(placePort.find(PLACE_ID)).thenReturn(Optional.of(place(ActivityPlaceType.BOARD_GAME_CAFE)));
        when(scheduleCommandService.create(any(), any())).thenReturn(scheduleId);

        assertThat(createScheduleFacade.create(COMMAND)).isEqualTo(scheduleId);

        ArgumentCaptor<PlaceSnapshot> snapshot = ArgumentCaptor.forClass(PlaceSnapshot.class);
        verify(scheduleCommandService).create(eq(COMMAND), snapshot.capture());
        assertThat(snapshot.getValue())
                .extracting(PlaceSnapshot::getPlaceName, PlaceSnapshot::getPlaceRoadAddress,
                        PlaceSnapshot::getPlaceAddressDetail, PlaceSnapshot::getPlaceLatitude,
                        PlaceSnapshot::getPlaceLongitude, PlaceSnapshot::getRegionCode)
                .containsExactly("보드게임카페", "서울특별시 마포구 와우산로 1", "2층", new BigDecimal("37.5500000"),
                        new BigDecimal("126.9200000"), "11440");
    }

    private void assertError(ScheduleErrorCode errorCode) {
        assertThatThrownBy(() -> createScheduleFacade.create(COMMAND))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    private static ExposedPlaceResult place(ActivityPlaceType placeType) {
        return new ExposedPlaceResult(PLACE_ID, placeType, "보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
                new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440");
    }
}
