-- 일정 전체 취소 기록. 일정 애그리거트의 구성요소라 schedules에 FK를 건다.
-- 취소 일시는 created_at, 취소한 사람은 created_by다. 운영자 취소가 생기면 같은 테이블을 쓰고 created_by로 구분한다.

CREATE TABLE schedule_cancellations
(
    id          UUID         NOT NULL,
    schedule_id UUID         NOT NULL,
    reason      VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  VARCHAR(50)  NOT NULL,
    updated_by  VARCHAR(50)  NOT NULL,
    is_deleted  BOOLEAN      NOT NULL,
    CONSTRAINT pk_schedule_cancellations PRIMARY KEY (id),
    CONSTRAINT fk_schedule_cancellations_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
);

-- 일정 하나에 취소 기록은 하나다. 취소는 일정 행을 잠근 뒤 판정하므로 이 인덱스는 마지막 방어선이다.
CREATE UNIQUE INDEX uix_schedule_cancellations_schedule ON schedule_cancellations (schedule_id) WHERE is_deleted = FALSE;
