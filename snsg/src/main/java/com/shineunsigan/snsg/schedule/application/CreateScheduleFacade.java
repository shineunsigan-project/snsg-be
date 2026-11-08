package com.shineunsigan.snsg.schedule.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.application.port.out.PlacePort;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 일정을 연다. 사용자(user)·장소(place) 확인을 거쳐 저장은 {@link ScheduleCommandService}에 맡긴다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인 호출을 트랜잭션 밖에 두기 위해서다.
 * 장소는 place에서 읽어 사본으로 넘긴다. 요청으로 장소명·주소·좌표를 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CreateScheduleFacade {

    private final UserPort userPort;
    private final PlacePort placePort;
    private final ActivityCategoryQueryService activityCategoryQueryService;
    private final ScheduleCommandService scheduleCommandService;

    public UUID create(CreateScheduleCommand command) {
        validateActiveUser(command.userId());
        ActivityPlaceType placeType = findActivityPlaceType(command.categoryId());
        ExposedPlaceResult place = findPlace(command.placeId(), placeType);
        return scheduleCommandService.create(command, toSnapshot(place));
    }

    private void validateActiveUser(UUID userId) {
        if (!userPort.isActive(userId)) {
            throw new BusinessException(ScheduleErrorCode.USER_NOT_ACTIVE);
        }
    }

    private ActivityPlaceType findActivityPlaceType(UUID categoryId) {
        return activityCategoryQueryService.findActivePlaceType(categoryId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.INVALID_CATEGORY));
    }

    /** 노출 대상이 아니거나, 활동이 고를 수 있는 유형이 아니면 고를 수 없는 장소다. */
    private ExposedPlaceResult findPlace(UUID placeId, ActivityPlaceType placeType) {
        return placePort.find(placeId)
                .filter(place -> place.placeType() == placeType)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.INVALID_PLACE));
    }

    private PlaceSnapshot toSnapshot(ExposedPlaceResult place) {
        return PlaceSnapshot.of(place.name(), place.roadAddress(), place.addressDetail(), place.latitude(),
                place.longitude(), place.regionCode());
    }
}
