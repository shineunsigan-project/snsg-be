-- 서비스 지역(시군구). 행정구역 코드가 키다.
-- 마이그레이션으로만 관리하는 기준 데이터라 UUID 키와 soft delete를 두지 않는다. 지역을 빼려면 is_active를 끈다.

CREATE TABLE regions
(
    code          CHAR(5)     NOT NULL,
    sido_name     VARCHAR(20) NOT NULL,
    sigungu_name  VARCHAR(20) NOT NULL,
    display_order INT         NOT NULL,
    is_active     BOOLEAN     NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    CONSTRAINT pk_regions PRIMARY KEY (code)
);

INSERT INTO regions (code, sido_name, sigungu_name, display_order, is_active, created_at, updated_at)
VALUES ('11440', '서울특별시', '마포구', 1, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul'),
       ('11410', '서울특별시', '서대문구', 2, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul'),
       ('11200', '서울특별시', '성동구', 3, TRUE, NOW() AT TIME ZONE 'Asia/Seoul', NOW() AT TIME ZONE 'Asia/Seoul');
