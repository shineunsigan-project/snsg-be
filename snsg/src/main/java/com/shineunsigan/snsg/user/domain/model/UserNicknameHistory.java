package com.shineunsigan.snsg.user.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import com.shineunsigan.snsg.common.entity.BaseEntity;
import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;

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
 * 닉네임 보호 이력. User 애그리거트의 구성요소다.
 * {@code reservedUntil}까지 이 닉네임을 다른 사람이 쓰지 못한다.
 * 닉네임을 바꾼 시각은 {@code createdAt}이고, 가장 최근 {@code CHANGED} 행의 시각으로 다음 변경을 제한한다.
 * 비식별 때 닉네임을 비우는 것 말고는 수정하지 않으므로 변경 주체를 남기지 않는다({@link BaseEntity}).
 */
@Getter
@Entity
@Table(name = "user_nickname_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNicknameHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NicknameHistoryReason reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime reservedUntil;

    private UserNicknameHistory(User user, String nickname, NicknameHistoryReason reason,
            LocalDateTime reservedUntil) {
        this.user = user;
        this.nickname = nickname;
        this.reason = reason;
        this.reservedUntil = reservedUntil;
    }

    /**
     * 닉네임을 바꿨다. 바꾸기 전 닉네임을 {@code changedAt}부터 {@code protectPeriod} 동안 보호한다.
     */
    public static UserNicknameHistory changed(User user, String previousNickname, LocalDateTime changedAt,
            Duration protectPeriod) {
        return new UserNicknameHistory(user, previousNickname, NicknameHistoryReason.CHANGED,
                changedAt.plus(protectPeriod));
    }

    /**
     * 탈퇴했다. 탈퇴 시점 닉네임을 {@code withdrawnAt}부터 {@code protectPeriod} 동안 보호한다.
     */
    public static UserNicknameHistory withdrawn(User user, String nickname, LocalDateTime withdrawnAt,
            Duration protectPeriod) {
        return new UserNicknameHistory(user, nickname, NicknameHistoryReason.WITHDRAWN,
                withdrawnAt.plus(protectPeriod));
    }

    /** 비식별 때 남아 있던 닉네임을 지운다. */
    public void clearNickname() {
        this.nickname = "";
    }

    /**
     * 닉네임을 다시 바꿀 수 있는 시각. 마지막 변경 시각 + {@code restrictPeriod}가 {@code now}보다 뒤면 그 시각이고,
     * 같거나 지났으면(변경 이력이 없을 때 포함) 지금 바꿀 수 있으므로 비어 있다.
     */
    public static Optional<LocalDateTime> nicknameChangeableAt(Optional<LocalDateTime> lastChangedAt,
            Duration restrictPeriod, LocalDateTime now) {
        return lastChangedAt
                .map(changedAt -> changedAt.plus(restrictPeriod))
                .filter(changeableAt -> changeableAt.isAfter(now));
    }
}
