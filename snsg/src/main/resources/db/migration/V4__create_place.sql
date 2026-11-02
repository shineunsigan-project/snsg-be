-- 장소 마스터. 공공데이터에서 적재한 후보를 사람이 검수해 노출한다.
-- 화면 값(name 등)은 검수자가 고친 값이고, 원천 값(source_*)은 적재 때마다 원천 응답으로 갱신한다.
-- 폐업(is_closed)은 검수 상태(status)와 따로 둔다. 원천에 다시 나타나면 is_closed만 돌린다.
-- MANUAL 행의 source_id·source_*는 ''와 0이다.
-- regions와는 다른 애그리거트라 region_code에 FK를 걸지 않는다.

CREATE TABLE places
(
    id                    UUID          NOT NULL,
    place_type            VARCHAR(20)   NOT NULL,
    name                  VARCHAR(100)  NOT NULL,
    road_address          VARCHAR(200)  NOT NULL,
    address_detail        VARCHAR(100)  NOT NULL,
    latitude              NUMERIC(10,7) NOT NULL,
    longitude             NUMERIC(10,7) NOT NULL,
    region_code           CHAR(5)       NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    is_closed             BOOLEAN       NOT NULL,
    source                VARCHAR(20)   NOT NULL,
    source_id             VARCHAR(50)   NOT NULL,
    source_name           VARCHAR(100)  NOT NULL,
    source_road_address   VARCHAR(200)  NOT NULL,
    source_address_detail VARCHAR(100)  NOT NULL,
    source_latitude       NUMERIC(10,7) NOT NULL,
    source_longitude      NUMERIC(10,7) NOT NULL,
    source_region_code    CHAR(5)       NOT NULL,
    last_synced_at        TIMESTAMP     NOT NULL,
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL,
    is_deleted            BOOLEAN       NOT NULL,
    CONSTRAINT pk_places PRIMARY KEY (id),
    -- 유형이 정해지지 않은 장소는 노출할 수 없다. 검수를 DB에서 직접 하므로 DB가 막는다.
    CONSTRAINT ck_places_approved_type CHECK (status <> 'APPROVED' OR place_type <> 'UNCLASSIFIED')
);

-- MANUAL은 source_id가 ''라 여러 행이 있을 수 있다.
CREATE UNIQUE INDEX uix_places_source ON places (source, source_id)
    WHERE is_deleted = FALSE AND source <> 'MANUAL';

CREATE INDEX ix_places_exposed ON places (place_type, region_code, status);
