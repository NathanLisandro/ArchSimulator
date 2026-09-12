CREATE TABLE user_progress (
    user_id      VARCHAR(36) PRIMARY KEY REFERENCES users(id),
    rank         VARCHAR(16)  NOT NULL,
    xp           BIGINT       NOT NULL DEFAULT 0,
    solved_count INTEGER      NOT NULL DEFAULT 0,
    total_score  NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ  NOT NULL,
    document     JSONB        NOT NULL
);

CREATE TABLE sprints (
    id               VARCHAR(36) PRIMARY KEY,
    user_id          VARCHAR(36) NOT NULL REFERENCES users(id),
    status           VARCHAR(16) NOT NULL,
    total_cases      INTEGER     NOT NULL,
    time_limit_secs  BIGINT      NOT NULL,
    level            VARCHAR(16) NOT NULL,
    case_type        VARCHAR(32),
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    document         JSONB       NOT NULL
);
CREATE INDEX idx_sprints_user_started ON sprints (user_id, started_at DESC);
CREATE UNIQUE INDEX uq_sprints_running ON sprints (user_id) WHERE status = 'RUNNING';

CREATE TABLE generated_cards (
    user_id    VARCHAR(36)  NOT NULL REFERENCES users(id),
    card_id    VARCHAR(200) NOT NULL,
    deck_id    VARCHAR(64)  NOT NULL,
    origin     VARCHAR(36),
    document   JSONB        NOT NULL,
    PRIMARY KEY (user_id, card_id)
);
CREATE INDEX idx_generated_cards_deck ON generated_cards (user_id, deck_id);

CREATE TABLE review_schedules (
    user_id          VARCHAR(36)  NOT NULL REFERENCES users(id),
    card_id          VARCHAR(200) NOT NULL,
    repetitions      INTEGER      NOT NULL DEFAULT 0,
    interval_days    INTEGER      NOT NULL DEFAULT 0,
    ease_factor      NUMERIC(4,2) NOT NULL DEFAULT 2.50,
    lapses           INTEGER      NOT NULL DEFAULT 0,
    due_at           TIMESTAMPTZ  NOT NULL,
    last_reviewed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, card_id)
);
CREATE INDEX idx_review_schedules_due ON review_schedules (user_id, due_at);
