-- 일정과 참여 행. schedule 도메인이 소유한다.
-- 개설자(host_user_id)·활동(category_id)·장소(place_id)·지역(region_code)은 다른 애그리거트나 다른 도메인이라 FK를 걸지 않는다.
-- 장소는 개설 시점의 값을 place_* 컬럼에 사본으로 둔다. 장소가 검수에서 고쳐지거나 폐업해도 열린 일정은 그대로 보인다.
-- 인원 4~24 같은 정책 값은 설정·도메인에서 검사하고, 확정 인원 ≤ 정원만 어떤 정책에서도 깨지면 안 되는 불변식이라 CHECK로 둔다.

CREATE TABLE schedules
(
    id                   UUID          NOT NULL,
    host_user_id         UUID          NOT NULL,
    host_type            VARCHAR(20)   NOT NULL,
    category_id          UUID          NOT NULL,
    title                VARCHAR(20)   NOT NULL,
    description          VARCHAR(500)  NOT NULL,
    duration_type        VARCHAR(10)   NOT NULL,
    start_at             TIMESTAMP     NOT NULL,
    capacity             INT           NOT NULL,
    confirmed_count      INT           NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    recruitment_status   VARCHAR(20)   NOT NULL,
    place_id             UUID          NOT NULL,
    place_name           VARCHAR(100)  NOT NULL,
    place_road_address   VARCHAR(200)  NOT NULL,
    place_address_detail VARCHAR(100)  NOT NULL,
    place_latitude       NUMERIC(10,7) NOT NULL,
    place_longitude      NUMERIC(10,7) NOT NULL,
    region_code          CHAR(5)       NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR(50)   NOT NULL,
    updated_by           VARCHAR(50)   NOT NULL,
    is_deleted           BOOLEAN       NOT NULL,
    CONSTRAINT pk_schedules PRIMARY KEY (id),
    CONSTRAINT ck_schedules_count CHECK (confirmed_count >= 0 AND confirmed_count <= capacity)
);

-- 목록 커서가 (start_at, id)이고 시작 시각이 30분 단위라 같은 start_at이 자주 겹친다. id까지 있어야 인덱스만으로 끊는다.
CREATE INDEX ix_schedules_status_start ON schedules (status, start_at, id);

CREATE INDEX ix_schedules_host ON schedules (host_user_id, start_at);

-- 일정의 구성요소. 사용자(user_id)는 다른 도메인이라 FK를 걸지 않는다.
CREATE TABLE schedule_participants
(
    id          UUID        NOT NULL,
    schedule_id UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    role        VARCHAR(10) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    is_deleted  BOOLEAN     NOT NULL,
    CONSTRAINT pk_schedule_participants PRIMARY KEY (id),
    CONSTRAINT fk_schedule_participants_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
);

-- 한 일정에 같은 사용자의 확정 참여는 하나뿐이다. 취소한 행은 남기고 다시 참여하면 새 행을 만든다.
CREATE UNIQUE INDEX uix_participants_confirmed ON schedule_participants (schedule_id, user_id)
    WHERE status = 'CONFIRMED' AND is_deleted = FALSE;

-- 시간 겹침 검사가 내 확정 참여 행에서 출발한다.
CREATE INDEX ix_participants_user ON schedule_participants (user_id, status);
