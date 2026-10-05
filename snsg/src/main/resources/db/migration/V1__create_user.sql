-- 사용자와 소셜 계정, 약관 동의 이력.
-- 모든 테이블은 soft delete 한다. 유니크 제약은 삭제되지 않은 행에만 건다.

CREATE TABLE users
(
    id                UUID         NOT NULL,
    nickname          VARCHAR(10)  NOT NULL,
    gender            VARCHAR(10)  NOT NULL,
    birth_date        DATE         NOT NULL,
    profile_image_url VARCHAR(500) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    updated_by        VARCHAR(50)  NOT NULL,
    is_deleted        BOOLEAN      NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uix_users_nickname ON users (nickname) WHERE is_deleted = FALSE;

CREATE TABLE user_social_accounts
(
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    created_by       VARCHAR(50)  NOT NULL,
    updated_by       VARCHAR(50)  NOT NULL,
    is_deleted       BOOLEAN      NOT NULL,
    CONSTRAINT pk_user_social_accounts PRIMARY KEY (id),
    CONSTRAINT fk_user_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 탈퇴 후 같은 소셜 계정으로 다시 가입하면 새 사용자가 된다.
CREATE UNIQUE INDEX uix_user_social_accounts_provider
    ON user_social_accounts (provider, provider_user_id) WHERE is_deleted = FALSE;

CREATE INDEX ix_user_social_accounts_user ON user_social_accounts (user_id);

CREATE TABLE terms_agreements
(
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    terms_type VARCHAR(30) NOT NULL,
    version    VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    is_deleted BOOLEAN     NOT NULL,
    CONSTRAINT pk_terms_agreements PRIMARY KEY (id),
    CONSTRAINT fk_terms_agreements_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_terms_agreements_user ON terms_agreements (user_id);
