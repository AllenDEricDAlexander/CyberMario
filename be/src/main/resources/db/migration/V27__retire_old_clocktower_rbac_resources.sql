UPDATE sys_role
SET permission_version = permission_version + 1
WHERE id IN (SELECT DISTINCT rp.role_id
             FROM sys_role_permission rp
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE p.deleted = FALSE
               AND p.perm_code IN (
                                   'api:clocktower:rooms:*',
                                   'api:clocktower:scripts:*',
                                   'api:clocktower:terms:read',
                                   'api:clocktower:jinx-rules:read',
                                   'api:clocktower:boards:*',
                                   'api:clocktower:rooms:read:list',
                                   'api:clocktower:rooms:read:detail',
                                   'api:clocktower:rooms:player:join',
                                   'api:clocktower:rooms:player:leave',
                                   'api:clocktower:rooms:player:view',
                                   'api:clocktower:rooms:player:action',
                                   'api:clocktower:rooms:chat:*',
                                   'api:clocktower:chat:*',
                                   'api:clocktower:rooms:storyteller:create',
                                   'api:clocktower:rooms:storyteller:start',
                                   'api:clocktower:rooms:storyteller:seat',
                                   'api:clocktower:rooms:storyteller:game:start',
                                   'api:clocktower:games:storyteller:end',
                                   'api:clocktower:games:storyteller:abort',
                                   'api:clocktower:rooms:storyteller:game:timeout-abort',
                                   'api:clocktower:rooms:storyteller:night',
                                   'api:clocktower:rooms:storyteller:flow',
                                   'api:clocktower:rooms:storyteller:night-task',
                                   'api:clocktower:rooms:storyteller:nomination',
                                   'api:clocktower:rooms:storyteller:execution',
                                   'api:clocktower:rooms:storyteller:action',
                                   'api:clocktower:rooms:storyteller:ruling',
                                   'api:clocktower:rooms:storyteller:ruling:detail',
                                   'api:clocktower:events:stream',
                                   'api:clocktower:grimoire:*',
                                   'api:clocktower:replays:*'
                 ));

DELETE
FROM sys_role_permission
WHERE id IN (SELECT rp.id
             FROM sys_role_permission rp
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE p.deleted = FALSE
               AND p.perm_code IN (
                                   'api:clocktower:rooms:*',
                                   'api:clocktower:scripts:*',
                                   'api:clocktower:terms:read',
                                   'api:clocktower:jinx-rules:read',
                                   'api:clocktower:boards:*',
                                   'api:clocktower:rooms:read:list',
                                   'api:clocktower:rooms:read:detail',
                                   'api:clocktower:rooms:player:join',
                                   'api:clocktower:rooms:player:leave',
                                   'api:clocktower:rooms:player:view',
                                   'api:clocktower:rooms:player:action',
                                   'api:clocktower:rooms:chat:*',
                                   'api:clocktower:chat:*',
                                   'api:clocktower:rooms:storyteller:create',
                                   'api:clocktower:rooms:storyteller:start',
                                   'api:clocktower:rooms:storyteller:seat',
                                   'api:clocktower:rooms:storyteller:game:start',
                                   'api:clocktower:games:storyteller:end',
                                   'api:clocktower:games:storyteller:abort',
                                   'api:clocktower:rooms:storyteller:game:timeout-abort',
                                   'api:clocktower:rooms:storyteller:night',
                                   'api:clocktower:rooms:storyteller:flow',
                                   'api:clocktower:rooms:storyteller:night-task',
                                   'api:clocktower:rooms:storyteller:nomination',
                                   'api:clocktower:rooms:storyteller:execution',
                                   'api:clocktower:rooms:storyteller:action',
                                   'api:clocktower:rooms:storyteller:ruling',
                                   'api:clocktower:rooms:storyteller:ruling:detail',
                                   'api:clocktower:events:stream',
                                   'api:clocktower:grimoire:*',
                                   'api:clocktower:replays:*'
                 ));

UPDATE sys_permission
SET status = 0,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE perm_code IN (
                    'api:clocktower:rooms:*',
                    'api:clocktower:scripts:*',
                    'api:clocktower:terms:read',
                    'api:clocktower:jinx-rules:read',
                    'api:clocktower:boards:*',
                    'api:clocktower:rooms:read:list',
                    'api:clocktower:rooms:read:detail',
                    'api:clocktower:rooms:player:join',
                    'api:clocktower:rooms:player:leave',
                    'api:clocktower:rooms:player:view',
                    'api:clocktower:rooms:player:action',
                    'api:clocktower:rooms:chat:*',
                    'api:clocktower:chat:*',
                    'api:clocktower:rooms:storyteller:create',
                    'api:clocktower:rooms:storyteller:start',
                    'api:clocktower:rooms:storyteller:seat',
                    'api:clocktower:rooms:storyteller:game:start',
                    'api:clocktower:games:storyteller:end',
                    'api:clocktower:games:storyteller:abort',
                    'api:clocktower:rooms:storyteller:game:timeout-abort',
                    'api:clocktower:rooms:storyteller:night',
                    'api:clocktower:rooms:storyteller:flow',
                    'api:clocktower:rooms:storyteller:night-task',
                    'api:clocktower:rooms:storyteller:nomination',
                    'api:clocktower:rooms:storyteller:execution',
                    'api:clocktower:rooms:storyteller:action',
                    'api:clocktower:rooms:storyteller:ruling',
                    'api:clocktower:rooms:storyteller:ruling:detail',
                    'api:clocktower:events:stream',
                    'api:clocktower:grimoire:*',
                    'api:clocktower:replays:*'
    )
  AND deleted = FALSE;
