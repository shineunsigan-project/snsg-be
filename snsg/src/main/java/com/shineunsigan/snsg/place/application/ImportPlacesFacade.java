package com.shineunsigan.snsg.place.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourcePageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourceRow;
import com.shineunsigan.snsg.place.application.dto.result.RegionResult;
import com.shineunsigan.snsg.place.application.port.out.PlaceImportLockPort;
import com.shineunsigan.snsg.place.application.port.out.PublicPlaceSourcePort;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.model.ImportablePlace;
import com.shineunsigan.snsg.place.domain.model.PlaceImportPolicy;
import com.shineunsigan.snsg.place.domain.model.SourceStore;

import lombok.extern.slf4j.Slf4j;

/**
 * 상가정보 장소 적재(JOB-04). 활성 지역마다 원천 페이지를 끝까지 받아 반영하고, 모든 페이지를 받은 지역만 폐업 처리한다.
 * <p>
 * 원천 호출은 트랜잭션 밖이다. 반영은 페이지마다 {@link PlaceCommandService#reflectPage} 한 번(트랜잭션 하나),
 * 폐업 처리는 모든 지역을 받은 뒤 {@link PlaceCommandService#closeMissing} 한 번이다.
 * 한 지역의 실패는 그 지역만 미완주로 남기고 다음 지역으로 넘어간다. 이미 반영한 페이지는 되돌리지 않는다.
 * 여러 서버가 함께 돌지 않도록 실행 전체를 {@link PlaceImportLockPort} 안에서 돌린다.
 * <p>
 * 요청 URL에는 인증키가 들어 있어 로그에 남기지 않는다. 실패 이유는 원천 결과가 담은 이유만 쓴다.
 */
@Slf4j
@Service
public class ImportPlacesFacade {

    private static final PlaceSource SOURCE = PlaceSource.SEMAS;

    private final PlaceImportLockPort placeImportLockPort;
    private final PublicPlaceSourcePort publicPlaceSourcePort;
    private final RegionQueryService regionQueryService;
    private final PlaceCommandService placeCommandService;
    private final PlaceImportPolicy placeImportPolicy;
    private final int maxAttempts;

    public ImportPlacesFacade(PlaceImportLockPort placeImportLockPort, PublicPlaceSourcePort publicPlaceSourcePort,
            RegionQueryService regionQueryService, PlaceCommandService placeCommandService,
            PlaceProperties placeProperties) {
        this.placeImportLockPort = placeImportLockPort;
        this.publicPlaceSourcePort = publicPlaceSourcePort;
        this.regionQueryService = regionQueryService;
        this.placeCommandService = placeCommandService;
        this.placeImportPolicy = new PlaceImportPolicy(placeProperties.semasCategoryCodes(),
                placeProperties.nameKeywords(), placeProperties.typeKeywords());
        this.maxAttempts = placeProperties.importMaxAttempts();
    }

    /** 잠금을 잡지 못하면(다른 실행이 도는 중) 아무것도 하지 않고 건너뛴다. */
    public void importPlaces() {
        boolean ran = placeImportLockPort.runExclusively(this::importAll);
        if (!ran) {
            log.info("장소 적재: 다른 실행이 잠금을 쥐고 있어 이번 실행은 건너뛴다. source={}", SOURCE);
        }
    }

    private void importAll() {
        LocalDateTime startedAt = LocalDateTime.now();
        List<RegionResult> regions = regionQueryService.findActive();
        log.info("장소 적재 시작. source={}, startedAt={}, 활성 지역 {}곳", SOURCE, startedAt, regions.size());

        List<String> completedRegionCodes = new ArrayList<>();
        for (RegionResult region : regions) {
            if (importRegion(region.code())) {
                completedRegionCodes.add(region.code());
            }
        }
        placeCommandService.closeMissing(SOURCE, completedRegionCodes, startedAt);

        log.info("장소 적재 종료. source={}, 완주 지역 {} / {}곳 {}, 걸린 시간 {}초", SOURCE, completedRegionCodes.size(),
                regions.size(), completedRegionCodes, Duration.between(startedAt, LocalDateTime.now()).toSeconds());
    }

    /** 지역 하나의 모든 페이지를 받아 반영한다. 완주했으면 true. */
    private boolean importRegion(String regionCode) {
        RegionProgress progress = new RegionProgress(regionCode);
        try {
            String incompleteReason = importPages(progress);
            if (incompleteReason != null) {
                log.warn("장소 적재 미완주. 이 지역은 폐업 처리하지 않는다. regionCode={}, reason={}, {}",
                        regionCode, incompleteReason, progress);
                return false;
            }
            log.info("장소 적재 지역 완주. regionCode={}, {}", regionCode, progress);
            return true;
        } catch (RuntimeException exception) {
            log.warn("장소 적재 미완주. 이 지역은 폐업 처리하지 않는다. regionCode={}, reason=반영 실패({}), {}",
                    regionCode, exception.getClass().getSimpleName(), progress, exception);
            return false;
        }
    }

