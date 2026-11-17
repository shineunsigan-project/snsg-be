package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipantRole;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipationStatus;
import com.shineunsigan.snsg.schedule.domain.constants.SchedulePeriod;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.model.RecruitingScheduleFilter;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleCursor;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {

    private final ScheduleJpaRepository scheduleJpaRepository;
    private final EntityManager entityManager;

    @Override
    public Schedule save(Schedule schedule) {
        return scheduleJpaRepository.save(schedule);
    }

    @Override
    public void lockUser(UUID userId) {
        scheduleJpaRepository.lockUser(userId.toString());
    }

    @Override
    public Optional<Schedule> findByIdForUpdate(UUID scheduleId) {
        return scheduleJpaRepository.findByIdForUpdate(scheduleId);
    }

    @Override
    public Optional<Schedule> findById(UUID scheduleId) {
        return scheduleJpaRepository.findNotDeletedById(scheduleId);
    }

    @Override
    public boolean existsOverlapping(UUID userId, LocalDateTime startAt, Duration window) {
        return scheduleJpaRepository.existsConfirmedScheduledBetween(userId, startAt.minus(window),
                startAt.plus(window));
    }

    @Override
    public Optional<LocalDateTime> findLatestWithdrawalBlockingStartAt(UUID userId, LocalDateTime now,
            Duration blockAfterStart) {
        return Optional.ofNullable(scheduleJpaRepository.findLatestConfirmedNotCanceledStartAtAfter(userId,
                now.minus(blockAfterStart)));
    }

    /** 선택 조건이 있어 JPQL을 조건별로 이어 만든다. 컬렉션 파라미터에 null을 바인딩하지 않기 위해서다. */
    @Override
    public List<Schedule> findRecruiting(RecruitingScheduleFilter filter, LocalDateTime now, ScheduleCursor after,
            int limit) {
        if (filter.matchesNothing()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select s from Schedule s
                where s.isDeleted = false
                  and s.status = :scheduled
                  and s.startAt > :now""");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scheduled", ScheduleStatus.SCHEDULED);
        parameters.put("now", now);
        if (filter.categoryIds() != null) {
            jpql.append(" and s.categoryId in :categoryIds");
            parameters.put("categoryIds", filter.categoryIds());
        }
        if (filter.regionCodes() != null) {
            jpql.append(" and s.place.regionCode in :regionCodes");
            parameters.put("regionCodes", filter.regionCodes());
        }
        if (filter.startFrom() != null) {
            jpql.append(" and s.startAt >= :startFrom and s.startAt < :startBefore");
            parameters.put("startFrom", filter.startFrom());
            parameters.put("startBefore", filter.startBefore());
        }
        return findPage(jpql, parameters, after, limit, false);
    }

    /** 확정 행은 일정당 사용자 하나뿐이라(부분 유니크 인덱스) 같은 일정이 두 번 나오지 않는다. */
    @Override
    public List<Schedule> findParticipating(UUID userId, SchedulePeriod period, LocalDateTime completionBoundary,
            ScheduleCursor after, int limit) {
        StringBuilder jpql = new StringBuilder("""
                select s from ScheduleParticipant p join p.schedule s
                where p.userId = :userId
                  and p.role = :member
                  and p.status = :confirmed
                  and p.isDeleted = false
                  and s.isDeleted = false""");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        parameters.put("member", ParticipantRole.MEMBER);
        parameters.put("confirmed", ParticipationStatus.CONFIRMED);
        appendPeriod(jpql, parameters, period, completionBoundary);
        return findPage(jpql, parameters, after, limit, period == SchedulePeriod.PAST);
    }

    @Override
    public List<Schedule> findHosted(UUID userId, SchedulePeriod period, LocalDateTime completionBoundary,
            ScheduleCursor after, int limit) {
        StringBuilder jpql = new StringBuilder("""
                select s from Schedule s
                where s.hostUserId = :userId
                  and s.isDeleted = false""");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        appendPeriod(jpql, parameters, period, completionBoundary);
        return findPage(jpql, parameters, after, limit, period == SchedulePeriod.PAST);
    }

    @Override
    public void closeRecruitmentStarted(LocalDateTime now) {
        scheduleJpaRepository.closeRecruitmentStarted(now, Auditor.SYSTEM_SCHEDULER);
    }

    @Override
    public void completeFinished(LocalDateTime now, Duration completionAfter) {
        scheduleJpaRepository.completeStartedBy(now.minus(completionAfter), now, Auditor.SYSTEM_SCHEDULER);
    }

    /** 두 기간은 서로의 여집합이다. 완료 기준 시각과 같은 시작은 지난 일정이다. */
    private static void appendPeriod(StringBuilder jpql, Map<String, Object> parameters, SchedulePeriod period,
            LocalDateTime completionBoundary) {
        switch (period) {
            case UPCOMING -> jpql.append(" and s.status <> :completed and s.startAt > :completionBoundary");
            case PAST -> jpql.append(" and (s.status = :completed or s.startAt <= :completionBoundary)");
        }
        parameters.put("completed", ScheduleStatus.COMPLETED);
        parameters.put("completionBoundary", completionBoundary);
    }

    /**
     * keyset 조건과 정렬을 붙여 limit건을 읽는다. 정렬과 커서 비교는 DB에서 한다.
     * Java의 UUID 비교는 부호 있는 비교라 PostgreSQL uuid 순서와 다르다.
     * 내림차순이면 (start_at, id)가 커서보다 앞인 일정을 늦은 순으로 읽는다.
     */
    private List<Schedule> findPage(StringBuilder jpql, Map<String, Object> parameters, ScheduleCursor after,
            int limit, boolean descending) {
        if (after != null) {
            jpql.append(descending
                    ? " and (s.startAt < :cursorStartAt or (s.startAt = :cursorStartAt and s.id < :cursorId))"
                    : " and (s.startAt > :cursorStartAt or (s.startAt = :cursorStartAt and s.id > :cursorId))");
            parameters.put("cursorStartAt", after.startAt());
            parameters.put("cursorId", after.id());
        }
        jpql.append(descending ? " order by s.startAt desc, s.id desc" : " order by s.startAt, s.id");
        TypedQuery<Schedule> query = entityManager.createQuery(jpql.toString(), Schedule.class);
        parameters.forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList();
    }
}
