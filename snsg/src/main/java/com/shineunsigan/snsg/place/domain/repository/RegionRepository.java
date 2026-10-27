package com.shineunsigan.snsg.place.domain.repository;

import java.util.List;
import java.util.Optional;

import com.shineunsigan.snsg.place.domain.model.Region;

/**
 * 목록은 모두 display_order 오름차순이다.
 */
public interface RegionRepository {

    List<Region> findAllActive();

    /** 비활성 지역도 포함한다. */
    List<Region> findAll();

    /** 비활성 지역도 찾는다. */
    Optional<Region> findByCode(String code);
}
