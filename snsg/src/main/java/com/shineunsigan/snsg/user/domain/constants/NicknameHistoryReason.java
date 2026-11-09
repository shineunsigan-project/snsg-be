package com.shineunsigan.snsg.user.domain.constants;

/**
 * 닉네임 보호 이력을 남긴 이유.
 */
public enum NicknameHistoryReason {

    /** 닉네임을 바꿨다. 이전 닉네임을 보호하고, 가장 최근 행의 시각으로 다음 변경을 제한한다. */
    CHANGED,

    /** 탈퇴했다. 탈퇴 시점 닉네임을 보호한다. */
    WITHDRAWN
}
