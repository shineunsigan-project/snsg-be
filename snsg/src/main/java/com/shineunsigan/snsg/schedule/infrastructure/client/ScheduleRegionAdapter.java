package com.shineunsigan.snsg.schedule.infrastructure.client;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.place.application.dto.result.RegionResult;
import com.shineunsigan.snsg.place.application.port.in.FindRegionUseCase;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleRegionResult;
import com.shineunsigan.snsg.schedule.application.port.out.RegionPort;

import lombok.RequiredArgsConstructor;

/**
 * schedule에서 place 도메인의 지역을 부르는 자리. 여기서만 place 패키지를 import한다.
 * 이름을 schedule 접두로 둔다. 다른 도메인의 어댑터와 빈 이름이 겹치지 않게 하기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleRegionAdapter implements RegionPort {

    private final FindRegionUseCase findRegionUseCase;

    @Override
    public Optional<ScheduleRegionResult> find(String code) {
        return findRegionUseCase.find(code).map(ScheduleRegionAdapter::toResult);
    }

    @Override
    public List<ScheduleRegionResult> findAll() {
        return findRegionUseCase.findAll().stream()
                .map(ScheduleRegionAdapter::toResult)
                .toList();
    }

    private static ScheduleRegionResult toResult(RegionResult region) {
        return new ScheduleRegionResult(region.code(), region.sigunguName(), region.active());
    }
}
