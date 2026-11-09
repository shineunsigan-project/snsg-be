package com.shineunsigan.snsg.schedule.domain.model;

import java.util.UUID;

import com.shineunsigan.snsg.common.entity.BaseEntity;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipantRole;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정의 참여 행. {@link Schedule} 애그리거트의 구성요소이고 루트를 통해서만 만든다.
 * 사용자는 user 도메인이라 id만 가진다. 한 일정에 같은 사용자의 확정 행은 하나뿐이다(부분 유니크 인덱스).
 */
@Getter
@Entity
@Table(name = "schedule_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private Schedule schedule;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false, updatable = false)
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ParticipationStatus status;

    private ScheduleParticipant(Schedule schedule, UUID userId, ParticipantRole role) {
        this.schedule = schedule;
        this.userId = userId;
        this.role = role;
        this.status = ParticipationStatus.CONFIRMED;
    }

    /** 개설자의 확정 행. */
    static ScheduleParticipant host(Schedule schedule, UUID userId) {
        return new ScheduleParticipant(schedule, userId, ParticipantRole.HOST);
    }

    /** 참여한 멤버의 확정 행. 참여할 때마다 새 행이다. */
    static ScheduleParticipant member(Schedule schedule, UUID userId) {
        return new ScheduleParticipant(schedule, userId, ParticipantRole.MEMBER);
    }

    boolean isConfirmedBy(UUID userId) {
        return this.userId.equals(userId) && isConfirmed();
    }

    /** 확정 상태이고 삭제되지 않은 행. */
    boolean isConfirmed() {
        return status == ParticipationStatus.CONFIRMED && !isDeleted();
    }

    /** 참여 취소. 행은 남기고 상태만 바꾼다. */
    void cancel() {
        this.status = ParticipationStatus.CANCELED;
    }
}
