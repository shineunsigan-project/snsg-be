package com.shineunsigan.snsg.schedule.domain.model;

import com.shineunsigan.snsg.common.entity.BaseEntity;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 활동 카테고리(보드게임·방탈출·산책). 일정을 분류하는 기준 데이터다.
 * 마이그레이션과 DB 직접 수정으로만 관리하므로 쓰기 경로가 없다. 활동을 숨기려면 {@code isActive}를 끈다.
 */
@Getter
@Entity
@Table(name = "activity_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityCategory extends BaseEntity {

    @Column(length = 30, nullable = false)
    private String code;

    @Column(length = 20, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ActivityPlaceType placeType;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean isActive;
}
