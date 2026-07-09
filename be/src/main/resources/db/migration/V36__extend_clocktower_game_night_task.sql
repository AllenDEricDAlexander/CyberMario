ALTER TABLE clocktower_game_night_task ADD COLUMN task_type VARCHAR(64);
ALTER TABLE clocktower_game_night_task ADD COLUMN choice_json JSONB;
ALTER TABLE clocktower_game_night_task ADD COLUMN result_json JSONB;
ALTER TABLE clocktower_game_night_task ADD COLUMN resolved_by_actor_id BIGINT;

UPDATE clocktower_game_night_task
SET task_type = 'ST_RESOLVE',
    choice_json = '{}',
    result_json = '{}'
WHERE task_type IS NULL
   OR choice_json IS NULL
   OR result_json IS NULL;

ALTER TABLE clocktower_game_night_task ALTER COLUMN task_type SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN task_type SET DEFAULT 'ST_RESOLVE';
ALTER TABLE clocktower_game_night_task ALTER COLUMN choice_json SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN choice_json SET DEFAULT '{}';
ALTER TABLE clocktower_game_night_task ALTER COLUMN result_json SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN result_json SET DEFAULT '{}';

CREATE INDEX idx_clocktower_game_night_task_actor_status
    ON clocktower_game_night_task (actor_game_seat_id, status);
