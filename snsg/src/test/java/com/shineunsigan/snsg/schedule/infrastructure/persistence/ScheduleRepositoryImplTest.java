package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;

/**
 * 시간 겹침 쿼리, 일정 잠금 조회와 DB 제약(확정 참여 부분 유니크 인덱스, 확정 인원 CHECK)을 실제 PostgreSQL에서 검증한다.
 * 일정·참여 행은 native INSERT로 넣는다. 사용자 id는 테스트마다 무작위라 다른 데이터와 섞이지 않는다.
 * 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScheduleRepositoryImpl.class)
class ScheduleRepositoryImplTest {

    private static final Duration WINDOW = Duration.ofHours(12);
    private static final LocalDateTime EXISTING_START = LocalDateTime.of(2027, 1, 10, 19, 0);

    @Autowired
    private ScheduleRepositoryImpl scheduleRepository;

    @Autowired
    private EntityManager entityManager;

    private final UUID userId = UUID.randomUUID();

    // 시간 겹침

    @Test
    @DisplayName("확정 참여한 일정과 11시간 59분 차이면 앞이든 뒤든 겹친다")
    void lessThanWindowOverlaps() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "HOST", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START.plusHours(11).plusMinutes(59))).isTrue();
        assertThat(overlaps(EXISTING_START.minusHours(11).minusMinutes(59))).isTrue();
    }

    @Test
    @DisplayName("정확히 12시간 차이면 앞이든 뒤든 겹치지 않는다")
    void exactlyWindowDoesNotOverlap() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "HOST", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START.plusHours(12))).isFalse();
        assertThat(overlaps(EXISTING_START.minusHours(12))).isFalse();
    }

    @Test
    @DisplayName("멤버로 확정 참여한 일정도 겹침으로 잡힌다")
    void memberParticipationOverlaps() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, UUID.randomUUID(), "HOST", "CONFIRMED", false);
        insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START)).isTrue();
    }

    @Test
    @DisplayName("참여를 취소한 일정은 겹침에서 빠진다")
    void canceledParticipationIsIgnored() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "MEMBER", "CANCELED", false);

        assertThat(overlaps(EXISTING_START)).isFalse();
    }

    @Test
    @DisplayName("삭제된 참여 행은 겹침에서 빠진다")
    void deletedParticipationIsIgnored() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", true);

        assertThat(overlaps(EXISTING_START)).isFalse();
    }

    @Test
    @DisplayName("취소·완료된 일정은 겹침에서 빠진다")
    void canceledOrCompletedScheduleIsIgnored() {
        insertParticipant(insertSchedule(row().status("CANCELED")), userId, "HOST", "CONFIRMED", false);
        insertParticipant(insertSchedule(row().status("COMPLETED")), userId, "HOST", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START)).isFalse();
    }

    @Test
    @DisplayName("삭제된 일정은 겹침에서 빠진다")
    void deletedScheduleIsIgnored() {
        insertParticipant(insertSchedule(row().deleted(true)), userId, "HOST", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START)).isFalse();
    }

    @Test
    @DisplayName("다른 사용자가 확정 참여한 일정은 겹침에서 빠진다")
    void otherUsersScheduleIsIgnored() {
        insertParticipant(insertSchedule(row()), UUID.randomUUID(), "HOST", "CONFIRMED", false);

        assertThat(overlaps(EXISTING_START)).isFalse();
    }

    // 일정 잠금 조회

    @Test
    @DisplayName("삭제되지 않은 일정은 잠금 조회로 찾는다")
    void findsNotDeletedScheduleForUpdate() {
        UUID schedule = insertSchedule(row());

        assertThat(scheduleRepository.findByIdForUpdate(schedule))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(schedule));
    }

    @Test
    @DisplayName("삭제된 일정은 잠금 조회로 찾지 않는다")
    void deletedScheduleIsNotFoundForUpdate() {
        UUID schedule = insertSchedule(row().deleted(true));

        assertThat(scheduleRepository.findByIdForUpdate(schedule)).isEmpty();
    }

    // DB 제약

    @Test
    @DisplayName("같은 일정에 같은 사용자의 확정 행을 하나 더 넣을 수 없다")
    void duplicatedConfirmedParticipantIsRejected() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false);

        assertThatThrownBy(() -> insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false))
                .hasStackTraceContaining("uix_participants_confirmed");
    }

    @Test
    @DisplayName("취소한 행이 있어도 같은 일정에 같은 사용자의 확정 행을 다시 넣을 수 있다")
    void confirmedAfterCanceledIsAllowed() {
        UUID schedule = insertSchedule(row());
        insertParticipant(schedule, userId, "MEMBER", "CANCELED", false);

        assertThatCode(() -> insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("확정 인원이 정원을 넘는 일정은 넣을 수 없다")
    void confirmedCountOverCapacityIsRejected() {
        assertThatThrownBy(() -> insertSchedule(row().capacity(4).confirmedCount(5)))
                .hasStackTraceContaining("ck_schedules_count");
    }

    private boolean overlaps(LocalDateTime startAt) {
        return scheduleRepository.existsOverlapping(userId, startAt, WINDOW);
    }

    private ScheduleRow row() {
        return new ScheduleRow();
    }

    private UUID insertSchedule(ScheduleRow row) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO schedules (id, host_user_id, host_type, category_id, title, description,
                                               duration_type, start_at, capacity, confirmed_count, status,
                                               recruitment_status, place_id, place_name, place_road_address,
                                               place_address_detail, place_latitude, place_longitude, region_code,
                                               created_at, updated_at, created_by, updated_by, is_deleted)
                        VALUES (?, ?, 'USER', ?, '테스트일정', '테스트 일정 설명입니다.', 'NORMAL', ?, ?, ?, ?,
                                'RECRUITING', ?, '테스트장소', '서울특별시 테스트로 1', '', 37.5, 126.9, '11440',
                                NOW(), NOW(), 'SYSTEM', 'SYSTEM', ?)""")
                .setParameter(1, id)
                .setParameter(2, UUID.randomUUID())
                .setParameter(3, UUID.randomUUID())
                .setParameter(4, EXISTING_START)
                .setParameter(5, row.capacity)
                .setParameter(6, row.confirmedCount)
                .setParameter(7, row.status)
                .setParameter(8, UUID.randomUUID())
                .setParameter(9, row.deleted)
                .executeUpdate();
        return id;
    }

    private void insertParticipant(UUID scheduleId, UUID participantId, String role, String status,
            boolean deleted) {
        entityManager.createNativeQuery("""
                        INSERT INTO schedule_participants (id, schedule_id, user_id, role, status,
                                                           created_at, updated_at, is_deleted)
                        VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)""")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, scheduleId)
                .setParameter(3, participantId)
                .setParameter(4, role)
                .setParameter(5, status)
                .setParameter(6, deleted)
                .executeUpdate();
    }

    /** 기본값은 EXISTING_START에 시작하는 예정 일정(정원 6, 확정 1). */
    private static final class ScheduleRow {

        private String status = "SCHEDULED";
        private int capacity = 6;
        private int confirmedCount = 1;
        private boolean deleted;

        ScheduleRow status(String status) {
            this.status = status;
            return this;
        }

        ScheduleRow capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        ScheduleRow confirmedCount(int confirmedCount) {
            this.confirmedCount = confirmedCount;
            return this;
        }

        ScheduleRow deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }
    }
}
