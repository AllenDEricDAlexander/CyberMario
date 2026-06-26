UPDATE sys_role
SET permission_version = permission_version + 1
WHERE id IN (SELECT DISTINCT rp.role_id
             FROM sys_role_permission rp
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE p.deleted = FALSE
               AND p.perm_code = 'menu:agent:mcp-tools');

DELETE
FROM sys_role_permission
WHERE id IN (SELECT rp.id
             FROM sys_role_permission rp
                      JOIN sys_permission p ON p.id = rp.permission_id
             WHERE p.deleted = FALSE
               AND p.perm_code = 'menu:agent:mcp-tools');

UPDATE sys_permission
SET status = 0,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE perm_code = 'menu:agent:mcp-tools'
  AND deleted = FALSE;
