package com.shineunsigan.snsg.place.application.port.in;

import java.util.List;
import java.util.Optional;

import com.shineunsigan.snsg.place.application.dto.result.RegionResult;

/**
 * 타 도메인이 지역을 찾는 입구. 비활성 지역도 돌려준다. 활성 여부로 무엇을 할지는 호출하는 쪽이 정한다.
 */
public interface FindRegionUseCase {

    /** 없는 코드면 비어 있다. */
    Optional<RegionResult> find(String code);

    /** display_order 오름차순. */
    List<RegionResult> findAll();
}
