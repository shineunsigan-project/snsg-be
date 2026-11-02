package com.shineunsigan.snsg.place.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 원천 적재 결과를 places에 반영한다. 적재 배치가 페이지마다 {@link #upsert}를, 원천마다 {@link #closeMissing}을 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceCommandService {

    private final PlaceRepository placeRepository;
    private final PlaceProperties placeProperties;

    /**
     * 원천에서 확인한 행을 반영한다. 처음 보는 (source, sourceId)는 검수 대기 후보로 만들고,
     * 이미 있으면 원천 값을 갱신하고 폐업을 푼다. 화면 값은 검수 전 후보만 원천을 따른다.
     */
    @Transactional
    public void upsert(List<UpsertPlaceCommand> rows) {
        LocalDateTime syncedAt = LocalDateTime.now();
        Map<SourceKey, Place> existing = findExisting(rows);
        for (UpsertPlaceCommand row : rows) {
            Place place = existing.get(SourceKey.of(row));
            if (place == null) {
                existing.put(SourceKey.of(row), create(row, syncedAt));
            } else {
                confirm(place, row, syncedAt);
            }
        }
    }

    /**
     * 완주한 지역에서 jobStartedAt 이후 다시 확인되지 않은 CANDIDATE·APPROVED를 폐업으로 돌린다.
     * 지역 판정은 원천 지역 기준이다. 한 지역에서 닫을 행의 비율이 {@code place.close-max-ratio}를 넘으면
     * 원천 장애로 보고 그 지역은 하나도 닫지 않는다. 다른 지역은 계속 처리한다.
     *
     * @param completedRegionCodes 이번 적재에서 모든 페이지를 받은 지역
     * @param jobStartedAt         적재 배치 시작 시각. 이후에 반영된 행은 다시 확인된 것이다
     */
    @Transactional
    public void closeMissing(PlaceSource source, Collection<String> completedRegionCodes, LocalDateTime jobStartedAt) {
        if (completedRegionCodes.isEmpty()) {
            return;
        }
        Map<String, List<Place>> targetsByRegion = placeRepository
                .findAllCloseTargets(source, completedRegionCodes, jobStartedAt).stream()
                .collect(Collectors.groupingBy(Place::getSourceRegionCode));
        Map<String, Long> openCounts = placeRepository.countOpenBySourceRegion(source, completedRegionCodes);
        targetsByRegion.forEach((regionCode, targets) ->
                closeRegion(source, regionCode, targets, openCounts.get(regionCode)));
    }

    private Map<SourceKey, Place> findExisting(List<UpsertPlaceCommand> rows) {
        List<String> sourceIds = rows.stream().map(UpsertPlaceCommand::sourceId).toList();
        Map<SourceKey, Place> existing = new HashMap<>();
        for (Place place : placeRepository.findAllBySourceIds(sourceIds)) {
            existing.put(new SourceKey(place.getSource(), place.getSourceId()), place);
        }
        return existing;
    }

    private Place create(UpsertPlaceCommand row, LocalDateTime syncedAt) {
        return placeRepository.save(Place.createCandidate(row.source(), row.sourceId(), row.placeType(),
                row.name(), row.roadAddress(), row.addressDetail(),
                row.latitude(), row.longitude(), row.regionCode(), syncedAt));
    }

    private void confirm(Place place, UpsertPlaceCommand row, LocalDateTime syncedAt) {
        boolean needsReview = place.confirmSource(row.name(), row.roadAddress(), row.addressDetail(),
                row.latitude(), row.longitude(), row.regionCode(), syncedAt);
        if (needsReview) {
            log.warn("승인된 장소의 원천 값이 바뀌었다. 이전·상호 변경인지 검수가 필요하다. id={}, source={}, sourceId={}",
                    place.getId(), place.getSource(), place.getSourceId());
        }
    }

    private void closeRegion(PlaceSource source, String regionCode, List<Place> targets, long openCount) {
        if (exceedsCloseMaxRatio(targets.size(), openCount)) {
            log.warn("폐업 대상이 너무 많아 이 지역은 폐업 처리하지 않는다. source={}, regionCode={}, 대상 {} / 열린 행 {}, 상한 비율 {}",
                    source, regionCode, targets.size(), openCount, placeProperties.closeMaxRatio());
            return;
        }
        targets.forEach(Place::close);
    }

    /** 비율이 상한과 같으면 넘지 않은 것이다. 소수 오차가 없도록 곱셈으로 비교한다. */
    private boolean exceedsCloseMaxRatio(int targetCount, long openCount) {
        BigDecimal limit = placeProperties.closeMaxRatio().multiply(BigDecimal.valueOf(openCount));
        return BigDecimal.valueOf(targetCount).compareTo(limit) > 0;
    }

    private record SourceKey(PlaceSource source, String sourceId) {

        static SourceKey of(UpsertPlaceCommand row) {
            return new SourceKey(row.source(), row.sourceId());
        }
    }
}
