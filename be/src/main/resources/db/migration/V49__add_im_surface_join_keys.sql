-- V49: add immutable, system-generated join keys for channels and groups.

ALTER TABLE im_channel
    ADD COLUMN join_key VARCHAR(32);

UPDATE im_channel
SET join_key = 'chn_' || LPAD(CAST(id AS VARCHAR), 22, '0')
WHERE join_key IS NULL;

ALTER TABLE im_channel
    ALTER COLUMN join_key SET NOT NULL;

ALTER TABLE im_channel
    ADD CONSTRAINT chk_im_channel_join_key
        CHECK (LEFT(join_key, 4) = 'chn_' AND CHAR_LENGTH(join_key) = 26);

CREATE UNIQUE INDEX uk_im_channel_join_key
    ON im_channel (join_key);

ALTER TABLE im_group
    ADD COLUMN join_key VARCHAR(32);

UPDATE im_group
SET join_key = 'grp_' || LPAD(CAST(id AS VARCHAR), 22, '0')
WHERE join_key IS NULL;

ALTER TABLE im_group
    ALTER COLUMN join_key SET NOT NULL;

ALTER TABLE im_group
    ADD CONSTRAINT chk_im_group_join_key
        CHECK (LEFT(join_key, 4) = 'grp_' AND CHAR_LENGTH(join_key) = 26);

CREATE UNIQUE INDEX uk_im_group_join_key
    ON im_group (join_key);
