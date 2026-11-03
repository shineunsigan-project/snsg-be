-- 활동 카테고리. 일정을 분류하는 기준 데이터이고 schedule 도메인이 소유한다.
-- 활동 추가를 배포 없이 데이터로 끝내려고 enum이 아니라 테이블로 둔다. 쓰기 경로가 없어 DB에서 직접 바꾼다.
-- place_type은 그 활동이 고를 수 있는 장소 유형이다. places와는 다른 도메인이라 FK가 없다.
-- 초기 행의 id는 고정 UUID v7(시각 부분은 작성일)이라 어느 환경에 적용해도 같다.

CREATE TABLE activity_categories
(
    id            UUID        NOT NULL,
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(20) NOT NULL,
    place_type    VARCHAR(20) NOT NULL,
    display_order INT         NOT NULL,
    is_active     BOOLEAN     NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    is_deleted    BOOLEAN     NOT NULL,
    CONSTRAINT pk_activity_categories PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uix_activity_categories_code ON activity_categories (code) WHERE is_deleted = FALSE;

INSERT INTO activity_categories (id, code, name, place_type, display_order, is_active, created_at, updated_at,
                                 is_deleted)
VALUES ('01a19cc6-3d80-7000-8000-000000000001', 'BOARD_GAME', '보드게임', 'BOARD_GAME_CAFE', 1, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul', FALSE),
       ('01a19cc6-3d80-7000-8000-000000000002', 'ESCAPE_ROOM', '방탈출', 'ESCAPE_ROOM', 2, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul', FALSE),
       ('01a19cc6-3d80-7000-8000-000000000003', 'WALK', '산책', 'PARK', 3, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul', FALSE);
