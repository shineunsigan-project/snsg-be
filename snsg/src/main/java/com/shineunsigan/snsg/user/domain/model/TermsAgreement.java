package com.shineunsigan.snsg.user.domain.model;

import com.shineunsigan.snsg.common.entity.BaseEntity;
import com.shineunsigan.snsg.user.domain.constants.TermsType;

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
 * 약관 동의 이력. 로그인하면 동의한 것으로 간주하고 한 건씩 쌓는다.
 * 수정·삭제하지 않으므로 변경 주체를 남기지 않는다({@link BaseEntity}).
 * 동의 시각은 {@code createdAt}이다.
 */
@Getter
@Entity
@Table(name = "terms_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TermsType termsType;

    @Column(nullable = false, updatable = false)
    private String version;

    private TermsAgreement(User user, TermsType termsType, String version) {
        this.user = user;
        this.termsType = termsType;
        this.version = version;
    }

    public static TermsAgreement of(User user, TermsType termsType, String version) {
        return new TermsAgreement(user, termsType, version);
    }
}
