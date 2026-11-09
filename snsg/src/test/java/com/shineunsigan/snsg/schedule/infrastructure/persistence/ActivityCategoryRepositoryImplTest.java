package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;

import jakarta.persistence.EntityManager;

/**
 * 활동 목록의 활성·삭제 필터와 정렬, code 부분 유니크 인덱스를 실제 PostgreSQL에서 검증한다.
 * 마이그레이션의 초기 행(BOARD_GAME, ESCAPE_ROOM, WALK)과 겹치지 않는 코드로 넣는다. 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ActivityCategoryRepositoryImpl.class)
class ActivityCategoryRepositoryImplTest {

    private static final List<String> MIGRATED_CODES = List.of("BOARD_GAME", "ESCAPE_ROOM", "WALK");

    @Autowired
    private ActivityCategoryRepositoryImpl activityCategoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("마이그레이션의 활동 3행이 보드게임, 방탈출, 산책 순서로 장소 유형과 함께 읽힌다")
    void migratedActivitiesAreReadInOrder() {
        List<ActivityCategory> activities = activityCategoryRepository.findAllActive();

        assertThat(activities)
                .filteredOn(activity -> MIGRATED_CODES.contains(activity.getCode()))
                .extracting(ActivityCategory::getCode, ActivityCategory::getName, ActivityCategory::getPlaceType)
                .containsExactly(
                        tuple("BOARD_GAME", "보드게임", ActivityPlaceType.BOARD_GAME_CAFE),
                        tuple("ESCAPE_ROOM", "방탈출", ActivityPlaceType.ESCAPE_ROOM),
                        tuple("WALK", "산책", ActivityPlaceType.PARK));
    }

    @Test
    @DisplayName("활동 목록은 비활성·삭제된 활동을 빼고 넣은 순서와 무관하게 display_order 순으로 준다")
    void findAllActiveExcludesInactiveAndDeletedAndSortsByDisplayOrder() {
        insert("TEST_LATE", 103, true, false);
        insert("TEST_INACTIVE", 101, false, false);
        insert("TEST_DELETED", 101, true, true);
        insert("TEST_EARLY", 102, true, false);

        List<ActivityCategory> activities = activityCategoryRepository.findAllActive();

        assertThat(activities).extracting(ActivityCategory::getCode)
                .doesNotContain("TEST_INACTIVE", "TEST_DELETED");
        assertThat(activities).extracting(ActivityCategory::getCode)
                .filteredOn(code -> code.startsWith("TEST_"))
                .containsExactly("TEST_EARLY", "TEST_LATE");
        assertThat(activities).extracting(ActivityCategory::getDisplayOrder).isSorted();
    }

    @Test
    @DisplayName("삭제되지 않은 활동과 같은 code로는 넣을 수 없다")
    void duplicatedCodeIsRejected() {
        insert("TEST_DUP", 101, true, false);

        assertThatThrownBy(() -> insert("TEST_DUP", 102, true, false))
                .hasStackTraceContaining("uix_activity_categories_code");
    }

    @Test
    @DisplayName("삭제된 활동과는 같은 code를 쓸 수 있다")
    void duplicatedCodeIsAllowedForDeletedRow() {
        insert("TEST_DUP", 101, true, false);

        assertThatCode(() -> insert("TEST_DUP", 102, true, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("id로 찾으면 비활성 활동도 주고 삭제된 활동은 뺀다")
    void findAllNotDeletedByIdsIncludesInactive() {
        UUID active = insert("TEST_ACTIVE", 101, true, false);
        UUID inactive = insert("TEST_INACTIVE", 102, false, false);
        UUID deleted = insert("TEST_DELETED", 103, true, true);

        assertThat(activityCategoryRepository.findAllNotDeletedByIds(List.of(active, inactive, deleted)))
                .extracting(ActivityCategory::getId)
                .containsExactlyInAnyOrder(active, inactive);
    }

    private UUID insert(String code, int displayOrder, boolean active, boolean deleted) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO activity_categories (id, code, name, place_type, display_order, is_active,
                                                         created_at, updated_at, is_deleted)
                        VALUES (?, ?, '테스트활동', 'PARK', ?, ?, NOW(), NOW(), ?)""")
                .setParameter(1, id)
                .setParameter(2, code)
                .setParameter(3, displayOrder)
                .setParameter(4, active)
                .setParameter(5, deleted)
                .executeUpdate();
        return id;
    }
}
