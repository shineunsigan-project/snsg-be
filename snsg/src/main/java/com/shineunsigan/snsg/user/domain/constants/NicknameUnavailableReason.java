package com.shineunsigan.snsg.user.domain.constants;

/**
 * 닉네임을 쓸 수 없는 이유.
 */
public enum NicknameUnavailableReason {

    /** 2~10자의 한글·영문·숫자·.·_가 아니다. */
    INVALID_FORMAT,

    /** 다른 회원이 쓰고 있거나 보호 기간이 남았다. 보호 중임을 알리면 탈퇴·변경 여부가 드러나므로 둘을 구분하지 않는다. */
    DUPLICATED
}
