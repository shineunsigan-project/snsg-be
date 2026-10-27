package com.shineunsigan.snsg.place.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.place.application.dto.result.RegionResult;
import com.shineunsigan.snsg.place.application.port.in.FindRegionUseCase;
import com.shineunsigan.snsg.place.domain.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionQueryService implements FindRegionUseCase {

    private final RegionRepository regionRepository;

    /** 사용자에게 보여줄 지역. 활성 지역만 display_order 오름차순으로. */
    public List<RegionResult> findActive() {
        return regionRepository.findAllActive().stream()
                .map(RegionResult::from)
                .toList();
    }

    @Override
    public Optional<RegionResult> find(String code) {
        return regionRepository.findByCode(code).map(RegionResult::from);
    }

    @Override
    public List<RegionResult> findAll() {
        return regionRepository.findAll().stream()
                .map(RegionResult::from)
                .toList();
    }
}
