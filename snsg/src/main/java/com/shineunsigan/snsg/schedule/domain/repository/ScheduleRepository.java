package com.shineunsigan.snsg.schedule.domain.repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.model.Schedule;

public interface ScheduleRepository {

    /** 일정과 그 참여 행을 함께 저장한다. */
    Schedule save(Schedule schedule);

    /**
     * 같은 사용자의 개설·참여를 트랜잭션이 끝날 때까지 차례로 세운다. 트랜잭션 안에서 불러야 한다.
     * 겹침 검사보다 먼저 불러야 동시에 들어온 두 요청이 서로의 결과를 보고 판정한다.
     */
    void lockUser(UUID userId);

    /**
     * 삭제되지 않은 일정을 행 잠금(SELECT … FOR UPDATE)과 함께 읽는다. 트랜잭션 안에서 불러야 한다.
     * 그 트랜잭션에서 이 일정을 처음 읽는 조회여야 한다. 먼저 읽어두면 잠근 뒤에도 옛 값(확정 인원·모집 상태)을 쓴다.
     * 참여 행은 잠근 뒤 지연 로딩으로 읽으므로 앞 트랜잭션이 커밋한 행을 본다.
     */
    Optional<Schedule> findByIdForUpdate(UUID scheduleId);

    /**
     * 그 사용자가 확정 참여(개설자 포함)한, 삭제되지 않은 예정(SCHEDULED) 일정 중
     * 시작 시각이 startAt과 window 미만으로 떨어진 것이 있는지. 정확히 window만큼 떨어지면 겹치지 않는다.
     */
    boolean existsOverlapping(UUID userId, LocalDateTime startAt, Duration window);
}
