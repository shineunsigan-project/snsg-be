-- 닉네임 보호 이력. 바꾸기 전 닉네임은 14일, 탈퇴한 회원의 닉네임은 90일 동안 다른 사람이 쓰지 못한다.
-- 마지막 닉네임 변경 시각도 이 테이블의 CHANGED 행으로 판정한다.
-- user 애그리거트의 구성요소라 users에 FK를 건다.

CREATE TABLE user_nickname_histories
(
    id             UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    nickname       VARCHAR(10) NOT NULL,
    reason         VARCHAR(20) NOT NULL,
    reserved_until TIMESTAMP   NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    is_deleted     BOOLEAN     NOT NULL,
    CONSTRAINT pk_user_nickname_histories PRIMARY KEY (id),
    CONSTRAINT fk_user_nickname_histories_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 닉네임 중복 판정이 대소문자를 무시하고 보호 기간이 남은 행을 찾는다.
CREATE INDEX ix_nickname_histories_nickname ON user_nickname_histories (LOWER(nickname), reserved_until);

-- 사용자의 마지막 닉네임 변경 시각을 찾는다.
CREATE INDEX ix_nickname_histories_user ON user_nickname_histories (user_id, reason, created_at);
