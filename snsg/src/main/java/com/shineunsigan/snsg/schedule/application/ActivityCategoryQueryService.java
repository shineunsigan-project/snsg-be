package com.shineunsigan.snsg.schedule.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.schedule.application.dto.result.ActivityCategoryResult;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityCategoryQueryService {

    private final ActivityCategoryRepository activityCategoryRepository;

    /** 사용자에게 보여줄 활동. 활성이고 삭제되지 않은 활동만 display_order 오름차순으로. */
    public List<ActivityCategoryResult> findActive() {
        return activityCategoryRepository.findAllActive().stream()
                .map(ActivityCategoryResult::from)
                .toList();
    }
}
