package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shineunsigan.snsg.schedule.domain.model.Schedule;

interface ScheduleJpaRepository extends JpaRepository<Schedule, UUID> {

    /**
     * 트랜잭션이 끝나면 풀리는 사용자 단위 잠금. pg_advisory_xact_lock은 void를 돌려줘 FROM에 두고 1을 받는다.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:userId))", nativeQuery = true)
    Integer lockUser(@Param("userId") String userId);

    /** 내 확정 참여 행(user_id, status 인덱스)에서 출발해 일정과 조인한다. 양 끝은 겹치지 않는다. */
    @Query("""
            select case when count(p) > 0 then true else false end
            from ScheduleParticipant p join p.schedule s
            where p.userId = :userId
              and p.status = com.shineunsigan.snsg.schedule.domain.constants.ParticipationStatus.CONFIRMED
              and p.isDeleted = false
              and s.isDeleted = false
              and s.status = com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus.SCHEDULED
              and s.startAt > :from
              and s.startAt < :to
            """)
    boolean existsConfirmedScheduledBetween(@Param("userId") UUID userId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
