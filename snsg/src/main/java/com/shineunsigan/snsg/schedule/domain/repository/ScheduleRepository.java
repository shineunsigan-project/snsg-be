package com.shineunsigan.snsg.schedule.domain.repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.model.RecruitingScheduleFilter;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleCursor;

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

    /*
     * 일정 목록 3종. 모두 삭제되지 않은 일정을 (start_at, id) 오름차순으로 limit건까지 준다.
     * after가 있으면 그 (start_at, id)보다 뒤의 일정만 준다(keyset). null이면 처음부터다.
     */

    /**
     * 모집 중 목록. 예정(SCHEDULED)이고 시작 시각이 now보다 뒤인 일정이다. 모집 마감(정원 참)도 포함한다.
     * 필터에 빈 목록 조건이 있으면 빈 목록이다.
     */
    List<Schedule> findRecruiting(RecruitingScheduleFilter filter, LocalDateTime now, ScheduleCursor after,
            int limit);

    /**
     * 내가 멤버로 확정 참여한 일정. 완료(COMPLETED)가 아니고 시작 시각이 startAfter보다 뒤인 것이다.
     * 개설자가 취소한 일정도 포함한다. 개설한 일정(HOST 행)은 빠진다.
     *
     * @param startAfter 지금 − 완료 기준 시간. 시작 + 완료 기준 시간이 지금보다 뒤인 일정만 남긴다
     */
    List<Schedule> findParticipating(UUID userId, LocalDateTime startAfter, ScheduleCursor after, int limit);

    /**
     * 내가 개설한 일정. 완료(COMPLETED)가 아니고 시작 시각이 startAfter보다 뒤인 것이다. 취소한 일정도 포함한다.
     *
     * @param startAfter 지금 − 완료 기준 시간
     */
    List<Schedule> findHosted(UUID userId, LocalDateTime startAfter, ScheduleCursor after, int limit);

    /*
     * 상태 전환(스케줄러). 조건부 일괄 UPDATE라 여러 번, 여러 서버에서 돌아도 결과가 같다.
     * 삭제된 일정은 바꾸지 않는다(삭제한 주체의 updated_by를 덮어쓰지 않는다).
     * JPA Auditing을 거치지 않으므로 updated_at은 now, updated_by는 스케줄러로 직접 넣는다.
     */

    /** 예정(SCHEDULED)·모집 중이고 시작 시각이 now 이하인 일정을 모집 마감(CLOSED)으로 바꾼다. */
    void closeRecruitmentStarted(LocalDateTime now);

    /** 예정(SCHEDULED)이고 시작 + completionAfter가 now 이하인 일정을 완료(COMPLETED)로 바꾼다. 모집 상태는 그대로다. */
    void completeFinished(LocalDateTime now, Duration completionAfter);
}
