-- 운영 도구에 로그인하는 어드민 계정. 서비스 사용자(users)와 따로 둔다.
-- 가입 신청은 ADMIN·PENDING으로 저장되고, MASTER는 운영자가 DB에 직접 넣는다(README).
-- role·status 값은 앱이 정한다. 상태를 더할 때 마이그레이션이 필요 없도록 값 제약을 두지 않는다.

CREATE TABLE admins
(
    id            UUID         NOT NULL,
    login_id      VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(20)  NOT NULL,
    team          VARCHAR(20)  NOT NULL,
    birth_date    DATE         NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(50)  NOT NULL,
    updated_by    VARCHAR(50)  NOT NULL,
    is_deleted    BOOLEAN      NOT NULL,
    CONSTRAINT pk_admins PRIMARY KEY (id)
);

-- 아이디 중복 금지. 영문 소문자만 받으므로 lower()를 쓰지 않는다.
CREATE UNIQUE INDEX uix_admins_login_id ON admins (login_id) WHERE is_deleted = FALSE;
