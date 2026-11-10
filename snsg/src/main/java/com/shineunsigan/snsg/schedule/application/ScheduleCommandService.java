package com.shineunsigan.snsg.schedule.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleCommandService {

    private final ScheduleRepository scheduleRepository;
    private final ActivityCategoryRepository activityCategoryRepository;
    private final ScheduleProperties scheduleProperties;

    /**
     * 일정과 개설자의 참여 행을 한 트랜잭션에서 저장한다.
     * <p>
     * 사용자 잠금을 겹침 검사보다 먼저 건다. 같은 사용자의 요청(더블클릭 등)이 차례로 처리되어,
     * 두 번째 요청은 첫 번째가 저장한 일정을 보고 겹침으로 거부된다.
     * 활동은 호출 전에 확인했어도 트랜잭션 안에서 다시 본다. 그 사이 비활성이 될 수 있다.
     *
     * @param place 개설 시점의 장소 사본. 장소 유형은 호출하는 쪽이 활동과 맞춰 왔다
     */
    @Transactional
    public UUID create(CreateScheduleCommand command, PlaceSnapshot place) {
        scheduleRepository.lockUser(command.userId());
        validateCategoryActive(command.categoryId());
        validateNoOverlap(command.userId(), command.startAt());
        return scheduleRepository.save(newSchedule(command, place)).getId();
    }

    /**
     * 멤버로 참여한다. 잠금 순서는 사용자 → 일정이다.
     * <p>
     * 사용자 잠금으로 같은 사용자의 개설·참여를 차례로 세워 겹침 검사가 서로의 결과를 보게 한다.
     * 일정 행 잠금으로 같은 일정의 참여를 차례로 세워 마지막 자리에 두 명이 들어가지 못하게 한다.
     * 겹침 검사는 개설자·이미 참여·모집 중 아님 판정 뒤에 한다. 그 경우에 시간 겹침으로 안내하지 않는다.
     */
    @Transactional
    public void join(UUID scheduleId, UUID userId) {
        scheduleRepository.lockUser(userId);
        Schedule schedule = findScheduleForUpdate(scheduleId);
        LocalDateTime now = LocalDateTime.now();
        schedule.validateJoinable(userId, now, scheduleProperties.completionAfter());
        validateNoOverlap(userId, schedule.getStartAt());
        schedule.join(userId, now, scheduleProperties.completionAfter());
    }

    /**
     * 참여를 취소한다. 일정 행만 잠근다. 취소는 겹침을 만들지 않아 사용자 잠금이 필요 없다.
     */
    @Transactional
    public void leave(UUID scheduleId, UUID userId) {
        Schedule schedule = findScheduleForUpdate(scheduleId);
        schedule.leave(userId, LocalDateTime.now(), scheduleProperties.completionAfter());
    }

    /**
     * 개설자가 일정 전체를 취소한다. 일정 행만 잠그고 판정한다. 사용자 잠금은 걸지 않는다. 취소는 겹침을 만들지 않는다.
     * <p>
     * 잠금으로 같은 일정의 참여·참여 취소·취소를 차례로 세운다. 동시에 들어온 두 취소 중 뒤의 것은 취소된 일정을 보고
     * SCHEDULE_NOT_CANCELABLE이 되고, 취소 뒤 들어온 참여는 RECRUIT_CLOSED가 된다.
     * 잠그지 않으면 참여가 바꾼 확정 인원을 옛 값으로 덮어쓸 수 있다.
     */
    @Transactional
    public void cancel(UUID scheduleId, UUID userId, String reason) {
        Schedule schedule = findScheduleForUpdate(scheduleId);
        schedule.cancel(userId, reason, LocalDateTime.now());
    }

    /** 시작 시각이 지난 모집 중 일정을 모집 마감으로 바꾼다(스케줄러). */
    @Transactional
    public void closeStartedRecruitments() {
        scheduleRepository.closeRecruitmentStarted(LocalDateTime.now());
    }

    /** 시작 + 완료 기준 시간이 지난 예정 일정을 완료로 바꾼다(스케줄러). */
    @Transactional
    public void completeFinishedSchedules() {
        scheduleRepository.completeFinished(LocalDateTime.now(), scheduleProperties.completionAfter());
    }

    private Schedule findScheduleForUpdate(UUID scheduleId) {
        return scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void validateCategoryActive(UUID categoryId) {
        if (activityCategoryRepository.findActiveById(categoryId).isEmpty()) {
            throw new BusinessException(ScheduleErrorCode.INVALID_CATEGORY);
        }
    }

    private void validateNoOverlap(UUID userId, LocalDateTime startAt) {
        if (scheduleRepository.existsOverlapping(userId, startAt, scheduleProperties.overlapWindow())) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
        }
    }

    private Schedule newSchedule(CreateScheduleCommand command, PlaceSnapshot place) {
        return Schedule.create(command.userId(), command.categoryId(), command.title(), command.description(),
                command.durationType(), command.startAt(), command.capacity(), command.placeId(), place,
                scheduleProperties.creationRule(), LocalDateTime.now());
    }
}
