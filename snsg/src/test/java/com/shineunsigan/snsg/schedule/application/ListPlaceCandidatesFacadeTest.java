package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.port.out.PlacePort;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 확인 순서(활동 → 지역)와, 실패하면 장소를 조회하지 않는지, 지역 조건이 어떻게 넘어가는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class ListPlaceCandidatesFacadeTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final String REGION = "11440";

    @Mock
    private ActivityCategoryQueryService activityCategoryQueryService;

    @Mock
    private RegionPort regionPort;

    @Mock
    private PlacePort placePort;

    @InjectMocks
    private ListPlaceCandidatesFacade listPlaceCandidatesFacade;

    @Test
    @DisplayName("없거나 비활성인 활동이면 INVALID_CATEGORY이고 지역·장소를 조회하지 않는다")
    void inactiveCategory() {
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID)).thenReturn(Optional.empty());

        assertError(REGION, ScheduleErrorCode.INVALID_CATEGORY);
        verifyNoInteractions(regionPort, placePort);
    }

    @Test
    @DisplayName("활동과 지역이 모두 틀리면 활동을 먼저 봐서 INVALID_CATEGORY")
    void categoryCheckedBeforeRegion() {
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID)).thenReturn(Optional.empty());

        assertError("99999", ScheduleErrorCode.INVALID_CATEGORY);
        verifyNoInteractions(regionPort, placePort);
    }

    @Test
    @DisplayName("없는 지역이면 INVALID_REGION이고 장소를 조회하지 않는다")
    void unknownRegion() {
        givenCategory();
        when(regionPort.find("99999")).thenReturn(Optional.empty());

        assertError("99999", ScheduleErrorCode.INVALID_REGION);
        verifyNoInteractions(placePort);
    }

    @Test
    @DisplayName("비활성 지역이면 INVALID_REGION이고 장소를 조회하지 않는다")
    void inactiveRegion() {
        givenCategory();
        when(regionPort.find(REGION)).thenReturn(Optional.of(new ScheduleRegionResult(REGION, false)));

        assertError(REGION, ScheduleErrorCode.INVALID_REGION);
        verifyNoInteractions(placePort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("빈 문자열이나 공백만인 지역 코드는 생략이 아니라 INVALID_REGION이고 장소를 조회하지 않는다")
    void blankRegion(String regionCode) {
        givenCategory();

        assertError(regionCode, ScheduleErrorCode.INVALID_REGION);
        verifyNoInteractions(placePort);
    }

    @Test
    @DisplayName("활성 지역을 지정하면 활동의 장소 유형과 그 지역 하나로 장소를 찾아 그대로 돌려준다")
    void listsInRegion() {
        List<ExposedPlaceResult> places = List.of(place("가"), place("나"));
        givenCategory();
        when(regionPort.find(REGION)).thenReturn(Optional.of(new ScheduleRegionResult(REGION, true)));
        when(placePort.findExposed(ActivityPlaceType.BOARD_GAME_CAFE, List.of(REGION))).thenReturn(places);

        assertThat(listPlaceCandidatesFacade.list(CATEGORY_ID, REGION)).containsExactlyElementsOf(places);
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록을 돌려준다")
    void emptyResult() {
        givenCategory();
        when(regionPort.find(REGION)).thenReturn(Optional.of(new ScheduleRegionResult(REGION, true)));
        when(placePort.findExposed(ActivityPlaceType.BOARD_GAME_CAFE, List.of(REGION))).thenReturn(List.of());

        assertThat(listPlaceCandidatesFacade.list(CATEGORY_ID, REGION)).isEmpty();
    }

    @Test
    @DisplayName("지역을 생략하면 지역을 조회하지 않고 빈 지역 조건(활성 지역 전체)으로 장소를 찾는다")
    void listsAllActiveRegionsWhenOmitted() {
        List<ExposedPlaceResult> places = List.of(place("가"));
        givenCategory();
        when(placePort.findExposed(ActivityPlaceType.BOARD_GAME_CAFE, List.of())).thenReturn(places);

        assertThat(listPlaceCandidatesFacade.list(CATEGORY_ID, null)).containsExactlyElementsOf(places);
        verify(placePort).findExposed(ActivityPlaceType.BOARD_GAME_CAFE, List.of());
        verifyNoInteractions(regionPort);
    }

    private void givenCategory() {
        when(activityCategoryQueryService.findActivePlaceType(CATEGORY_ID))
                .thenReturn(Optional.of(ActivityPlaceType.BOARD_GAME_CAFE));
    }

    private void assertError(String regionCode, ScheduleErrorCode errorCode) {
        assertThatThrownBy(() -> listPlaceCandidatesFacade.list(CATEGORY_ID, regionCode))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }

    private static ExposedPlaceResult place(String name) {
        return new ExposedPlaceResult(UUID.randomUUID(), ActivityPlaceType.BOARD_GAME_CAFE, name,
                "서울특별시 마포구 와우산로 1", "", new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), REGION);
    }
}
