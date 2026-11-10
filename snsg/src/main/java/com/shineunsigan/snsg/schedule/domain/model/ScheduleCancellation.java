package com.shineunsigan.snsg.schedule.domain.model;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정 전체 취소 기록. {@link Schedule} 애그리거트의 구성요소이고 루트를 통해서만 만든다.
 * 취소 일시는 {@code createdAt}, 취소한 사람은 {@code createdBy}다. 한 일정에 하나뿐이다(부분 유니크 인덱스).
 */
@Getter
@Entity
@Table(name = "schedule_cancellations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleCancellation extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private Schedule schedule;

    @Column(length = 100, nullable = false, updatable = false)
    private String reason;

    private ScheduleCancellation(Schedule schedule, String reason) {
        this.schedule = schedule;
        this.reason = reason;
    }

    /** @param reason 검증을 마친, 앞뒤 공백을 뺀 사유 */
    static ScheduleCancellation of(Schedule schedule, String reason) {
        return new ScheduleCancellation(schedule, reason);
    }
}
