package com.shineunsigan.snsg.schedule.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.HostType;
import com.shineunsigan.snsg.schedule.domain.constants.RecruitmentStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정. 참여 행({@link ScheduleParticipant})을 구성요소로 갖는 애그리거트 루트다.
 * <p>
 * 상태는 두 축이다. 일정 자체의 상태({@code status})와 모집 상태({@code recruitmentStatus}).
 * 화면에 보여줄 상태는 두 축과 시각을 합쳐 {@link #displayStatus}가 판정한다.
 * <p>
 * 개설 후 내용(제목·설명·시작·인원·장소)은 바꿀 수 없다. 그래서 내용을 바꾸는 메서드가 없다.
 * 개설자·활동·장소는 다른 애그리거트나 다른 도메인이라 id만 가진다.
 */
@Getter
@Entity
@Table(name = "schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseAuditEntity {

    private static final int TITLE_MIN_LENGTH = 1;
    private static final int TITLE_MAX_LENGTH = 20;
    private static final int DESCRIPTION_MIN_LENGTH = 10;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int HOST_COUNT = 1;

    @Column(nullable = false, updatable = false)
    private UUID hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, updatable = false)
    private HostType hostType;

    @Column(nullable = false, updatable = false)
    private UUID categoryId;

    @Column(length = 20, nullable = false, updatable = false)
    private String title;

    @Column(length = 500, nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false, updatable = false)
    private DurationType durationType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startAt;

    @Column(nullable = false, updatable = false)
    private int capacity;

    @Column(nullable = false)
    private int confirmedCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ScheduleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecruitmentStatus recruitmentStatus;

    @Column(nullable = false, updatable = false)
    private UUID placeId;

    @Embedded
    private PlaceSnapshot place;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.PERSIST)
    private List<ScheduleParticipant> participants = new ArrayList<>();

    private Schedule(UUID hostUserId, UUID categoryId, String title, String description, DurationType durationType,
            LocalDateTime startAt, int capacity, UUID placeId, PlaceSnapshot place) {
        this.hostUserId = hostUserId;
        this.hostType = HostType.USER;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.durationType = durationType;
        this.startAt = startAt;
        this.capacity = capacity;
        this.confirmedCount = HOST_COUNT;
        this.status = ScheduleStatus.SCHEDULED;
        this.recruitmentStatus = RecruitmentStatus.RECRUITING;
        this.placeId = placeId;
        this.place = place;
        this.participants.add(ScheduleParticipant.host(this, hostUserId));
    }

    /**
     * 사용자가 일정을 연다. 개설자는 확정 참여 행 하나로 함께 들어가고 확정 인원은 1이다.
     * 제목·설명은 앞뒤 공백을 뺀 값으로 길이를 보고 그 값을 저장한다.
     *
     * @param now 시작 일시를 판정할 기준 시각
     */
    public static Schedule create(UUID hostUserId, UUID categoryId, String title, String description,
            DurationType durationType, LocalDateTime startAt, int capacity, UUID placeId, PlaceSnapshot place,
            ScheduleCreationRule rule, LocalDateTime now) {
        String strippedTitle = validateTitle(title);
        String strippedDescription = validateDescription(description);
        validateStartAt(startAt, rule, now);
        validateCapacity(capacity, rule);
        return new Schedule(hostUserId, categoryId, strippedTitle, strippedDescription, durationType, startAt,
                capacity, placeId, place);
    }

    /**
     * 화면에 보여줄 상태. 위에서부터 먼저 맞는 것이다. 스케줄러가 늦어도 맞게 보이도록 저장된 상태와 시각을 함께 본다.
     *
     * @param completionAfter 시작 후 이만큼 지나면 완료로 본다. 같으면 완료다
     */
    public DisplayStatus displayStatus(LocalDateTime now, Duration completionAfter) {
        if (status == ScheduleStatus.CANCELED) {
            return DisplayStatus.CANCELED;
        }
        if (status == ScheduleStatus.COMPLETED || !now.isBefore(startAt.plus(completionAfter))) {
            return DisplayStatus.COMPLETED;
        }
        if (recruitmentStatus == RecruitmentStatus.CLOSED || !now.isBefore(startAt)) {
            return DisplayStatus.RECRUITMENT_CLOSED;
        }
        return DisplayStatus.RECRUITING;
    }

    public List<ScheduleParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    private static String validateTitle(String title) {
        String stripped = strip(title);
        if (!hasLengthBetween(stripped, TITLE_MIN_LENGTH, TITLE_MAX_LENGTH)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_TITLE);
        }
        return stripped;
    }

    private static String validateDescription(String description) {
        String stripped = strip(description);
        if (!hasLengthBetween(stripped, DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_DESCRIPTION);
        }
        return stripped;
    }

    /**
     * 30분 단위(초·나노초 0)이고, 지금 + 최소 리드 타임 이후(같으면 허용)이며,
     * 시작 날짜가 오늘 + 최대 일수 이내여야 한다. 마지막 날은 시각과 관계없이 허용한다.
     */
    private static void validateStartAt(LocalDateTime startAt, ScheduleCreationRule rule, LocalDateTime now) {
        if (startAt == null
                || !isHalfHour(startAt)
                || startAt.isBefore(now.plus(rule.minLeadTime()))
                || startAt.toLocalDate().isAfter(now.toLocalDate().plusDays(rule.maxAdvanceDays()))) {
            throw new BusinessException(ScheduleErrorCode.INVALID_START_AT);
        }
    }

    private static void validateCapacity(int capacity, ScheduleCreationRule rule) {
        if (capacity < rule.capacityMin() || capacity > rule.capacityMax()) {
            throw new BusinessException(ScheduleErrorCode.INVALID_CAPACITY);
        }
    }

    private static boolean isHalfHour(LocalDateTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30) && time.getSecond() == 0 && time.getNano() == 0;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    /** 길이는 글자(코드 포인트) 수로 센다. 이모지 하나를 두 글자로 세지 않는다. */
    private static boolean hasLengthBetween(String value, int min, int max) {
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
