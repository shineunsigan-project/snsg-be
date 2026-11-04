package com.shineunsigan.snsg.schedule.application.port.out;

import java.util.UUID;

/**
 * user 도메인을 부르는 통로. 구현(어댑터)만 user 패키지를 안다.
 */
public interface UserPort {

    /** 탈퇴하지 않은 사용자인지. 남은 access token으로 개설·참여하는 탈퇴 사용자를 막는다. */
    boolean isActive(UUID userId);
}
