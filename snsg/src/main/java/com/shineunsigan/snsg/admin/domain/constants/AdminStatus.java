package com.shineunsigan.snsg.admin.domain.constants;

/**
 * 어드민 계정 상태. {@link #APPROVED}만 로그인·인증을 통과한다.
 * 그 밖의 상태(거절·이용 중지 등)를 더해도 인증은 자동으로 막힌다.
 */
public enum AdminStatus {

    /** 가입 신청 후 MASTER의 승인을 기다린다. */
    PENDING,

    /** 승인됐다. */
    APPROVED
}
