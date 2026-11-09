package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.schedule.domain.model.RecruitingScheduleFilter;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleCursor;

import jakarta.persistence.EntityManager;

/**
 * 시간 겹침 쿼리, 일정 잠금 조회, 일정 목록 3종 쿼리와 DB 제약(확정 참여 부분 유니크 인덱스, 확정 인원 CHECK)을 실제 PostgreSQL에서 검증한다.
 * 일정·참여 행은 native INSERT로 넣는다. 사용자 id는 테스트마다 무작위라 다른 데이터와 섞이지 않는다.
 * 테스트 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScheduleRepositoryImpl.class)
class ScheduleRepositoryImplTest {

    private static final Duration WINDOW = Duration.ofHours(12);
    private static final LocalDateTime EXISTING_START = LocalDateTime.of(2027, 1, 10, 19, 0);
    /** 목록 쿼리의 기준 시각. 인자로 넘겨 고정한다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 3, 10, 12, 0);

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

    // 모집 중 목록. 다른 테스트·워크트리의 행과 섞이지 않게 무작위 활동 id로 넣고 그 활동으로 거른다.

    @Test
    @DisplayName("모집 중 목록은 시작 전 예정 일정만 준다. 모집 마감 일정은 포함하고 시작한·취소·완료·삭제 일정은 뺀다")
    void recruitingScope() {
        UUID category = UUID.randomUUID();
        UUID recruiting = insertSchedule(row().categoryId(category).startAt(NOW.plusHours(1)));
        UUID closed = insertSchedule(row().categoryId(category).startAt(NOW.plusHours(2))
                .capacity(6).confirmedCount(6).recruitmentStatus("CLOSED"));
        insertSchedule(row().categoryId(category).startAt(NOW.minusHours(1)));
        insertSchedule(row().categoryId(category).startAt(NOW));
        insertSchedule(row().categoryId(category).startAt(NOW.plusHours(3)).status("CANCELED"));
        insertSchedule(row().categoryId(category).startAt(NOW.plusHours(3)).status("COMPLETED"));
        insertSchedule(row().categoryId(category).startAt(NOW.plusHours(3)).deleted(true));

        assertThat(recruitingIds(filter(List.of(category), null, null, null))).containsExactly(recruiting, closed);
    }

    @Test
    @DisplayName("모집 중 목록은 시작 시각, 같으면 id 오름차순이다")
    void recruitingOrder() {
        UUID category = UUID.randomUUID();
        UUID later = insertSchedule(row().categoryId(category).startAt(NOW.plusDays(2)));
        List<UUID> sameStart = List.of(
                insertSchedule(row().categoryId(category).startAt(NOW.plusDays(1))),
                insertSchedule(row().categoryId(category).startAt(NOW.plusDays(1))),
                insertSchedule(row().categoryId(category).startAt(NOW.plusDays(1))));

        List<UUID> expected = new ArrayList<>(sortedLikePostgres(sameStart));
        expected.add(later);
        assertThat(recruitingIds(filter(List.of(category), null, null, null))).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("활동 두 개를 고르면 두 활동의 일정만 온다")
    void recruitingByCategories() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID firstSchedule = insertSchedule(row().categoryId(first).startAt(NOW.plusHours(1)));
        UUID secondSchedule = insertSchedule(row().categoryId(second).startAt(NOW.plusHours(2)));
        insertSchedule(row().startAt(NOW.plusHours(3)));

        assertThat(recruitingIds(filter(List.of(first, second), null, null, null)))
                .containsExactly(firstSchedule, secondSchedule);
    }

    @Test
    @DisplayName("지역 두 개를 고르면 두 지역의 일정만 온다")
    void recruitingByRegions() {
        UUID category = UUID.randomUUID();
        UUID mapo = insertSchedule(row().categoryId(category).startAt(NOW.plusHours(1)).regionCode("11440"));
        UUID seodaemun = insertSchedule(row().categoryId(category).startAt(NOW.plusHours(2)).regionCode("11410"));
        insertSchedule(row().categoryId(category).startAt(NOW.plusHours(3)).regionCode("11200"));

        assertThat(recruitingIds(filter(List.of(category), List.of("11440", "11410"), null, null)))
                .containsExactly(mapo, seodaemun);
    }

    @Test
    @DisplayName("활동과 지역을 함께 고르면 둘 다 맞는 일정만 온다")
    void recruitingByCategoryAndRegion() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID matched = insertSchedule(row().categoryId(first).startAt(NOW.plusHours(1)).regionCode("11440"));
        insertSchedule(row().categoryId(first).startAt(NOW.plusHours(2)).regionCode("11200"));
        insertSchedule(row().categoryId(second).startAt(NOW.plusHours(3)).regionCode("11200"));

        assertThat(recruitingIds(filter(List.of(first, second), List.of("11440"), null, null)))
                .containsExactly(matched);
    }

    @Test
    @DisplayName("모르는 활동 id만 보내거나 빈 값만 보내면 빈 결과다")
    void recruitingByUnknownValues() {
        insertSchedule(row().startAt(NOW.plusHours(1)));

        assertThat(recruitingIds(filter(List.of(UUID.randomUUID()), null, null, null))).isEmpty();
        assertThat(recruitingIds(filter(List.of(), null, null, null))).isEmpty();
        assertThat(recruitingIds(filter(null, List.of(""), null, null))).isEmpty();
    }

    @Test
    @DisplayName("날짜 범위 [D, D+1]은 D 00:00 ~ D+1 23:30을 포함하고 D-1 23:30, D+2 00:00은 뺀다")
    void recruitingByDateRange() {
        UUID category = UUID.randomUUID();
        LocalDate day = NOW.toLocalDate().plusDays(5);
        insertSchedule(row().categoryId(category).startAt(day.minusDays(1).atTime(23, 30)));
        UUID first = insertSchedule(row().categoryId(category).startAt(day.atStartOfDay()));
        UUID last = insertSchedule(row().categoryId(category).startAt(day.plusDays(1).atTime(23, 30)));
        insertSchedule(row().categoryId(category).startAt(day.plusDays(2).atStartOfDay()));

        assertThat(recruitingIds(filter(List.of(category), null, day, day.plusDays(1)))).containsExactly(first, last);
    }

    @Test
    @DisplayName("시작·끝 날짜가 같으면 그날 일정만 온다")
    void recruitingByOneDay() {
        UUID category = UUID.randomUUID();
        LocalDate day = NOW.toLocalDate().plusDays(5);
        insertSchedule(row().categoryId(category).startAt(day.minusDays(1).atTime(23, 30)));
        UUID morning = insertSchedule(row().categoryId(category).startAt(day.atTime(10, 0)));
        UUID night = insertSchedule(row().categoryId(category).startAt(day.atTime(23, 30)));
        insertSchedule(row().categoryId(category).startAt(day.plusDays(1).atStartOfDay()));

        assertThat(recruitingIds(filter(List.of(category), null, day, day))).containsExactly(morning, night);
    }

    @Test
    @DisplayName("커서로 size 2씩 끝까지 넘기면 같은 시작 시각이 여러 건이어도 중복·누락 없이 전체 목록과 같다")
    void recruitingCursorPagination() {
        UUID category = UUID.randomUUID();
        insertSchedule(row().categoryId(category).startAt(NOW.plusDays(1)));
        for (int i = 0; i < 3; i++) {
            insertSchedule(row().categoryId(category).startAt(NOW.plusDays(2)));
        }
        insertSchedule(row().categoryId(category).startAt(NOW.plusDays(3)));
        insertSchedule(row().categoryId(category).startAt(NOW.plusDays(3)));
        RecruitingScheduleFilter filter = filter(List.of(category), null, null, null);
        List<UUID> all = recruitingIds(filter);

        List<UUID> paged = new ArrayList<>();
        ScheduleCursor cursor = null;
        List<Schedule> page;
        do {
            page = scheduleRepository.findRecruiting(filter, NOW, cursor, 2);
            page.forEach(schedule -> paged.add(schedule.getId()));
            if (!page.isEmpty()) {
                Schedule last = page.get(page.size() - 1);
                cursor = ScheduleCursor.decode(new ScheduleCursor(last.getStartAt(), last.getId()).encode());
            }
        } while (!page.isEmpty());

        assertThat(all).hasSize(6);
        assertThat(paged).containsExactlyElementsOf(all);
    }

    // 참여한 목록. 사용자 id가 무작위라 다른 데이터와 섞이지 않는다.

    @Test
    @DisplayName("참여한 목록은 내가 멤버로 확정 참여한 완료 전 일정이다. 개설자가 취소한 일정, 시작했지만 완료 전인 일정도 온다")
    void participatingIncluded() {
        UUID scheduled = insertSchedule(row().startAt(NOW.plusDays(1)));
        UUID canceled = insertSchedule(row().startAt(NOW.plusDays(2)).status("CANCELED"));
        UUID started = insertSchedule(row().startAt(NOW.minusHours(11).minusMinutes(30)));
        for (UUID schedule : List.of(scheduled, canceled, started)) {
            insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false);
        }

        assertThat(participatingIds()).containsExactly(started, scheduled, canceled);
    }

    @Test
    @DisplayName("참여한 목록에서 내가 참여 취소한·완료된·시작 + 12시간이 지난·내가 연·남만 참여한·삭제된 일정은 빠진다")
    void participatingExcluded() {
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1))), userId, "MEMBER", "CANCELED", false);
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1)).status("COMPLETED")), userId, "MEMBER",
                "CONFIRMED", false);
        insertParticipant(insertSchedule(row().startAt(NOW.minusHours(12))), userId, "MEMBER", "CONFIRMED", false);
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1)).hostUserId(userId)), userId, "HOST",
                "CONFIRMED", false);
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1))), UUID.randomUUID(), "MEMBER", "CONFIRMED",
                false);
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1)).deleted(true)), userId, "MEMBER",
                "CONFIRMED", false);

        assertThat(participatingIds()).isEmpty();
    }

    @Test
    @DisplayName("참여 취소 후 다시 참여한 일정은 한 번만 온다")
    void participatingRejoinedOnce() {
        UUID schedule = insertSchedule(row().startAt(NOW.plusDays(1)));
        insertParticipant(schedule, userId, "MEMBER", "CANCELED", false);
        insertParticipant(schedule, userId, "MEMBER", "CONFIRMED", false);

        assertThat(participatingIds()).containsExactly(schedule);
    }

    // 개설한 목록

    @Test
    @DisplayName("개설한 목록은 내가 연 완료 전 일정이다. 취소한 일정도 온다")
    void hostedIncluded() {
        UUID scheduled = insertSchedule(row().startAt(NOW.plusDays(1)).hostUserId(userId));
        UUID canceled = insertSchedule(row().startAt(NOW.plusDays(2)).hostUserId(userId).status("CANCELED"));

        assertThat(hostedIds()).containsExactly(scheduled, canceled);
    }

    @Test
    @DisplayName("개설한 목록에서 멤버로 참여만 한·완료된·시작 + 12시간이 지난·삭제된 일정은 빠진다")
    void hostedExcluded() {
        insertParticipant(insertSchedule(row().startAt(NOW.plusDays(1))), userId, "MEMBER", "CONFIRMED", false);
        insertSchedule(row().startAt(NOW.plusDays(1)).hostUserId(userId).status("COMPLETED"));
        insertSchedule(row().startAt(NOW.minusHours(12)).hostUserId(userId));
        insertSchedule(row().startAt(NOW.plusDays(1)).hostUserId(userId).deleted(true));

        assertThat(hostedIds()).isEmpty();
    }

    private List<UUID> recruitingIds(RecruitingScheduleFilter filter) {
        return ids(scheduleRepository.findRecruiting(filter, NOW, null, 100));
    }

    private List<UUID> participatingIds() {
        return ids(scheduleRepository.findParticipating(userId, NOW.minus(WINDOW), null, 100));
    }

    private List<UUID> hostedIds() {
        return ids(scheduleRepository.findHosted(userId, NOW.minus(WINDOW), null, 100));
    }

    private static List<UUID> ids(List<Schedule> schedules) {
        return schedules.stream().map(Schedule::getId).toList();
    }

    private static RecruitingScheduleFilter filter(List<UUID> categoryIds, List<String> regionCodes,
            LocalDate startDate, LocalDate endDate) {
        return RecruitingScheduleFilter.of(categoryIds, regionCodes, startDate, endDate);
    }

    /** PostgreSQL uuid는 바이트를 부호 없이 비교한다. 소문자 16진 문자열의 사전 순과 같다. */
    private static List<UUID> sortedLikePostgres(List<UUID> ids) {
        return ids.stream().sorted(Comparator.comparing(UUID::toString)).toList();
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
                                ?, ?, '테스트장소', '서울특별시 테스트로 1', '', 37.5, 126.9, ?,
                                NOW(), NOW(), 'SYSTEM', 'SYSTEM', ?)""")
                .setParameter(1, id)
                .setParameter(2, row.hostUserId != null ? row.hostUserId : UUID.randomUUID())
                .setParameter(3, row.categoryId != null ? row.categoryId : UUID.randomUUID())
                .setParameter(4, row.startAt)
                .setParameter(5, row.capacity)
                .setParameter(6, row.confirmedCount)
                .setParameter(7, row.status)
                .setParameter(8, row.recruitmentStatus)
                .setParameter(9, UUID.randomUUID())
                .setParameter(10, row.regionCode)
                .setParameter(11, row.deleted)
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

    /** 기본값은 EXISTING_START에 시작하는 마포구(11440)의 모집 중인 예정 일정(정원 6, 확정 1). 개설자·활동은 무작위다. */
    private static final class ScheduleRow {

        private String status = "SCHEDULED";
        private int capacity = 6;
        private int confirmedCount = 1;
        private boolean deleted;
        private LocalDateTime startAt = EXISTING_START;
        private UUID categoryId;
        private String regionCode = "11440";
        private UUID hostUserId;
        private String recruitmentStatus = "RECRUITING";

        ScheduleRow startAt(LocalDateTime startAt) {
            this.startAt = startAt;
            return this;
        }

        ScheduleRow categoryId(UUID categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        ScheduleRow regionCode(String regionCode) {
            this.regionCode = regionCode;
            return this;
        }

        ScheduleRow hostUserId(UUID hostUserId) {
            this.hostUserId = hostUserId;
            return this;
        }

        ScheduleRow recruitmentStatus(String recruitmentStatus) {
            this.recruitmentStatus = recruitmentStatus;
            return this;
        }

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
