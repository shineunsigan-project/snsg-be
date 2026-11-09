package com.shineunsigan.snsg.schedule.infrastructure.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleAgeGroupResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;
import com.shineunsigan.snsg.user.application.dto.result.UserProfileResult;
import com.shineunsigan.snsg.user.application.dto.result.UserProfileResult.AgeGroupResult;
import com.shineunsigan.snsg.user.application.port.in.FindUserProfilesUseCase;
import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;

import lombok.RequiredArgsConstructor;

/**
 * schedule에서 user 도메인을 부르는 자리. 여기서만 user 패키지를 import한다.
 * 이름을 auth의 UserAdapter와 다르게 둔다. 빈 이름이 같으면 기동이 실패한다.
 * <p>
 * user의 enum(성별, 연령대 구간)은 여기서 schedule의 enum으로 바꾼다. 변환은 {@code default} 없는 switch라
 * user에 값이 늘면 컴파일이 깨져 변환 누락을 잡는다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleUserAdapter implements UserPort {

    private final FindUserUseCase findUserUseCase;
    private final FindUserProfilesUseCase findUserProfilesUseCase;

    /** 탈퇴하면 사용자가 삭제 상태가 되어 찾히지 않는다. */
    @Override
    public boolean isActive(UUID userId) {
        return findUserUseCase.exists(userId);
    }

    @Override
    public Map<UUID, ScheduleUserProfileResult> findProfiles(Collection<UUID> userIds) {
        return findUserProfilesUseCase.findProfiles(userIds).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> toResult(entry.getValue())));
    }

    private static ScheduleUserProfileResult toResult(UserProfileResult profile) {
        return new ScheduleUserProfileResult(profile.nickname(), profile.profileImageUrl(),
                toGender(profile.gender()), toAgeGroup(profile.ageGroup()), profile.withdrawn());
    }

    /** 사용자 정보를 찾을 수 없는 id는 성별이 없다. */
    private static Gender toGender(com.shineunsigan.snsg.user.domain.constants.Gender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case FEMALE -> Gender.FEMALE;
            case MALE -> Gender.MALE;
        };
    }

    /** 탈퇴자·사용자 정보를 찾을 수 없는 id는 연령대가 없다. */
    private static ScheduleAgeGroupResult toAgeGroup(AgeGroupResult ageGroup) {
        if (ageGroup == null) {
            return null;
        }
        return new ScheduleAgeGroupResult(ageGroup.decade(), toPhase(ageGroup.phase()));
    }

    private static AgePhase toPhase(com.shineunsigan.snsg.user.domain.constants.AgePhase phase) {
        return switch (phase) {
            case EARLY -> AgePhase.EARLY;
            case MID -> AgePhase.MID;
            case LATE -> AgePhase.LATE;
        };
    }
}
