package com.shineunsigan.snsg.user.domain.model;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴 여부는 {@code isDeleted}로 안다. 별도 상태 컬럼을 두지 않는다.
 * 운영자 정지처럼 삭제가 아닌 상태가 생기면 그때 상태 값을 더한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseAuditEntity {

    @Embedded
    private Profile profile;

    private User(Profile profile) {
        this.profile = profile;
    }

    public static User create(Profile profile) {
        return new User(profile);
    }

    public void updateProfile(Profile profile) {
        this.profile = profile;
    }

    /**
     * 탈퇴와 비식별 처리를 한 번에 한다. 둘을 나누면 호출하는 쪽에서 하나를 빠뜨려
     * 탈퇴했는데 개인정보가 남은 사용자가 생긴다.
     */
    public void withdraw() {
        if (isDeleted()) {
            throw new BusinessException(UserErrorCode.ALREADY_WITHDRAWN);
        }
        this.profile = profile.anonymized();
        delete();
    }
}
