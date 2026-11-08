package com.shineunsigan.snsg.schedule.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.port.out.PlacePort;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 장소 선택 화면의 장소 후보. 활동이 고를 수 있는 유형의 노출 장소를 지역 조건으로 찾는다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인(place) 호출을 트랜잭션 밖에 두기 위해서다.
 * 활동을 지역보다 먼저 본다. 둘 다 틀리면 INVALID_CATEGORY다.
 */
@Service
@RequiredArgsConstructor
public class ListPlaceCandidatesFacade {

    private final ActivityCategoryQueryService activityCategoryQueryService;
    private final RegionPort regionPort;
    private final PlacePort placePort;

    /**
     * @param regionCode 지역 코드. null이면 지역을 고르지 않은 것이라 활성 지역 전체다.
     *                   빈 문자열·공백은 고르지 않은 것이 아니라 틀린 지역이다.
     */
    public List<ExposedPlaceResult> list(UUID categoryId, String regionCode) {
        ActivityPlaceType placeType = findActivityPlaceType(categoryId);
        if (regionCode == null) {
            return placePort.findExposed(placeType, List.of());
        }
        validateActiveRegion(regionCode);
        return placePort.findExposed(placeType, List.of(regionCode));
    }

    private ActivityPlaceType findActivityPlaceType(UUID categoryId) {
        return activityCategoryQueryService.findActivePlaceType(categoryId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.INVALID_CATEGORY));
    }

    private void validateActiveRegion(String regionCode) {
        if (regionCode.isBlank() || !isActiveRegion(regionCode)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_REGION);
        }
    }

    private boolean isActiveRegion(String regionCode) {
        return regionPort.find(regionCode).map(ScheduleRegionResult::active).orElse(false);
    }
}
