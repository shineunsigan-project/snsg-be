package com.shineunsigan.snsg.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

/**
 * upsert가 행마다 새로 만들지 갱신할지 가르고 승인 장소의 원천 변경을 경고하는지,
 * 폐업 처리가 지역마다 안전장치 비율을 따로 판정하는지 본다. 대상 선택 쿼리는 PlaceRepositoryImplTest가 본다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PlaceCommandServiceTest {

    private static final String REVIEW_WARNING = "승인된 장소의 원천 값이 바뀌었다";
    private static final String CLOSE_WARNING = "폐업 대상이 너무 많아";
    private static final LocalDateTime FIRST_SYNC = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final LocalDateTime JOB_STARTED_AT = LocalDateTime.of(2027, 1, 5, 3, 0);
    private static final BigDecimal LAT = new BigDecimal("37.5775410");
    private static final BigDecimal LNG = new BigDecimal("126.9103629");

    @Mock
    private PlaceRepository placeRepository;

    private PlaceCommandService placeCommandService;

    @BeforeEach
    void setUp() {
        placeCommandService = new PlaceCommandService(placeRepository, new PlaceProperties(new BigDecimal("0.3"),
                Duration.ofSeconds(10), 3, List.of(), List.of(), Map.of()));
    }

    private UpsertPlaceCommand row(String sourceId, String name, String addressDetail) {
        return new UpsertPlaceCommand(PlaceSource.SEMAS, sourceId, name, "서울특별시 마포구 원천로 1", addressDetail,
                LAT, LNG, "11440", PlaceType.ESCAPE_ROOM);
    }

    /** 원천 값은 row("TEST-…", "원천이름", "2층")과 같고, 화면 값은 검수에서 고친 행. */
    private Place existing(String sourceId, PlaceStatus status) {
        Place place = Place.createCandidate(PlaceSource.SEMAS, sourceId, PlaceType.ESCAPE_ROOM,
                "원천이름", "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440", FIRST_SYNC);
        ReflectionTestUtils.setField(place, "status", status);
        ReflectionTestUtils.setField(place, "name", "고친이름");
        return place;
    }

    private Place openPlace(String regionCode) {
        Place place = Place.createCandidate(PlaceSource.SEMAS, "TEST-" + regionCode, PlaceType.ESCAPE_ROOM,
                "원천이름", "서울특별시 마포구 원천로 1", "", LAT, LNG, regionCode, FIRST_SYNC);
        ReflectionTestUtils.setField(place, "status", PlaceStatus.APPROVED);
        return place;
    }

    private List<Place> openPlaces(String regionCode, int count) {
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            places.add(openPlace(regionCode));
        }
        return places;
    }

    @Test
    @DisplayName("처음 보는 원천 행은 검수 대기 후보로 새로 만든다")
    void upsertCreatesCandidate() {
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        placeCommandService.upsert(List.of(row("TEST-1", "원천이름", "2층")));

        ArgumentCaptor<Place> saved = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(saved.capture());
        Place place = saved.getValue();
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.CANDIDATE);
        assertThat(place.isClosed()).isFalse();
        assertThat(place.getPlaceType()).isEqualTo(PlaceType.ESCAPE_ROOM);
        assertThat(place).extracting("source", "sourceId", "name", "addressDetail", "sourceName",
                        "sourceAddressDetail", "regionCode", "sourceRegionCode")
                .containsExactly(PlaceSource.SEMAS, "TEST-1", "원천이름", "2층", "원천이름", "2층", "11440", "11440");
        assertThat(place.getLastSyncedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("이미 있는 원천 행은 새로 만들지 않고 그 행에 반영한다")
    void upsertConfirmsExisting() {
        Place place = existing("TEST-1", PlaceStatus.CANDIDATE);
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of(place));

        placeCommandService.upsert(List.of(row("TEST-1", "새이름", "2층")));

        verify(placeRepository, never()).save(any());
        assertThat(place.getName()).isEqualTo("새이름");
        assertThat(place.getLastSyncedAt()).isAfter(FIRST_SYNC);
    }

    @Test
    @DisplayName("승인된 장소의 원천 값이 바뀌면 그 행의 원천 식별자를 담아 경고 로그를 남긴다")
    void upsertWarnsWhenApprovedSourceChanged(CapturedOutput output) {
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of(existing("TEST-1", PlaceStatus.APPROVED)));

        placeCommandService.upsert(List.of(row("TEST-1", "새이름", "2층")));

        assertThat(output).contains("WARN").contains(REVIEW_WARNING).contains("sourceId=TEST-1");
    }

    @Test
    @DisplayName("승인된 장소의 상세주소만 바뀌어도 경고 로그를 남긴다")
    void upsertWarnsWhenApprovedAddressDetailChanged(CapturedOutput output) {
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of(existing("TEST-1", PlaceStatus.APPROVED)));

        placeCommandService.upsert(List.of(row("TEST-1", "원천이름", "3층")));

        assertThat(output).contains(REVIEW_WARNING).contains("sourceId=TEST-1");
    }

    @Test
    @DisplayName("승인된 장소의 원천 값이 그대로면 화면 값과 달라도 경고하지 않는다")
    void upsertDoesNotWarnWhenApprovedSourceSame(CapturedOutput output) {
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of(existing("TEST-1", PlaceStatus.APPROVED)));

        placeCommandService.upsert(List.of(row("TEST-1", "원천이름", "2층")));

        assertThat(output).doesNotContain(REVIEW_WARNING);
    }

    @Test
    @DisplayName("거절된 장소는 원천 값이 바뀌어도 경고하지 않는다")
    void upsertDoesNotWarnWhenRejectedSourceChanged(CapturedOutput output) {
        when(placeRepository.findAllBySourceIds(any())).thenReturn(List.of(existing("TEST-1", PlaceStatus.REJECTED)));

        placeCommandService.upsert(List.of(row("TEST-1", "새이름", "3층")));

        assertThat(output).doesNotContain(REVIEW_WARNING);
    }

    @Test
    @DisplayName("닫을 행의 비율이 상한을 넘는 지역은 하나도 닫지 않고 경고하며, 같은 호출의 다른 지역은 닫는다")
    void closeSkipsRegionOverRatioAndContinues(CapturedOutput output) {
        List<Place> overRatio = openPlaces("99001", 4);
        List<Place> withinRatio = openPlaces("99002", 1);
        List<Place> targets = new ArrayList<>(overRatio);
        targets.addAll(withinRatio);
        when(placeRepository.findAllCloseTargets(PlaceSource.SEMAS, List.of("99001", "99002"), JOB_STARTED_AT))
                .thenReturn(targets);
        when(placeRepository.countOpenBySourceRegion(PlaceSource.SEMAS, List.of("99001", "99002")))
                .thenReturn(Map.of("99001", 10L, "99002", 10L));

        placeCommandService.closeMissing(PlaceSource.SEMAS, List.of("99001", "99002"), JOB_STARTED_AT);

        assertThat(overRatio).noneMatch(Place::isClosed);
        assertThat(withinRatio).allMatch(Place::isClosed);
        assertThat(output).contains("WARN").contains(CLOSE_WARNING).contains("regionCode=99001")
                .doesNotContain("regionCode=99002");
    }

    @Test
    @DisplayName("닫을 행의 비율이 상한과 정확히 같으면 닫고 검수 상태와 값은 그대로 둔다")
    void closeWhenRatioEqualsLimit(CapturedOutput output) {
        List<Place> targets = openPlaces("99001", 3);
        when(placeRepository.findAllCloseTargets(PlaceSource.SEMAS, List.of("99001"), JOB_STARTED_AT))
                .thenReturn(targets);
        when(placeRepository.countOpenBySourceRegion(PlaceSource.SEMAS, List.of("99001")))
                .thenReturn(Map.of("99001", 10L));

        placeCommandService.closeMissing(PlaceSource.SEMAS, List.of("99001"), JOB_STARTED_AT);

        assertThat(targets).allMatch(Place::isClosed);
        assertThat(targets).allMatch(place -> place.getStatus() == PlaceStatus.APPROVED
                && place.getName().equals("원천이름")
                && place.getRoadAddress().equals("서울특별시 마포구 원천로 1"));
        assertThat(output).doesNotContain(CLOSE_WARNING);
    }

    @Test
    @DisplayName("대상인 검수 대기 후보와 승인 장소가 모두 닫히고 각자의 검수 상태와 이름·주소는 그대로다")
    void closeCandidateAndApprovedKeepsStatusAndValues() {
        Place candidate = Place.createCandidate(PlaceSource.SEMAS, "TEST-C", PlaceType.ESCAPE_ROOM,
                "후보이름", "서울특별시 마포구 후보로 1", "2층", LAT, LNG, "99001", FIRST_SYNC);
        Place approved = existing("TEST-A", PlaceStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "sourceRegionCode", "99001");
        when(placeRepository.findAllCloseTargets(PlaceSource.SEMAS, List.of("99001"), JOB_STARTED_AT))
                .thenReturn(List.of(candidate, approved));
        when(placeRepository.countOpenBySourceRegion(PlaceSource.SEMAS, List.of("99001")))
                .thenReturn(Map.of("99001", 10L));

        placeCommandService.closeMissing(PlaceSource.SEMAS, List.of("99001"), JOB_STARTED_AT);

        assertThat(candidate.isClosed()).isTrue();
        assertThat(candidate).extracting("status", "name", "roadAddress", "addressDetail")
                .containsExactly(PlaceStatus.CANDIDATE, "후보이름", "서울특별시 마포구 후보로 1", "2층");
        assertThat(approved.isClosed()).isTrue();
        assertThat(approved).extracting("status", "name", "roadAddress", "addressDetail")
                .containsExactly(PlaceStatus.APPROVED, "고친이름", "서울특별시 마포구 원천로 1", "2층");
    }

    @Test
    @DisplayName("완주한 지역이 없으면 아무것도 조회하지 않는다")
    void closeWithNoCompletedRegion() {
        placeCommandService.closeMissing(PlaceSource.SEMAS, List.of(), JOB_STARTED_AT);

        verify(placeRepository, never()).findAllCloseTargets(any(), any(), any());
    }
}
