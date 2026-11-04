package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;

/**
 * place 도메인을 부르는 통로. 구현(어댑터)만 place 패키지를 안다.
 */
public interface PlacePort {

    /** 노출 대상(검수 승인, 폐업 아님, 삭제 안 됨, 활성 지역, 유형 확정)인 장소. 아니거나 없으면 비어 있다. */
    Optional<ExposedPlaceResult> find(UUID placeId);
}
