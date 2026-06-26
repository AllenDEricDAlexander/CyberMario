import {mcpButtonCodes} from '../../modules/agent/mcp/mcpPermissionCodes'
import {ragButtonCodes} from '../../modules/rag/ragPermissionCodes'
import {rbacButtonCodes} from '../../modules/rbac/rbacPermissionCodes'

type RouteButtonPermission = {
    path: string
    buttonCodes: string[]
}

const routeButtonPermissions: RouteButtonPermission[] = [
    {path: '/rag/knowledge-bases', buttonCodes: Object.values(ragButtonCodes.kb)},
    {
        path: '/rag/documents',
        buttonCodes: [...Object.values(ragButtonCodes.doc), ...Object.values(ragButtonCodes.chunk)]
    },
    {path: '/rag/ingestion-jobs', buttonCodes: Object.values(ragButtonCodes.job)},
    {
        path: '/agent/mcp/servers',
        buttonCodes: [...Object.values(mcpButtonCodes.server), ...Object.values(mcpButtonCodes.tool)],
    },
    {path: '/agent/mcp/tools', buttonCodes: Object.values(mcpButtonCodes.tool)},
    {path: '/agent/mcp/logs', buttonCodes: Object.values(mcpButtonCodes.log)},
    {path: '/rbac/users', buttonCodes: Object.values(rbacButtonCodes.user)},
    {path: '/rbac/roles', buttonCodes: Object.values(rbacButtonCodes.role)},
    {path: '/rbac/permissions', buttonCodes: Object.values(rbacButtonCodes.permission)},
    {path: '/rbac/menus', buttonCodes: Object.values(rbacButtonCodes.menu)},
    {path: '/rbac/buttons', buttonCodes: Object.values(rbacButtonCodes.button)},
    {path: '/rbac/apis', buttonCodes: Object.values(rbacButtonCodes.api)},
]

export function isCurrentPathAffectedByLostButtons(pathname: string, lostButtonCodes: string[]) {
    if (!lostButtonCodes.length) {
        return false
    }
    const routePermission = routeButtonPermissions.find(({path}) => pathname === path || pathname.startsWith(`${path}/`))
    if (!routePermission) {
        return false
    }
    const routeButtonCodes = new Set(routePermission.buttonCodes)
    return lostButtonCodes.some((buttonCode) => routeButtonCodes.has(buttonCode))
}
