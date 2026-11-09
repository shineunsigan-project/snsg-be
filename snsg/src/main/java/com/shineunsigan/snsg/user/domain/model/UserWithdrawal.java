package com.shineunsigan.snsg.user.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴 기록. User 애그리거트의 구성요소다. 탈퇴 일시는 {@code createdAt}이다.
 * 원본은 {@code anonymizeDueAt}까지 보관하고, 그 뒤 비식별 배치가 비식별하고 {@code anonymized}를 켠다.
 * 누가 탈퇴시켰는지(본인·웹훅)와 누가 비식별했는지(스케줄러)는 도메인 컬럼으로 알 수 없어 감사 컬럼을 둔다.
 */
@Getter
@Entity
@Table(name = "user_withdrawals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWithdrawal extends BaseAuditEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private WithdrawReason reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime anonymizeDueAt;

    @Column(nullable = false)
    private boolean anonymized;

    private UserWithdrawal(User user, WithdrawReason reason, LocalDateTime anonymizeDueAt) {
        this.user = user;
        this.reason = reason;
        this.anonymizeDueAt = anonymizeDueAt;
        this.anonymized = false;
    }

    /**
     * 탈퇴했다. 원본을 {@code withdrawnAt}부터 {@code retention} 동안 보관한다.
     */
    public static UserWithdrawal of(User user, WithdrawReason reason, LocalDateTime withdrawnAt, Duration retention) {
        return new UserWithdrawal(user, reason, withdrawnAt.plus(retention));
    }

    public void markAnonymized() {
        this.anonymized = true;
    }
}
