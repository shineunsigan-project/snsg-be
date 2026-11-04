package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {

    private final ScheduleJpaRepository scheduleJpaRepository;

    @Override
    public Schedule save(Schedule schedule) {
        return scheduleJpaRepository.save(schedule);
    }

    @Override
    public void lockUser(UUID userId) {
        scheduleJpaRepository.lockUser(userId.toString());
    }

    @Override
    public boolean existsOverlapping(UUID userId, LocalDateTime startAt, Duration window) {
        return scheduleJpaRepository.existsConfirmedScheduledBetween(userId, startAt.minus(window),
                startAt.plus(window));
    }
}
