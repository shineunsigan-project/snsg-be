package com.shineunsigan.snsg.user.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.shineunsigan.snsg.user.application.dto.result.UserProfileResult;

/**
 * 타 도메인이 여러 사용자의 프로필을 한 번에 읽는 입구. 남에게 보여줄 정보만 준다.
 * 탈퇴한 사용자와 행이 없는 ID도 결과에서 빠지지 않고 탈퇴자로 가려져 들어간다.
 */
public interface FindUserProfilesUseCase {

    /**
     * 입력의 서로 다른 ID마다 항목이 정확히 하나 있다. 빈 입력이면 빈 맵이다. 순서는 보장하지 않는다.
     * 돌려준 맵은 바꿀 수 없다.
     */
    Map<UUID, UserProfileResult> findProfiles(Collection<UUID> userIds);
}
