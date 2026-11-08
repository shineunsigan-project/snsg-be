package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;

/**
 * place 도메인을 부르는 통로. 구현(어댑터)만 place 패키지를 안다.
 */
public interface PlacePort {

    /** 노출 대상(검수 승인, 폐업 아님, 삭제 안 됨, 활성 지역, 유형 확정)인 장소. 아니거나 없으면 비어 있다. */
    Optional<ExposedPlaceResult> find(UUID placeId);

    /**
     * 그 유형이고 regionCodes에 든 지역의 노출 장소. regionCodes가 비면 활성 지역 전체다.
     * 비활성 지역이나 없는 코드는 오류 없이 빠진다. 지역 노출 순서(display_order), 장소명 순이다.
     */
    List<ExposedPlaceResult> findExposed(ActivityPlaceType placeType, Collection<String> regionCodes);
}
