-- 장소를 어드민 API로 검수·수정·등록한다. 누가 바꿨는지 남기려고 places를 감사 엔티티로 바꾼다.
-- 기존 행은 마이그레이션·적재로 들어와 사람이 남긴 주체가 없으므로 SYSTEM으로 채운다.
-- 엔티티가 항상 값을 넣으므로 DEFAULT는 남기지 않는다.
--
-- updated_by는 분기 적재가 확인한 행마다 SYSTEM_SCHEDULER로 덮는다. 그래서 마지막으로 검수한 어드민은
-- reviewed_by·reviewed_at에 따로 둔다. 둘은 어드민 수정·등록만 바꾸고 적재·폐업 처리는 바꾸지 않는다.
-- reviewed_by가 ''이고 reviewed_at이 NULL이면 API로 검수한 적이 없는 행이다(마이그레이션·DB 직접 검수 포함).

ALTER TABLE places ADD COLUMN created_by VARCHAR(50);
ALTER TABLE places ADD COLUMN updated_by VARCHAR(50);
ALTER TABLE places ADD COLUMN reviewed_by VARCHAR(50);
ALTER TABLE places ADD COLUMN reviewed_at TIMESTAMP;

UPDATE places SET created_by = 'SYSTEM', updated_by = 'SYSTEM', reviewed_by = '';

ALTER TABLE places ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE places ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE places ALTER COLUMN reviewed_by SET NOT NULL;
