UPDATE sys_role
SET permission_version = permission_version + 1
WHERE id IN (SELECT DISTINCT rp.role_id
             FROM sys_role_permission rp
                      JOIN sys_role r ON r.id = rp.role_id
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE r.role_code IN ('CHAT_BASIC', 'CHAT_USER', 'AGENT_DASHBOARD_USER')
               AND p.perm_code IN (
                                   'api:agent:model-audit:dashboard:global',
                                   'api:agent:model-audit:dashboard:user-options'
                 ));

DELETE
FROM sys_role_permission
WHERE id IN (SELECT rp.id
             FROM sys_role_permission rp
                      JOIN sys_role r ON r.id = rp.role_id
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE r.role_code IN ('CHAT_BASIC', 'CHAT_USER', 'AGENT_DASHBOARD_USER')
               AND p.perm_code IN (
                                   'api:agent:model-audit:dashboard:global',
                                   'api:agent:model-audit:dashboard:user-options'
                 ));
