ALTER TABLE sys_user
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE sys_user
    ALTER COLUMN status TYPE INTEGER
        USING CASE status
                  WHEN 'DISABLED' THEN 0
                  WHEN 'ENABLED' THEN 1
                  ELSE CAST(status AS INTEGER)
        END;

ALTER TABLE sys_user
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE sys_role
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE sys_role
    ALTER COLUMN status TYPE INTEGER
        USING CASE status
                  WHEN 'DISABLED' THEN 0
                  WHEN 'ENABLED' THEN 1
                  ELSE CAST(status AS INTEGER)
        END;

ALTER TABLE sys_role
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE sys_permission
    ALTER COLUMN perm_type TYPE INTEGER
        USING CASE perm_type
                  WHEN 'MENU' THEN 1
                  WHEN 'BUTTON' THEN 2
                  WHEN 'API' THEN 3
                  ELSE CAST(perm_type AS INTEGER)
        END;

ALTER TABLE sys_permission
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE sys_permission
    ALTER COLUMN status TYPE INTEGER
        USING CASE status
                  WHEN 'DISABLED' THEN 0
                  WHEN 'ENABLED' THEN 1
                  WHEN 'DRAFT' THEN 2
                  ELSE CAST(status AS INTEGER)
        END;

ALTER TABLE sys_permission
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE sys_api
    ALTER COLUMN matcher_type TYPE INTEGER
        USING CASE matcher_type
                  WHEN 'EXACT' THEN 1
                  WHEN 'MVC' THEN 2
                  WHEN 'ANT' THEN 3
                  WHEN 'REGEX' THEN 4
                  ELSE CAST(matcher_type AS INTEGER)
        END;

ALTER TABLE sys_api
    ALTER COLUMN risk_level DROP DEFAULT;

ALTER TABLE sys_api
    ALTER COLUMN risk_level TYPE INTEGER
        USING CASE risk_level
                  WHEN 'LOW' THEN 1
                  WHEN 'MEDIUM' THEN 2
                  WHEN 'HIGH' THEN 3
                  ELSE CAST(risk_level AS INTEGER)
        END;

ALTER TABLE sys_api
    ALTER COLUMN risk_level SET DEFAULT 1;

ALTER TABLE sys_button_api
    ALTER COLUMN relation_type DROP DEFAULT;

ALTER TABLE sys_button_api
    ALTER COLUMN relation_type TYPE INTEGER
        USING CASE relation_type
                  WHEN 'CALLS' THEN 1
                  WHEN 'REQUIRES' THEN 2
                  WHEN 'SUGGESTED' THEN 3
                  ELSE CAST(relation_type AS INTEGER)
        END;

ALTER TABLE sys_button_api
    ALTER COLUMN relation_type SET DEFAULT 1;