    /**
     * 페이지 수는 첫 페이지의 totalCount로 정한다. 중간 페이지가 0건이면 더 부르지 않는다.
     *
     * @return 미완주 이유. 완주했으면 null
     */
    private String importPages(RegionProgress progress) {
        PlaceSourcePageResult first = fetchWithRetry(progress.regionCode, 1);
        if (first.noData()) {
            return "0건(" + first.failureReason() + ")";
        }
        if (!first.success()) {
            return "1페이지 받기 실패(" + first.failureReason() + ")";
        }
        progress.totalCount = first.totalCount();
        if (progress.totalCount == 0) {
            return "0건";
        }
        if (first.rows().isEmpty()) {
            return "1페이지가 0건이라 건수 불일치";
        }
        reflect(progress, first.rows());

        int pageSize = PublicPlaceSourcePort.PAGE_SIZE;
        int pageCount = (progress.totalCount + pageSize - 1) / pageSize;
        for (int pageNo = 2; pageNo <= pageCount; pageNo++) {
            PlaceSourcePageResult page = fetchWithRetry(progress.regionCode, pageNo);
            if (page.noData()) {
                return pageNo + "페이지가 0건이라 건수 불일치(" + page.failureReason() + ")";
            }
            if (!page.success()) {
                return pageNo + "페이지 받기 실패(" + page.failureReason() + ")";
            }
            if (page.rows().isEmpty()) {
                return pageNo + "페이지가 0건이라 건수 불일치";
            }
            reflect(progress, page.rows());
        }
        if (progress.received != progress.totalCount) {
            return "받은 건수가 totalCount와 다름";
        }
        return null;
    }

    /** 받기에 실패하면 같은 페이지를 최대 시도 횟수까지 바로 다시 부른다. "데이터 없음"은 다시 불러도 같아 바로 돌려준다. */
    private PlaceSourcePageResult fetchWithRetry(String regionCode, int pageNo) {
        PlaceSourcePageResult result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = fetch(regionCode, pageNo);
            if (result.success() || result.noData()) {
                return result;
            }
            log.warn("장소 원천 페이지 받기 실패. regionCode={}, pageNo={}, 시도 {}/{}, reason={}",
                    regionCode, pageNo, attempt, maxAttempts, result.failureReason());
        }
        return result;
    }

    /** 포트는 실패를 결과로 주지만, 예상하지 못한 예외도 실패로 다룬다. 예외 메시지에는 요청 URL이 들 수 있어 종류만 남긴다. */
    private PlaceSourcePageResult fetch(String regionCode, int pageNo) {
        try {
            return publicPlaceSourcePort.fetchPage(regionCode, pageNo);
        } catch (RuntimeException exception) {
            return PlaceSourcePageResult.failure(exception.getClass().getSimpleName());
        }
    }

    /** 적재 대상은 반영하고, 걸러진 행은 상가업소번호가 있으면 확인 시각만 갱신한다. */
    private void reflect(RegionProgress progress, List<PlaceSourceRow> rows) {
        List<UpsertPlaceCommand> upserts = new ArrayList<>();
        List<String> confirmedOnly = new ArrayList<>();
        for (PlaceSourceRow row : rows) {
            SourceStore store = row.toSourceStore();
            if (!store.hasSourceId()) {
                continue;
            }
            placeImportPolicy.toImportable(store).ifPresentOrElse(
                    place -> upserts.add(toCommand(place, progress.regionCode)),
                    () -> confirmedOnly.add(store.sourceId().strip()));
        }
        placeCommandService.reflectPage(SOURCE, upserts, confirmedOnly);
        progress.received += rows.size();
        progress.imported += upserts.size();
        progress.filtered += confirmedOnly.size();
    }

    private UpsertPlaceCommand toCommand(ImportablePlace place, String regionCode) {
        return new UpsertPlaceCommand(SOURCE, place.sourceId(), place.name(), place.roadAddress(),
                place.addressDetail(), place.latitude(), place.longitude(), regionCode, place.placeType());
    }

    /** 한 지역의 진행 상황. 로그에 그대로 찍는다. */
    private static final class RegionProgress {

        private final String regionCode;
        private int totalCount;
        private int received;
        private int imported;
        private int filtered;

        private RegionProgress(String regionCode) {
            this.regionCode = regionCode;
        }

        @Override
        public String toString() {
            return "totalCount=" + totalCount + ", 받은 행=" + received + ", 적재 대상=" + imported
                    + ", 걸러진 행=" + filtered;
        }
    }
}
