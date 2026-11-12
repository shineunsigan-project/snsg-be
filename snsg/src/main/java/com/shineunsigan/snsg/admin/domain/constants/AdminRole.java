package com.shineunsigan.snsg.admin.domain.constants;

/**
 * 어드민 역할. 토큰에 싣지 않고 요청마다 DB에서 읽는다.
 */
public enum AdminRole {

    /** 가입 승인·거절·이용 중지를 할 수 있다. API로 만들지 않고 DB에 직접 넣는다. */
    MASTER,

    /** 가입 신청으로 생기는 일반 어드민. */
    ADMIN
}
