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
