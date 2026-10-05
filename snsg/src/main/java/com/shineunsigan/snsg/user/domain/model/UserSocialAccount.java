package com.shineunsigan.snsg.user.domain.model;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

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
 * 사용자와 소셜 계정의 연결. User 애그리거트의 구성요소다.
 * 삭제되지 않은 행에만 (provider, providerUserId) 유니크가 걸려 있어,
 * 탈퇴 후 같은 소셜 계정으로 다시 가입하면 새 사용자가 된다.
 */
@Getter
@Entity
@Table(name = "user_social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(nullable = false, updatable = false)
    private String providerUserId;

    private UserSocialAccount(User user, SocialProvider provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static UserSocialAccount of(User user, SocialProvider provider, String providerUserId) {
        return new UserSocialAccount(user, provider, providerUserId);
    }
}
