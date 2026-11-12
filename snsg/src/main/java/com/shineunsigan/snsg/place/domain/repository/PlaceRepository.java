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
import com.shineunsigan.snsg.place.domain.model.PlaceCursor;
import com.shineunsigan.snsg.place.domain.model.PlaceSearchCondition;

/**
 * 조회는 삭제되지 않은 행만 본다.
 * <p>
 * 노출 대상은 APPROVED이고 폐업하지 않았으며, 유형이 정해져 있고 지역이 활성인 장소다.
 * 폐업 처리의 지역 판정은 화면 값이 아니라 원천 값({@code sourceRegionCode}) 기준이다.
 * <p>
 * 적재(기존 행 조회·폐업 대상 조회)와 어드민 수정은 행을 {@code SELECT … FOR UPDATE}로 잠가 읽는다.
 * 둘이 같은 행을 동시에 고치면 뒤에 커밋한 쪽이 전체 컬럼을 덮어 앞의 변경이 사라지기 때문이다.
 */
public interface PlaceRepository {

    Place save(Place place);

    /** 바로 반영해, 감사 값(수정 시각·수정한 주체)이 채워진 엔티티를 준다. */
    Place saveAndFlush(Place place);

    /** 노출 여부와 상관없이 삭제되지 않은 장소. 어드민 상세용이다. */
    Optional<Place> findById(UUID id);

    /** 삭제되지 않은 장소를 잠가 읽는다. 어드민 수정용. 그 트랜잭션에서 그 행을 처음 읽는 조회여야 한다. */
    Optional<Place> findByIdForUpdate(UUID id);

    /**
     * 어드민 목록. 노출 여부와 상관없이 삭제되지 않은 장소 중 조건에 맞는 것을 (생성 시각, id) 오름차순으로 준다.
     *
     * @param after 이 커서보다 뒤의 장소만. null이면 처음부터
     * @param limit 최대 개수
     */
    List<Place> findAllForReview(PlaceSearchCondition condition, PlaceCursor after, int limit);

    /** 노출 대상일 때만 준다. */
    Optional<Place> findExposed(UUID id);

    /**
     * 노출 대상 중 그 유형이고 regionCodes에 든 지역의 장소. regionCodes가 비면 활성 지역 전체다.
     * 지역 display_order, 장소명 순이다.
     */
    List<Place> findAllExposed(PlaceType placeType, Collection<String> regionCodes);

    /** sourceIds 중 하나를 원천 식별자로 가진 행. 원천은 가리지 않는다. 잠가 읽는다. */
    List<Place> findAllBySourceIds(Collection<String> sourceIds);

    /**
     * 폐업 처리 대상: 그 원천의 CANDIDATE·APPROVED 중 폐업하지 않았고, 원천 지역이 regionCodes에 들며,
     * 마지막 확인이 before보다 이른 행. MANUAL은 원천이 없으므로 대상이 아니다. 잠가 읽는다.
     */
    List<Place> findAllCloseTargets(PlaceSource source, Collection<String> regionCodes, LocalDateTime before);

    /**
     * 원천 지역별로 그 원천의 CANDIDATE·APPROVED 중 폐업하지 않은 행 수. 행이 없는 지역은 빠진다.
     */
    Map<String, Long> countOpenBySourceRegion(PlaceSource source, Collection<String> regionCodes);

    /**
     * 그 원천의 sourceIds 행의 마지막 확인 시각만 syncedAt으로 바꾼다. 다른 값과 폐업 여부는 그대로다.
     * 적재 대상에서 걸러졌지만 원천에는 있는 행을 폐업으로 오인하지 않게 한다. 영속성 컨텍스트를 거치지 않는 일괄 UPDATE다.
     * 수정한 주체는 스케줄러이고, 검수자·검수 시각은 바꾸지 않는다.
     */
    void markSynced(PlaceSource source, Collection<String> sourceIds, LocalDateTime syncedAt);
}
