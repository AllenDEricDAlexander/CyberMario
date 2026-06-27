ALTER TABLE sys_user
    ADD COLUMN account_no VARCHAR(64);

UPDATE sys_user
SET account_no = username
WHERE account_no IS NULL;

ALTER TABLE sys_user
    ALTER COLUMN account_no SET NOT NULL;

ALTER TABLE sys_user
    ADD CONSTRAINT uk_user_account_no_deleted UNIQUE (account_no, deleted);
