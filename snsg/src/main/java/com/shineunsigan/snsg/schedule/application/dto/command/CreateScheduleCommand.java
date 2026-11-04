package com.shineunsigan.snsg.schedule.application.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.DurationType;

public record CreateScheduleCommand(UUID userId, UUID categoryId, UUID placeId, String title,
        DurationType durationType, String description, LocalDateTime startAt, int capacity) {
}
