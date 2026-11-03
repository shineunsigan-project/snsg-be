package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ActivityCategoryRepositoryImpl implements ActivityCategoryRepository {

    private final ActivityCategoryJpaRepository activityCategoryJpaRepository;

    @Override
    public List<ActivityCategory> findAllActive() {
        return activityCategoryJpaRepository.findByIsActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
    }
}
