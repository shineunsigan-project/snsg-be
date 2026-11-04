package com.shineunsigan.snsg.schedule.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;

interface ActivityCategoryJpaRepository extends JpaRepository<ActivityCategory, UUID> {

    List<ActivityCategory> findByIsActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc();

    Optional<ActivityCategory> findByIdAndIsActiveTrueAndIsDeletedFalse(UUID id);
}
