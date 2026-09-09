CREATE TABLE participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(40) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_participants_phone UNIQUE (phone)
);

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_categories_code UNIQUE (code)
);

CREATE TABLE sentences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    CONSTRAINT fk_sentences_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_sentences_category_sequence UNIQUE (category_id, sequence_number)
);

CREATE TABLE play_passes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    participant_id BIGINT NOT NULL,
    free_participant_id BIGINT NULL,
    type VARCHAR(10) NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_play_passes_participant FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT fk_play_passes_free_participant FOREIGN KEY (free_participant_id) REFERENCES participants (id),
    CONSTRAINT uk_play_passes_free_participant UNIQUE (free_participant_id),
    CONSTRAINT ck_play_passes_free_owner CHECK (
        (type = 'FREE' AND free_participant_id = participant_id)
        OR (type = 'PAID' AND free_participant_id IS NULL)
    )
);

CREATE TABLE game_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    participant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    play_pass_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    elapsed_ms BIGINT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_game_sessions_participant FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT fk_game_sessions_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_game_sessions_play_pass FOREIGN KEY (play_pass_id) REFERENCES play_passes (id),
    CONSTRAINT ck_game_sessions_elapsed CHECK (elapsed_ms IS NULL OR elapsed_ms > 0)
);

CREATE INDEX idx_sentences_category ON sentences (category_id);
CREATE INDEX idx_play_passes_participant_status ON play_passes (participant_id, status);
CREATE INDEX idx_game_sessions_participant_category_status ON game_sessions (participant_id, category_id, status);
CREATE INDEX idx_game_sessions_category_status_elapsed ON game_sessions (category_id, status, elapsed_ms);
CREATE INDEX idx_game_sessions_play_pass_status ON game_sessions (play_pass_id, status);
