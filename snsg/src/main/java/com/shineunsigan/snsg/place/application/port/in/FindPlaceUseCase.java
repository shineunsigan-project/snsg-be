package com.shineunsigan.snsg.place.application.port.in;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.place.application.dto.result.PlaceResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 타 도메인이 노출 장소를 찾는 입구. 노출 대상(검수 승인, 폐업 아님, 유형 확정, 삭제 안 됨, 활성 지역)만 준다.
 */
public interface FindPlaceUseCase {

    /** 노출 대상이 아니거나 없는 id면 비어 있다. */
    Optional<PlaceResult> find(UUID placeId);

    /**
     * 그 유형이고 regionCodes에 든 지역의 노출 장소. regionCodes가 비면 활성 지역 전체다.
     * 비활성 지역이나 없는 코드는 오류 없이 빠진다. 지역 display_order, 장소명 순이다.
     */
    List<PlaceResult> findExposed(PlaceType placeType, Collection<String> regionCodes);
}
