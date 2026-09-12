CREATE TABLE users (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    display_name  VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE cases (
    id           VARCHAR(36) PRIMARY KEY,
    company_id   VARCHAR(64) NOT NULL,
    template_id  VARCHAR(120) NOT NULL,
    case_type    VARCHAR(32) NOT NULL,
    level        VARCHAR(16) NOT NULL,
    seed         BIGINT      NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    document     JSONB       NOT NULL
);

CREATE TABLE attempts (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    case_id      VARCHAR(36) NOT NULL REFERENCES cases(id),
    status       VARCHAR(16) NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    score        NUMERIC(5,2),
    sprint_id    VARCHAR(36),
    document     JSONB       NOT NULL
);
CREATE INDEX idx_attempts_user_started ON attempts (user_id, started_at DESC);
CREATE INDEX idx_attempts_case ON attempts (case_id);
