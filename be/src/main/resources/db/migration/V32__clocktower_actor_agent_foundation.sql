CREATE TABLE clocktower_actor (
    id BIGSERIAL PRIMARY KEY,
    actor_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_actor_type ON clocktower_actor (actor_type, deleted);
CREATE INDEX idx_clocktower_actor_user ON clocktower_actor (user_id, deleted);

CREATE TABLE clocktower_agent_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    display_name_template VARCHAR(128) NOT NULL,
    strategy_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    talkativeness INTEGER NOT NULL DEFAULT 50,
    deception_level INTEGER NOT NULL DEFAULT 50,
    aggression INTEGER NOT NULL DEFAULT 50,
    risk_tolerance INTEGER NOT NULL DEFAULT 50,
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_agent_profile_name UNIQUE (name, deleted)
);

INSERT INTO clocktower_agent_profile
    (name, display_name_template, strategy_level, talkativeness, deception_level, aggression, risk_tolerance)
VALUES
    ('balanced', 'Agent {n}', 'NORMAL', 50, 50, 50, 50),
    ('quiet', 'Agent {n}', 'QUIET', 25, 40, 35, 40),
    ('aggressive', 'Agent {n}', 'AGGRESSIVE', 65, 60, 75, 60),
    ('careful', 'Agent {n}', 'CAREFUL', 45, 35, 35, 25);

CREATE TABLE clocktower_agent_instance (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    game_id BIGINT,
    profile_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    room_seat_id BIGINT,
    game_seat_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    auto_mode VARCHAR(32) NOT NULL DEFAULT 'FULL_AUTO',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_agent_instance_actor UNIQUE (actor_id, deleted)
);

CREATE INDEX idx_clocktower_agent_instance_room ON clocktower_agent_instance (room_id, deleted);
CREATE INDEX idx_clocktower_agent_instance_game ON clocktower_agent_instance (game_id, deleted);

ALTER TABLE clocktower_room_seat ADD COLUMN actor_id BIGINT;
ALTER TABLE clocktower_room_seat ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE clocktower_room_seat ADD COLUMN agent_instance_id BIGINT;

CREATE INDEX idx_clocktower_room_seat_actor ON clocktower_room_seat (actor_id);
CREATE INDEX idx_clocktower_room_seat_agent ON clocktower_room_seat (agent_instance_id);

ALTER TABLE clocktower_game_seat ADD COLUMN actor_id BIGINT;
ALTER TABLE clocktower_game_seat ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE clocktower_game_seat ADD COLUMN agent_instance_id BIGINT;

CREATE INDEX idx_clocktower_game_seat_actor ON clocktower_game_seat (actor_id);
CREATE INDEX idx_clocktower_game_seat_agent ON clocktower_game_seat (agent_instance_id);
