CREATE TABLE clocktower_script (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    edition VARCHAR(128) NOT NULL,
    min_players INTEGER NOT NULL,
    max_players INTEGER NOT NULL,
    role_count INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source_url VARCHAR(512),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_script_code UNIQUE (script_code)
);

CREATE TABLE clocktower_role (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    alignment VARCHAR(32) NOT NULL,
    ability_text TEXT NOT NULL,
    first_night BOOLEAN NOT NULL DEFAULT FALSE,
    other_night BOOLEAN NOT NULL DEFAULT FALSE,
    setup_modifier BOOLEAN NOT NULL DEFAULT FALSE,
    complexity INTEGER NOT NULL DEFAULT 1,
    first_night_order INTEGER,
    other_night_order INTEGER,
    first_night_reminder TEXT,
    other_night_reminder TEXT,
    source_url VARCHAR(512),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_role_code UNIQUE (role_code)
);

CREATE TABLE clocktower_night_order (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    night_type VARCHAR(32) NOT NULL,
    order_no INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    wake_condition TEXT,
    reminder_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_jinx_rule (
    id BIGSERIAL PRIMARY KEY,
    role_a_code VARCHAR(64) NOT NULL,
    role_b_code VARCHAR(64) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    effect_type VARCHAR(64) NOT NULL,
    rule_text TEXT NOT NULL,
    source_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_room (
    id BIGSERIAL PRIMARY KEY,
    room_code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    player_count INTEGER NOT NULL,
    storyteller_user_id BIGINT,
    storyteller_mode VARCHAR(32) NOT NULL,
    allow_spectators BOOLEAN NOT NULL DEFAULT FALSE,
    allow_private_chat BOOLEAN NOT NULL DEFAULT TRUE,
    current_day_no INTEGER NOT NULL DEFAULT 0,
    current_night_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_room_code UNIQUE (room_code)
);

CREATE TABLE clocktower_seat (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_no INTEGER NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(64),
    role_type VARCHAR(32),
    alignment VARCHAR(32),
    life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE',
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    has_dead_vote BOOLEAN NOT NULL DEFAULT TRUE,
    is_traveler BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_seat_room_no UNIQUE (room_id, seat_no)
);

CREATE TABLE clocktower_event (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    event_seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    actor_user_id BIGINT,
    actor_seat_id BIGINT,
    target_seat_id BIGINT,
    visibility VARCHAR(32) NOT NULL,
    visible_seat_ids_json JSONB NOT NULL DEFAULT '[]',
    payload_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_event_room_seq UNIQUE (room_id, event_seq)
);

CREATE TABLE clocktower_grimoire_entry (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    alignment VARCHAR(32) NOT NULL,
    token_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reminder_tokens_json JSONB NOT NULL DEFAULT '[]',
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_grimoire_room_seat UNIQUE (room_id, seat_id)
);

CREATE TABLE clocktower_status_marker (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_id BIGINT,
    marker_code VARCHAR(64) NOT NULL,
    marker_name VARCHAR(128) NOT NULL,
    marker_source VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_phase VARCHAR(32),
    payload_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_nomination (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    day_no INTEGER NOT NULL,
    nominator_seat_id BIGINT NOT NULL,
    nominee_seat_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    vote_count INTEGER NOT NULL DEFAULT 0,
    executed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_vote (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    nomination_id BIGINT NOT NULL,
    voter_seat_id BIGINT NOT NULL,
    vote_value BOOLEAN NOT NULL,
    used_dead_vote BOOLEAN NOT NULL DEFAULT FALSE,
    event_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_vote_nomination_voter UNIQUE (nomination_id, voter_seat_id)
);

CREATE TABLE clocktower_board_config (
    id BIGSERIAL PRIMARY KEY,
    board_code VARCHAR(64) NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    player_count INTEGER NOT NULL,
    difficulty INTEGER NOT NULL,
    chaos INTEGER NOT NULL,
    evil_pressure INTEGER NOT NULL,
    newbie_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    seed VARCHAR(128),
    validation_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_board_code UNIQUE (board_code)
);

CREATE TABLE clocktower_board_role (
    id BIGSERIAL PRIMARY KEY,
    board_config_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    seat_no INTEGER,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_storyteller_task (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    role_code VARCHAR(64),
    seat_id BIGINT,
    status VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_event_room_seq ON clocktower_event (room_id, event_seq);
CREATE INDEX idx_clocktower_seat_room ON clocktower_seat (room_id);
CREATE INDEX idx_clocktower_board_role_config ON clocktower_board_role (board_config_id);
CREATE INDEX idx_clocktower_role_script ON clocktower_role (script_code);
CREATE INDEX idx_clocktower_night_order_script_type ON clocktower_night_order (script_code, night_type, order_no);
CREATE INDEX idx_clocktower_jinx_role_a ON clocktower_jinx_rule (role_a_code);
CREATE INDEX idx_clocktower_jinx_role_b ON clocktower_jinx_rule (role_b_code);

INSERT INTO clocktower_script (script_code, name, edition, min_players, max_players, role_count, source_url, sort_order)
VALUES
('TROUBLE_BREWING', '暗流涌动', 'BASE_3', 5, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E6%9A%97%E6%B5%81%E6%B6%8C%E5%8A%A8', 10),
('BAD_MOON_RISING', '黯月初升', 'BASE_3', 7, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E9%BB%AF%E6%9C%88%E5%88%9D%E5%8D%87', 20),
('SECTS_AND_VIOLETS', '梦殒春宵', 'BASE_3', 7, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E6%A2%A6%E6%AE%92%E6%98%A5%E5%AE%B5', 30);

INSERT INTO clocktower_role (script_code, role_code, name, role_type, alignment, ability_text, first_night, other_night,
                             first_night_order, other_night_order, first_night_reminder, other_night_reminder,
                             source_url, sort_order)
VALUES
('TROUBLE_BREWING', 'CHEF', '厨师', 'TOWNSFOLK', 'GOOD', '你会得知场上邻座邪恶玩家的对数。', TRUE, FALSE, 10, NULL,
 '告知邻座邪恶玩家对数。', NULL, NULL, 10),
('TROUBLE_BREWING', 'EMPATH', '共情者', 'TOWNSFOLK', 'GOOD', '每个夜晚，你会得知与你邻座的邪恶玩家数量。', TRUE, TRUE, 20, 20,
 '告知邻座邪恶玩家数量。', '告知邻座邪恶玩家数量。', NULL, 20),
('TROUBLE_BREWING', 'MONK', '僧侣', 'TOWNSFOLK', 'GOOD', '每个夜晚，选择除你以外的一名玩家：恶魔的攻击不会杀死该玩家。', FALSE, TRUE, NULL, 30,
 NULL, '选择要保护的玩家。', NULL, 30),
('TROUBLE_BREWING', 'POISONER', '投毒者', 'MINION', 'EVIL', '每个夜晚，选择一名玩家：该玩家中毒。', TRUE, TRUE, 5, 5,
 '选择中毒玩家。', '选择中毒玩家。', NULL, 40),
('TROUBLE_BREWING', 'IMP', '小恶魔', 'DEMON', 'EVIL', '每个夜晚，选择一名玩家：该玩家死亡。', FALSE, TRUE, NULL, 40,
 NULL, '选择要杀死的玩家。', NULL, 50);

INSERT INTO clocktower_night_order (script_code, role_code, night_type, order_no, sort_order, reminder_text)
VALUES
('TROUBLE_BREWING', 'POISONER', 'FIRST_NIGHT', 5, 5, '投毒者选择一名玩家中毒。'),
('TROUBLE_BREWING', 'CHEF', 'FIRST_NIGHT', 10, 10, '厨师得知邻座邪恶玩家对数。'),
('TROUBLE_BREWING', 'EMPATH', 'FIRST_NIGHT', 20, 20, '共情者得知邻座邪恶玩家数量。'),
('TROUBLE_BREWING', 'POISONER', 'OTHER_NIGHT', 5, 5, '投毒者选择一名玩家中毒。'),
('TROUBLE_BREWING', 'EMPATH', 'OTHER_NIGHT', 20, 20, '共情者得知邻座邪恶玩家数量。'),
('TROUBLE_BREWING', 'MONK', 'OTHER_NIGHT', 30, 30, '僧侣选择一名玩家保护。'),
('TROUBLE_BREWING', 'IMP', 'OTHER_NIGHT', 40, 40, '小恶魔选择一名玩家死亡。');
