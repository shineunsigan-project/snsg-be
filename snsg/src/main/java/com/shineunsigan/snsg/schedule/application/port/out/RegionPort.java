package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.Optional;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;

/**
 * place 도메인의 지역을 찾는 통로. 구현(어댑터)만 place 패키지를 안다.
 */
public interface RegionPort {

    /** 비활성 지역도 돌려준다. 활성 여부로 무엇을 할지는 schedule이 정한다. 없는 코드면 비어 있다. */
    Optional<ScheduleRegionResult> find(String code);
}
