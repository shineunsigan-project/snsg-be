package com.shineunsigan.snsg.schedule.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;

public interface ActivityCategoryRepository {

    /** 활성이고 삭제되지 않은 활동. display_order 오름차순이다. */
    List<ActivityCategory> findAllActive();

    /** 활성이고 삭제되지 않은 활동이면 담겨 있다. */
    Optional<ActivityCategory> findActiveById(UUID id);

    /** 삭제되지 않은 활동 중 id가 맞는 것. 비활성 활동도 준다(이미 열린 일정의 활동 이름). */
    List<ActivityCategory> findAllNotDeletedByIds(Collection<UUID> ids);
}
