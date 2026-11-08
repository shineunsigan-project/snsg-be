package com.shineunsigan.snsg.schedule.infrastructure.client;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.place.application.dto.result.PlaceResult;
import com.shineunsigan.snsg.place.application.port.in.FindPlaceUseCase;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.application.port.out.PlacePort;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;

import lombok.RequiredArgsConstructor;

/**
 * schedule에서 place 도메인을 부르는 자리. 여기서만 place 패키지를 import한다.
 * <p>
 * 장소 유형은 place와 schedule이 각자 가진다. 변환은 모든 값을 나열한 switch로 해서, place에 유형이 늘면
 * 컴파일이 깨져 변환 누락을 잡는다. 유형 미정(UNCLASSIFIED)은 노출 대상이 아니므로 장소가 없는 것으로 본다.
 * 이름을 schedule 접두로 둔다. 다른 도메인의 어댑터와 빈 이름이 겹치지 않게 하기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class SchedulePlaceAdapter implements PlacePort {

    private final FindPlaceUseCase findPlaceUseCase;

    @Override
    public Optional<ExposedPlaceResult> find(UUID placeId) {
        return findPlaceUseCase.find(placeId).flatMap(this::toExposedPlace);
    }

    /** place가 준 순서(지역 display_order, 장소명)를 그대로 둔다. */
    @Override
    public List<ExposedPlaceResult> findExposed(ActivityPlaceType placeType, Collection<String> regionCodes) {
        return findPlaceUseCase.findExposed(toPlaceType(placeType), regionCodes).stream()
                .flatMap(place -> toExposedPlace(place).stream())
                .toList();
    }

    private Optional<ExposedPlaceResult> toExposedPlace(PlaceResult place) {
        return toActivityPlaceType(place.placeType())
                .map(placeType -> new ExposedPlaceResult(place.id(), placeType, place.name(), place.roadAddress(),
                        place.addressDetail(), place.latitude(), place.longitude(), place.regionCode()));
    }

    private Optional<ActivityPlaceType> toActivityPlaceType(PlaceType placeType) {
        return switch (placeType) {
            case BOARD_GAME_CAFE -> Optional.of(ActivityPlaceType.BOARD_GAME_CAFE);
            case ESCAPE_ROOM -> Optional.of(ActivityPlaceType.ESCAPE_ROOM);
            case PARK -> Optional.of(ActivityPlaceType.PARK);
            case UNCLASSIFIED -> Optional.empty();
        };
    }

    private PlaceType toPlaceType(ActivityPlaceType placeType) {
        return switch (placeType) {
            case BOARD_GAME_CAFE -> PlaceType.BOARD_GAME_CAFE;
            case ESCAPE_ROOM -> PlaceType.ESCAPE_ROOM;
            case PARK -> PlaceType.PARK;
        };
    }
}
