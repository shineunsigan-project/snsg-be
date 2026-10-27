package com.shineunsigan.snsg.place.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.place.domain.model.Region;

import jakarta.persistence.EntityManager;

/**
 * 지역 목록의 활성 필터·정렬과, 타 도메인 조회가 비활성 지역까지 돌려주는지 검증한다.
 * 마이그레이션의 초기 행(11440, 11410, 11200)과 겹치지 않는 코드로 넣는다. 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RegionRepositoryImpl.class)
class RegionRepositoryImplTest {

    @Autowired
    private RegionRepositoryImpl regionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("활성 지역 목록은 비활성 지역을 빼고 넣은 순서와 무관하게 display_order 순으로 준다")
    void findAllActiveExcludesInactiveAndSortsByDisplayOrder() {
        insert("99001", 103, true);
        insert("99002", 101, false);
        insert("99003", 102, true);

        List<Region> regions = regionRepository.findAllActive();

        assertThat(regions).extracting(Region::getCode).doesNotContain("99002");
        assertThat(regions).extracting(Region::getCode)
                .filteredOn(code -> code.startsWith("99"))
                .containsExactly("99003", "99001");
        assertThat(regions).extracting(Region::getDisplayOrder).isSorted();
    }

    @Test
    @DisplayName("활성 지역을 코드로 찾으면 활성 상태로 준다")
    void findActiveByCode() {
        insert("99001", 101, true);

        assertThat(regionRepository.findByCode("99001"))
                .get()
                .extracting(Region::isActive)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("비활성 지역도 코드로 찾으면 비활성 상태로 준다")
    void findInactiveByCode() {
        insert("99001", 101, false);

        assertThat(regionRepository.findByCode("99001"))
                .get()
                .extracting(Region::isActive)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("없는 코드로 찾으면 비어 있다")
    void findUnknownCode() {
        assertThat(regionRepository.findByCode("99999")).isEmpty();
    }

    @Test
    @DisplayName("전체 목록에는 비활성 지역도 들어 있다")
    void findAllIncludesInactive() {
        insert("99001", 101, true);
        insert("99002", 102, false);

        assertThat(regionRepository.findAll()).extracting(Region::getCode)
                .contains("99001", "99002");
    }

    private void insert(String code, int displayOrder, boolean active) {
        entityManager.createNativeQuery("""
                        INSERT INTO regions (code, sido_name, sigungu_name, display_order, is_active, created_at, updated_at)
                        VALUES (?, '테스트시', ?, ?, ?, NOW(), NOW())""")
                .setParameter(1, code)
                .setParameter(2, "구" + code)
                .setParameter(3, displayOrder)
                .setParameter(4, active)
                .executeUpdate();
    }
}
