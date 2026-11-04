package com.shineunsigan.snsg.schedule.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.place.application.PlaceQueryService;
import com.shineunsigan.snsg.place.infrastructure.persistence.PlaceRepositoryImpl;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;

import jakarta.persistence.EntityManager;

/**
 * place의 노출 조건을 거쳐 온 장소만 schedule의 장소 결과로 바뀌는지 실제 PostgreSQL에서 본다.
 * 마이그레이션의 장소와 섞이지 않게 테스트용 지역 99001(활성)·99002(비활성)에 넣는다. 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SchedulePlaceAdapter.class, PlaceQueryService.class, PlaceRepositoryImpl.class})
class SchedulePlaceAdapterTest {

    private static final String ACTIVE = "99001";
    private static final String INACTIVE = "99002";

    @Autowired
    private SchedulePlaceAdapter schedulePlaceAdapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpRegions() {
        insertRegion(ACTIVE, true);
        insertRegion(INACTIVE, false);
    }

    @Test
    @DisplayName("승인된 장소는 유형이 활동 장소 유형으로 바뀌고 이름·주소·상세주소·좌표·지역 코드가 그대로 온다")
    void approvedPlaceIsConverted() {
        UUID id = insert("PARK", "APPROVED", false, false, ACTIVE);

        assertThat(schedulePlaceAdapter.find(id)).get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(new ExposedPlaceResult(id, ActivityPlaceType.PARK, "테스트공원", "서울특별시 테스트로 1", "정문",
                        new BigDecimal("37.5512345"), new BigDecimal("126.9212345"), ACTIVE));
    }

    @Test
    @DisplayName("없는 id면 비어 있다")
    void unknownId() {
        assertThat(schedulePlaceAdapter.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("검수 대기·거절·폐업·삭제·비활성 지역 장소는 비어 있다")
    void notExposedPlacesAreEmpty() {
        List<UUID> hidden = List.of(
                insert("PARK", "CANDIDATE", false, false, ACTIVE),
                insert("PARK", "REJECTED", false, false, ACTIVE),
                insert("PARK", "APPROVED", true, false, ACTIVE),
                insert("PARK", "APPROVED", false, true, ACTIVE),
                insert("PARK", "APPROVED", false, false, INACTIVE));

        assertThat(hidden).allSatisfy(id -> assertThat(schedulePlaceAdapter.find(id)).isEmpty());
    }

    private void insertRegion(String code, boolean active) {
        entityManager.createNativeQuery("""
                        INSERT INTO regions (code, sido_name, sigungu_name, display_order, is_active, created_at, updated_at)
                        VALUES (?, '테스트시', ?, 101, ?, NOW(), NOW())""")
                .setParameter(1, code)
                .setParameter(2, "구" + code)
                .setParameter(3, active)
                .executeUpdate();
    }

    private UUID insert(String placeType, String status, boolean closed, boolean deleted, String region) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO places (id, place_type, name, road_address, address_detail, latitude, longitude,
                                            region_code, status, is_closed, source, source_id, source_name,
                                            source_road_address, source_address_detail, source_latitude,
                                            source_longitude, source_region_code, last_synced_at,
                                            created_at, updated_at, is_deleted)
                        VALUES (?, ?, '테스트공원', '서울특별시 테스트로 1', '정문', 37.5512345, 126.9212345, ?, ?, ?,
                                'MANUAL', '', '', '', '', 0, 0, ?, NOW(), NOW(), NOW(), ?)""")
                .setParameter(1, id)
                .setParameter(2, placeType)
                .setParameter(3, region)
                .setParameter(4, status)
                .setParameter(5, closed)
                .setParameter(6, region)
                .setParameter(7, deleted)
                .executeUpdate();
        return id;
    }
}
