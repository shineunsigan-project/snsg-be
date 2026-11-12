package com.shineunsigan.snsg.admin.application.port.out;

import java.time.Duration;

/**
 * 어드민 로그인 연속 실패 기록과 잠금. 아이디는 입력한 문자열 그대로 묶는다.
 * 계정이 없는 아이디도 똑같이 센다. 그래야 잠금 응답으로 계정이 있는지 드러나지 않는다.
 * <p>
 * 잠금 정책(횟수·시간)은 호출하는 쪽이 넘긴다. 구현은 infrastructure의 Redis 어댑터다.
 */
public interface LoginAttemptPort {

    boolean isLocked(String loginId);

    /**
     * 실패를 하나 센다. 센 횟수가 {@code maxFailures}에 이르면 지금부터 {@code lockDuration} 동안 잠그고 참을 돌려준다.
     * 실패 사이가 {@code lockDuration}보다 벌어지면 횟수는 사라진다.
     */
    boolean recordFailure(String loginId, int maxFailures, Duration lockDuration);

    /** 실패 횟수와 잠금을 지운다. 비밀번호가 맞았을 때 부른다. */
    void reset(String loginId);
}
