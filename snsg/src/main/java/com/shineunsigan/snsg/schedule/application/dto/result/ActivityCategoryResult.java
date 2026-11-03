package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;

public record ActivityCategoryResult(UUID id, String code, String name) {

    public static ActivityCategoryResult from(ActivityCategory activityCategory) {
        return new ActivityCategoryResult(activityCategory.getId(), activityCategory.getCode(),
                activityCategory.getName());
    }
}
