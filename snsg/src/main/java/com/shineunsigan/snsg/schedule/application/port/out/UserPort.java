package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleUserProfileResult;

/**
 * user 도메인을 부르는 통로. 구현(어댑터)만 user 패키지를 안다.
 */
public interface UserPort {

    /** 탈퇴하지 않은 사용자인지. 남은 access token으로 개설·참여하는 탈퇴 사용자를 막는다. */
    boolean isActive(UUID userId);

    /**
     * 여러 사용자의 프로필을 한 번에 읽는다. 입력의 서로 다른 id마다 항목이 정확히 하나 있다.
     * 탈퇴했거나 사용자 정보를 찾을 수 없는 id도 빠지지 않고 가린 값으로 들어간다.
     */
    Map<UUID, ScheduleUserProfileResult> findProfiles(Collection<UUID> userIds);
}
