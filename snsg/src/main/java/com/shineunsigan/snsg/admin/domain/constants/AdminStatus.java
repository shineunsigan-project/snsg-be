package com.shineunsigan.snsg.admin.domain.constants;

/**
 * 어드민 계정 상태. {@link #APPROVED}만 로그인·인증을 통과한다.
 * 그 밖의 상태(거절·이용 중지 등)를 더해도 인증은 자동으로 막힌다.
 * <p>
 * 바뀌는 길은 넷뿐이다: 승인({@code PENDING → APPROVED}), 거절({@code PENDING → REJECTED}),
 * 이용 중지({@code APPROVED → SUSPENDED}), 이용 중지 해제({@code SUSPENDED → APPROVED}).
 */
public enum AdminStatus {

    /** 가입 신청 후 MASTER의 승인을 기다린다. */
    PENDING,

    /** 승인됐다. */
    APPROVED,

    /** 가입이 거절됐다. 되돌리지 않는 끝 상태다. 행을 지우지 않아 같은 아이디로 다시 가입할 수 없다. */
    REJECTED,

    /** 이용 중지됐다. MASTER가 해제하면 다시 승인 상태가 된다. */
    SUSPENDED
}
