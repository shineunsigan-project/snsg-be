package com.shineunsigan.snsg.user.domain.constants;

/**
 * 탈퇴한 이유. 어디까지 정리해야 하는지가 달라진다.
 */
public enum WithdrawReason {

    /** 사용자가 우리 서비스에서 탈퇴했다. 소셜 연동도 우리가 끊어야 한다. */
    USER_REQUESTED,

    /** 사용자가 소셜 쪽에서 연동을 끊었다. 이미 끊겼으므로 우리가 다시 끊지 않는다. */
    SOCIAL_UNLINKED
}
