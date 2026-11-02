-- 가입 시 받는 이름·이메일과 대소문자를 무시하는 닉네임 중복 판정.

-- 기존 행은 빈 문자열로 채우고 기본값은 남기지 않는다. 값은 애플리케이션이 항상 넣는다.
-- 이메일은 선택 입력이라 없으면 ''다.
ALTER TABLE users ADD COLUMN name VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN email VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN name DROP DEFAULT;
ALTER TABLE users ALTER COLUMN email DROP DEFAULT;

-- Mint와 mint를 같은 닉네임으로 본다.
DROP INDEX uix_users_nickname;
CREATE UNIQUE INDEX uix_users_nickname ON users (LOWER(nickname)) WHERE is_deleted = FALSE;
