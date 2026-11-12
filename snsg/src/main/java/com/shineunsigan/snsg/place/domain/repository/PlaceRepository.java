package com.shineunsigan.snsg.place.domain.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;

/**
 * 조회는 삭제되지 않은 행만 본다.
 * <p>
 * 노출 대상은 APPROVED이고 폐업하지 않았으며, 유형이 정해져 있고 지역이 활성인 장소다.
 * 폐업 처리의 지역 판정은 화면 값이 아니라 원천 값({@code sourceRegionCode}) 기준이다.
 */
public interface PlaceRepository {

    Place save(Place place);

    /** 노출 대상일 때만 준다. */
    Optional<Place> findExposed(UUID id);

    /**
     * 노출 대상 중 그 유형이고 regionCodes에 든 지역의 장소. regionCodes가 비면 활성 지역 전체다.
     * 지역 display_order, 장소명 순이다.
     */
    List<Place> findAllExposed(PlaceType placeType, Collection<String> regionCodes);

    /** sourceIds 중 하나를 원천 식별자로 가진 행. 원천은 가리지 않는다. */
    List<Place> findAllBySourceIds(Collection<String> sourceIds);

    /**
     * 폐업 처리 대상: 그 원천의 CANDIDATE·APPROVED 중 폐업하지 않았고, 원천 지역이 regionCodes에 들며,
     * 마지막 확인이 before보다 이른 행. MANUAL은 원천이 없으므로 대상이 아니다.
     */
    List<Place> findAllCloseTargets(PlaceSource source, Collection<String> regionCodes, LocalDateTime before);

    /**
     * 원천 지역별로 그 원천의 CANDIDATE·APPROVED 중 폐업하지 않은 행 수. 행이 없는 지역은 빠진다.
     */
    Map<String, Long> countOpenBySourceRegion(PlaceSource source, Collection<String> regionCodes);

    /**
     * 그 원천의 sourceIds 행의 마지막 확인 시각만 syncedAt으로 바꾼다. 다른 값과 폐업 여부는 그대로다.
     * 적재 대상에서 걸러졌지만 원천에는 있는 행을 폐업으로 오인하지 않게 한다. 영속성 컨텍스트를 거치지 않는 일괄 UPDATE다.
     */
    void markSynced(PlaceSource source, Collection<String> sourceIds, LocalDateTime syncedAt);
}
