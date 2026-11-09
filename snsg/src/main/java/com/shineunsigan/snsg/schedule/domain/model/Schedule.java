package com.shineunsigan.snsg.schedule.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.HostType;
import com.shineunsigan.snsg.schedule.domain.constants.RecruitmentStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
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

    /**
     * 참여할 수 있는지 본다. 개설자 → 이미 참여 → 모집 중 아님 순서다.
     * 이미 참여한 사용자는 그 참여로 정원이 차 마감됐어도 {@code ALREADY_JOINED}를 받는다(두 번 눌렸을 때).
     * 모집 중은 화면의 [참여하기]와 같은 판정이다. 저장된 모집 상태가 RECRUITING이어도 시작 시각이 되면 마감이다.
     */
    public void validateJoinable(UUID userId, LocalDateTime now, Duration completionAfter) {
        if (hostUserId.equals(userId)) {
            throw new BusinessException(ScheduleErrorCode.HOST_CANNOT_JOIN);
        }
        if (findConfirmedParticipant(userId).isPresent()) {
            throw new BusinessException(ScheduleErrorCode.ALREADY_JOINED);
        }
        if (displayStatus(now, completionAfter) != DisplayStatus.RECRUITING) {
            throw new BusinessException(ScheduleErrorCode.RECRUIT_CLOSED);
        }
    }

    /**
     * 멤버로 참여한다. 새 확정 행을 더하고, 확정 인원이 정원에 닿으면 모집을 마감한다.
     * 취소 후 다시 참여해도 이전 행을 되살리지 않고 새 행을 만든다.
     */
    public void join(UUID userId, LocalDateTime now, Duration completionAfter) {
        validateJoinable(userId, now, completionAfter);
        participants.add(ScheduleParticipant.member(this, userId));
        confirmedCount++;
        if (confirmedCount == capacity) {
            recruitmentStatus = RecruitmentStatus.CLOSED;
        }
    }

    /**
     * 참여를 취소한다. 개설자 → 확정 참여 아님 → 취소할 수 없는 일정 순서로 거부한다.
     * 완료 전(시작 후 포함)까지 취소할 수 있다. 자리가 나도 모집을 다시 여는 것은 시작 전뿐이다.
     */
    public void leave(UUID userId, LocalDateTime now, Duration completionAfter) {
        if (hostUserId.equals(userId)) {
            throw new BusinessException(ScheduleErrorCode.HOST_CANNOT_LEAVE);
        }
        ScheduleParticipant participant = findConfirmedParticipant(userId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.NOT_PARTICIPANT));
        validateCancelable(now, completionAfter);
        participant.cancel();
        confirmedCount--;
        reopenIfBeforeStart(now);
    }

    /**
     * 상세를 보는 사람의 역할. 개설자 → 확정 참여 → 그 밖 순서다. 참여를 취소한 사람은 NON_PARTICIPANT다.
     *
     * @param viewerId 로그인하지 않았으면 {@code null}
     */
    public ViewerRole viewerRole(UUID viewerId) {
        if (viewerId == null) {
            return ViewerRole.GUEST;
        }
        if (hostUserId.equals(viewerId)) {
            return ViewerRole.HOST;
        }
        if (findConfirmedParticipant(viewerId).isPresent()) {
            return ViewerRole.PARTICIPANT;
        }
        return ViewerRole.NON_PARTICIPANT;
    }

    /**
     * 그 역할이 지금 누를 수 있는 동작. 참여·참여 취소는 {@link #join}·{@link #leave}와 같은 표시 상태 기준이다.
     * 개설자의 일정 취소는 예정(SCHEDULED)이고 시작 전일 때만이다. 정원이 차 마감됐어도 시작 전이면 취소할 수 있다.
     */
    public List<ScheduleAction> availableActions(ViewerRole role, LocalDateTime now, Duration completionAfter) {
        DisplayStatus displayStatus = displayStatus(now, completionAfter);
        return switch (role) {
            case GUEST -> displayStatus == DisplayStatus.RECRUITING
                    ? List.of(ScheduleAction.LOGIN_TO_JOIN) : List.of();
            case NON_PARTICIPANT -> displayStatus == DisplayStatus.RECRUITING
                    ? List.of(ScheduleAction.JOIN) : List.of();
            case PARTICIPANT -> isParticipationCancelable(displayStatus)
                    ? List.of(ScheduleAction.CANCEL_PARTICIPATION) : List.of();
            case HOST -> status == ScheduleStatus.SCHEDULED && startAt.isAfter(now)
                    ? List.of(ScheduleAction.CANCEL_SCHEDULE) : List.of();
        };
    }

    /** 확정 참여자(개설자 포함)의 사용자 id. 참여를 취소한 행과 삭제된 행은 빠진다. */
    public List<UUID> confirmedUserIds() {
        return participants.stream()
                .filter(ScheduleParticipant::isConfirmed)
                .map(ScheduleParticipant::getUserId)
                .toList();
    }

    public List<ScheduleParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    private Optional<ScheduleParticipant> findConfirmedParticipant(UUID userId) {
        return participants.stream()
                .filter(participant -> participant.isConfirmedBy(userId))
                .findFirst();
    }

    /** 화면의 [참여 취소]와 같은 판정이다. 취소·완료(시각 포함)된 일정은 취소할 수 없다. */
    private void validateCancelable(LocalDateTime now, Duration completionAfter) {
        if (!isParticipationCancelable(displayStatus(now, completionAfter))) {
            throw new BusinessException(ScheduleErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }
    }

    private static boolean isParticipationCancelable(DisplayStatus displayStatus) {
        return displayStatus == DisplayStatus.RECRUITING || displayStatus == DisplayStatus.RECRUITMENT_CLOSED;
    }

    /** 시작 후의 마감은 시각 때문이라 되돌리지 않는다. 저장된 모집 상태는 그대로 둔다. */
    private void reopenIfBeforeStart(LocalDateTime now) {
        if (now.isBefore(startAt) && recruitmentStatus == RecruitmentStatus.CLOSED) {
            recruitmentStatus = RecruitmentStatus.RECRUITING;
        }
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
