package com.shineunsigan.snsg.place.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.place.application.PlaceCommandService;
import com.shineunsigan.snsg.place.application.PlaceProperties;
import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;

import jakarta.persistence.EntityManager;

/**
 * 노출 조건·정렬, 폐업 처리 대상 선택, DB 제약을 실제 PostgreSQL에서 검증한다.
 * <p>
 * 마이그레이션의 초기 장소(활성 지역 11440·11410·11200)와 섞이지 않도록 테스트용 지역 99001(활성)·99003(활성)·
 * 99002(비활성)을 넣고, 장소도 그 지역에만 넣는다. 목록 단정은 테스트 지역 행으로 거른 결과에 한다.
 * 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlaceRepositoryImpl.class, PlaceCommandService.class, JpaAuditingConfig.class})
@EnableConfigurationProperties(PlaceProperties.class)
class PlaceRepositoryImplTest {

    private static final String ACTIVE = "99001";
    private static final String ACTIVE_FIRST = "99003";
    private static final String INACTIVE = "99002";
    private static final LocalDateTime OLD_SYNC = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final LocalDateTime JOB_STARTED_AT = LocalDateTime.of(2027, 1, 5, 3, 0);

    @Autowired
    private PlaceRepositoryImpl placeRepository;

    @Autowired
    private PlaceCommandService placeCommandService;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpRegions() {
        insertRegion(ACTIVE, 102, true);
        insertRegion(ACTIVE_FIRST, 101, true);
        insertRegion(INACTIVE, 103, false);
    }

    // 노출 조건

    @Test
    @DisplayName("노출 대상인 장소는 단건으로 찾힌다")
    void findExposedPlace() {
        UUID id = insert(row().name("노출장소"));

        assertThat(placeRepository.findExposed(id)).get().extracting(Place::getName).isEqualTo("노출장소");
    }

    @Test
    @DisplayName("없는 id로 찾으면 비어 있다")
    void findUnknownId() {
        assertThat(placeRepository.findExposed(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("검수 대기·거절·폐업·삭제·비활성 지역 장소는 단건과 목록 어디에도 나오지 않는다")
    void notExposedPlacesAreHidden() {
        List<UUID> hidden = List.of(
                insert(row().status(PlaceStatus.CANDIDATE)),
                insert(row().status(PlaceStatus.REJECTED)),
                insert(row().closed(true)),
                insert(row().deleted(true)),
                insert(row().region(INACTIVE)));

        assertThat(hidden).allMatch(id -> placeRepository.findExposed(id).isEmpty());
        assertThat(placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of(ACTIVE, INACTIVE)))
                .extracting(Place::getId).doesNotContainAnyElementsOf(hidden);
        assertThat(placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of()))
                .extracting(Place::getId).doesNotContainAnyElementsOf(hidden);
    }

    @Test
    @DisplayName("목록은 요청한 유형과 지역의 장소만 준다")
    void findAllExposedFiltersTypeAndRegion() {
        UUID wanted = insert(row().region(ACTIVE));
        insert(row().region(ACTIVE).placeType(PlaceType.BOARD_GAME_CAFE));
        insert(row().region(ACTIVE_FIRST));

        assertThat(placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of(ACTIVE)))
                .extracting(Place::getId).containsExactly(wanted);
    }

    @Test
    @DisplayName("요청 지역에 비활성 지역이나 없는 코드가 섞여도 오류 없이 그 지역만 빠진다")
    void findAllExposedIgnoresInactiveAndUnknownRegions() {
        UUID wanted = insert(row().region(ACTIVE));
        insert(row().region(INACTIVE));

        assertThat(placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of(ACTIVE, INACTIVE, "99999")))
                .extracting(Place::getId).containsExactly(wanted);
    }

    @Test
    @DisplayName("요청 지역이 비면 활성 지역 전체의 장소를 주고 비활성 지역 장소는 주지 않는다")
    void findAllExposedWithoutRegionsMeansAllActive() {
        UUID active = insert(row().region(ACTIVE));
        UUID activeFirst = insert(row().region(ACTIVE_FIRST));
        UUID inactive = insert(row().region(INACTIVE));

        List<Place> places = placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of());

        assertThat(places).extracting(Place::getId).contains(active, activeFirst).doesNotContain(inactive);
        List<String> regionCodes = places.stream().map(Place::getRegionCode).distinct().toList();
        assertThat(countInactiveRegions(regionCodes)).isZero();
    }

    @Test
    @DisplayName("목록은 넣은 순서와 무관하게 지역 display_order, 장소명 순이다")
    void findAllExposedSortsByRegionOrderThenName() {
        insert(row().region(ACTIVE).name("하늘방탈출"));
        insert(row().region(ACTIVE).name("가나방탈출"));
        insert(row().region(ACTIVE_FIRST).name("다람방탈출"));

        assertThat(placeRepository.findAllExposed(PlaceType.ESCAPE_ROOM, List.of()))
                .filteredOn(place -> place.getRegionCode().startsWith("99"))
                .extracting(Place::getName)
                .containsExactly("다람방탈출", "가나방탈출", "하늘방탈출");
    }

    // 폐업 처리 대상 선택

    @Test
    @DisplayName("폐업 처리 대상은 그 원천의 열려 있는 검수 대기·승인 장소 중 기준 시각 전에 확인된 것이다")
    void findAllCloseTargets() {
        UUID candidate = insert(row().status(PlaceStatus.CANDIDATE).syncedAt(OLD_SYNC));
        UUID approved = insert(row().syncedAt(OLD_SYNC));
        insert(row().status(PlaceStatus.REJECTED).syncedAt(OLD_SYNC));
        insert(row().closed(true).syncedAt(OLD_SYNC));
        insert(row().deleted(true).syncedAt(OLD_SYNC));
        insert(row().source(PlaceSource.PARK_STANDARD).syncedAt(OLD_SYNC));
        insert(row().syncedAt(JOB_STARTED_AT));
        insert(row().syncedAt(JOB_STARTED_AT.plusHours(1)));

        assertThat(placeRepository.findAllCloseTargets(PlaceSource.SEMAS, List.of(ACTIVE), JOB_STARTED_AT))
                .extracting(Place::getId).containsExactlyInAnyOrder(candidate, approved);
    }

    @Test
    @DisplayName("폐업 처리의 지역 판정은 원천 지역 기준이라, 화면 지역이 목록에 있어도 원천 지역이 없으면 대상이 아니다")
    void closeTargetsAreJudgedBySourceRegion() {
        insert(row().region(ACTIVE).sourceRegion(ACTIVE_FIRST).syncedAt(OLD_SYNC));
        UUID bySourceRegion = insert(row().region(ACTIVE_FIRST).sourceRegion(ACTIVE).syncedAt(OLD_SYNC));

        assertThat(placeRepository.findAllCloseTargets(PlaceSource.SEMAS, List.of(ACTIVE), JOB_STARTED_AT))
                .extracting(Place::getId).containsExactly(bySourceRegion);
    }

    @Test
    @DisplayName("MANUAL 장소는 폐업 처리 대상이 아니다")
    void manualIsNeverCloseTarget() {
        insert(row().source(PlaceSource.MANUAL).sourceId("").sourceRegion(ACTIVE).syncedAt(OLD_SYNC));

        assertThat(placeRepository.findAllCloseTargets(PlaceSource.MANUAL, List.of(ACTIVE), JOB_STARTED_AT))
                .isEmpty();
    }

    @Test
    @DisplayName("안전장치의 분모는 원천 지역별로 그 원천의 열려 있는 검수 대기·승인 장소 수다")
    void countOpenBySourceRegion() {
        insert(row().status(PlaceStatus.CANDIDATE).syncedAt(OLD_SYNC));
        insert(row().syncedAt(OLD_SYNC));
        insert(row().syncedAt(JOB_STARTED_AT.plusHours(1)));
        insert(row().status(PlaceStatus.REJECTED));
        insert(row().closed(true));
        insert(row().deleted(true));
        insert(row().source(PlaceSource.PARK_STANDARD));
        insert(row().sourceRegion(ACTIVE_FIRST));

        assertThat(placeRepository.countOpenBySourceRegion(PlaceSource.SEMAS, List.of(ACTIVE, ACTIVE_FIRST)))
                .containsEntry(ACTIVE, 3L)
                .containsEntry(ACTIVE_FIRST, 1L)
                .hasSize(2);
    }

    // 재노출

    @Test
    @DisplayName("폐업이던 승인 장소가 다시 반영되면 검수에서 고친 이름·주소 그대로 노출 조회에 나온다")
    void reconfirmedClosedApprovedIsExposedWithReviewedValues() {
        UUID id = insert(row().closed(true).name("고친이름").roadAddress("서울특별시 고친로 1").addressDetail("3층")
                .sourceId("TEST-REOPEN"));
        assertThat(placeRepository.findExposed(id)).isEmpty();

        placeCommandService.upsert(List.of(new UpsertPlaceCommand(PlaceSource.SEMAS, "TEST-REOPEN", "원천이름",
                "서울특별시 원천로 1", "2층", new BigDecimal("37.5"), new BigDecimal("126.9"), ACTIVE,
                PlaceType.ESCAPE_ROOM)));
        entityManager.flush();
        entityManager.clear();

        assertThat(placeRepository.findExposed(id)).get()
                .extracting(Place::getName, Place::getRoadAddress, Place::getAddressDetail, Place::isClosed)
                .containsExactly("고친이름", "서울특별시 고친로 1", "3층", false);
    }

    // DB 제약

    @Test
    @DisplayName("유형 미정이면서 승인된 장소는 넣을 수 없다")
    void unclassifiedApprovedIsRejectedOnInsert() {
        assertThatThrownBy(() -> insert(row().placeType(PlaceType.UNCLASSIFIED)))
                .hasStackTraceContaining("ck_places_approved_type");
    }

    @Test
    @DisplayName("유형 미정인 장소를 승인으로 바꿀 수 없다")
    void unclassifiedApprovedIsRejectedOnUpdate() {
        UUID id = insert(row().placeType(PlaceType.UNCLASSIFIED).status(PlaceStatus.CANDIDATE));

        assertThatThrownBy(() -> entityManager
                .createNativeQuery("UPDATE places SET status = 'APPROVED' WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate())
                .hasStackTraceContaining("ck_places_approved_type");
    }

    @Test
    @DisplayName("삭제되지 않은 같은 원천 식별자의 두 번째 행은 넣을 수 없다")
    void duplicatedSourceIdIsRejected() {
        insert(row().sourceId("TEST-DUP"));

        assertThatThrownBy(() -> insert(row().sourceId("TEST-DUP")))
                .hasStackTraceContaining("uix_places_source");
    }

    @Test
    @DisplayName("원천 식별자가 빈 MANUAL 장소는 여러 개 넣을 수 있다")
    void manualPlacesShareEmptySourceId() {
        insert(row().source(PlaceSource.MANUAL).sourceId(""));
        insert(row().source(PlaceSource.MANUAL).sourceId(""));

        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM places WHERE source = 'MANUAL' AND source_id = '' AND region_code = ?")
                .setParameter(1, ACTIVE)
                .getSingleResult();
        assertThat(count.longValue()).isEqualTo(2);
    }

    private long countInactiveRegions(List<String> codes) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM regions WHERE code IN (:codes) AND is_active = FALSE")
                .setParameter("codes", codes)
                .getSingleResult()).longValue();
    }

    private void insertRegion(String code, int displayOrder, boolean active) {
        entityManager.createNativeQuery("""
                        INSERT INTO regions (code, sido_name, sigungu_name, display_order, is_active, created_at, updated_at)
                        VALUES (?, '테스트시', ?, ?, ?, NOW(), NOW())""")
                .setParameter(1, code)
                .setParameter(2, "구" + code)
                .setParameter(3, displayOrder)
                .setParameter(4, active)
                .executeUpdate();
    }

    private static int sequence = 0;

    /** 기본값은 활성 지역의 노출 대상(SEMAS, 승인, 방탈출). 원천 식별자는 행마다 다르다. */
    private PlaceRow row() {
        return new PlaceRow("TEST-" + (++sequence));
    }

    private UUID insert(PlaceRow row) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO places (id, place_type, name, road_address, address_detail, latitude, longitude,
                                            region_code, status, is_closed, source, source_id, source_name,
                                            source_road_address, source_address_detail, source_latitude,
                                            source_longitude, source_region_code, last_synced_at,
                                            created_at, updated_at, is_deleted)
                        VALUES (?, ?, ?, ?, ?, 37.5, 126.9, ?, ?, ?, ?, ?, '원천이름', '서울특별시 원천로 1', '2층',
                                37.5, 126.9, ?, ?, NOW(), NOW(), ?)""")
                .setParameter(1, id)
                .setParameter(2, row.placeType.name())
                .setParameter(3, row.name)
                .setParameter(4, row.roadAddress)
                .setParameter(5, row.addressDetail)
                .setParameter(6, row.region)
                .setParameter(7, row.status.name())
                .setParameter(8, row.closed)
                .setParameter(9, row.source.name())
                .setParameter(10, row.sourceId)
                .setParameter(11, row.sourceRegion == null ? row.region : row.sourceRegion)
                .setParameter(12, row.syncedAt)
                .setParameter(13, row.deleted)
                .executeUpdate();
        return id;
    }

    private static final class PlaceRow {

        private PlaceType placeType = PlaceType.ESCAPE_ROOM;
        private PlaceStatus status = PlaceStatus.APPROVED;
        private PlaceSource source = PlaceSource.SEMAS;
        private String sourceId;
        private String name = "테스트장소";
        private String roadAddress = "서울특별시 테스트로 1";
        private String addressDetail = "";
        private String region = ACTIVE;
        private String sourceRegion;
        private boolean closed;
        private boolean deleted;
        private LocalDateTime syncedAt = OLD_SYNC;

        private PlaceRow(String sourceId) {
            this.sourceId = sourceId;
        }

        PlaceRow placeType(PlaceType placeType) {
            this.placeType = placeType;
            return this;
        }

        PlaceRow status(PlaceStatus status) {
            this.status = status;
            return this;
        }

        PlaceRow source(PlaceSource source) {
            this.source = source;
            return this;
        }

        PlaceRow sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        PlaceRow name(String name) {
            this.name = name;
            return this;
        }

        PlaceRow roadAddress(String roadAddress) {
            this.roadAddress = roadAddress;
            return this;
        }

        PlaceRow addressDetail(String addressDetail) {
            this.addressDetail = addressDetail;
            return this;
        }

        /** 화면 지역. 원천 지역을 따로 주지 않으면 같은 값이다. */
        PlaceRow region(String region) {
            this.region = region;
            return this;
        }

        PlaceRow sourceRegion(String sourceRegion) {
            this.sourceRegion = sourceRegion;
            return this;
        }

        PlaceRow closed(boolean closed) {
            this.closed = closed;
            return this;
        }

        PlaceRow deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        PlaceRow syncedAt(LocalDateTime syncedAt) {
            this.syncedAt = syncedAt;
            return this;
        }
    }
}
