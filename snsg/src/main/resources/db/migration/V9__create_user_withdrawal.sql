-- 탈퇴 기록. 원본은 anonymize_due_at(탈퇴 시각 + 보관 기간)까지 보관하고, 그 뒤 비식별 배치가 비식별한다.
-- 탈퇴 일시는 created_at이다. user 애그리거트의 구성요소라 users에 FK를 건다.

CREATE TABLE user_withdrawals
(
    id               UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    reason           VARCHAR(30) NOT NULL,
    anonymize_due_at TIMESTAMP   NOT NULL,
    anonymized       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    created_by       VARCHAR(50) NOT NULL,
    updated_by       VARCHAR(50) NOT NULL,
    is_deleted       BOOLEAN     NOT NULL,
    CONSTRAINT pk_user_withdrawals PRIMARY KEY (id),
    CONSTRAINT fk_user_withdrawals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 회원 한 명에 탈퇴 기록은 하나다.
CREATE UNIQUE INDEX uix_user_withdrawals_user ON user_withdrawals (user_id) WHERE is_deleted = FALSE;

-- 비식별 배치가 아직 비식별하지 않은 대상 중 예정 시각이 지난 행을 찾는다.
CREATE INDEX ix_user_withdrawals_due ON user_withdrawals (anonymized, anonymize_due_at);
