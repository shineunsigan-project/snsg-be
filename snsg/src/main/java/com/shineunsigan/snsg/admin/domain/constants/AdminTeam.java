package com.shineunsigan.snsg.admin.domain.constants;

/**
 * 어드민의 소속팀. 목록을 주는 API는 없다. FE가 같은 값을 상수로 갖는다.
 */
public enum AdminTeam {

    /** 기획 */
    PLANNING,

    /** 디자인 */
    DESIGN,

    /** 개발(FE) */
    FRONTEND,

    /** 개발(BE) */
    BACKEND,

    /** 마케팅 */
    MARKETING,

    /** 운영 */
    OPERATIONS,

    /** 사업개발 */
    BUSINESS,

    /** 데이터 */
    DATA,

    /** 경영지원 */
    MANAGEMENT
}
