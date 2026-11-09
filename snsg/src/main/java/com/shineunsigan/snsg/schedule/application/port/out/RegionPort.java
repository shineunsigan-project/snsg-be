package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.List;
import java.util.Optional;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;

/**
 * place 도메인의 지역을 찾는 통로. 구현(어댑터)만 place 패키지를 안다.
 */
public interface RegionPort {

    /** 비활성 지역도 돌려준다. 활성 여부로 무엇을 할지는 schedule이 정한다. 없는 코드면 비어 있다. */
    Optional<ScheduleRegionResult> find(String code);

    /** 비활성 지역까지 모든 지역. 일정 목록의 지역 이름을 한 번에 채울 때 쓴다. */
    List<ScheduleRegionResult> findAll();
}
