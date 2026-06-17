import {
    ApiOutlined,
    AppstoreOutlined,
    AuditOutlined,
    BranchesOutlined,
    CloudServerOutlined,
    CommentOutlined,
    ControlOutlined,
    DashboardOutlined,
    DatabaseOutlined,
    ExperimentOutlined,
    FileSearchOutlined,
    FileTextOutlined,
    InboxOutlined,
    MenuOutlined,
    MessageOutlined,
    NodeIndexOutlined,
    PlayCircleOutlined,
    ProfileOutlined,
    ReadOutlined,
    SafetyCertificateOutlined,
    SearchOutlined,
    SettingOutlined,
    SyncOutlined,
    TeamOutlined,
    ToolOutlined,
} from '@ant-design/icons'
import type {MenuProps} from 'antd'
import type {MenuTreeResponse} from '../../modules/rbac/rbacTypes'

export type AdminMenuItem = Required<MenuProps>['items'][number]

const menuPathByKey: Record<string, string> = {
    '/dashboard': '/dashboard',
    '/chat': '/chat',
    '/agent/debug': '/agent/debug',
    '/agent/conversation-audits': '/agent/conversation-audits',
    '/agent/run-audits': '/agent/run-audits',
    '/agent/memory': '/agent/memory',
    '/agent/memory/archive': '/agent/memory/archive',
    '/agent/mcp/servers': '/agent/mcp/servers',
    '/agent/mcp/tools': '/agent/mcp/tools',
    '/agent/mcp/logs': '/agent/mcp/logs',
    '/rag/chat': '/rag/chat',
    '/rag/knowledge-bases': '/rag/knowledge-bases',
    '/rag/documents': '/rag/documents',
    '/rag/ingestion-jobs': '/rag/ingestion-jobs',
    '/rag/retrieval-lab': '/rag/retrieval-lab',
    '/rag/arxiv-logs': '/rag/arxiv-logs',
    '/rag/settings': '/rag/settings',
    '/clocktower/boards': '/clocktower/boards',
    '/clocktower/rooms': '/clocktower/rooms',
    '/clocktower/rules': '/clocktower/rules',
    '/clocktower/replays': '/clocktower/replays',
    '/rbac/users': '/rbac/users',
    '/rbac/roles': '/rbac/roles',
    '/rbac/permissions': '/rbac/permissions',
    '/rbac/menus': '/rbac/menus',
    '/rbac/buttons': '/rbac/buttons',
    '/rbac/apis': '/rbac/apis',
}

const compatibilityMenuPathAliases: Record<string, string> = {
    '/clocktower/rule': '/clocktower/rules',
    '/clocktower/replay': '/clocktower/replays',
}

export const adminMenuItems: AdminMenuItem[] = [
    {
        key: '/dashboard',
        icon: <DashboardOutlined/>,
        label: '首页控制台',
    },
    {
        key: '/chat',
        icon: <CommentOutlined/>,
        label: 'Agent Chat',
    },
    {
        key: 'agent',
        icon: <ExperimentOutlined/>,
        label: 'Agent 管理',
        children: [
            {
                key: '/agent/debug',
                icon: <ExperimentOutlined/>,
                label: 'Agent 调试',
            },
            {
                key: '/agent/conversation-audits',
                icon: <AuditOutlined/>,
                label: '对话审计',
            },
            {
                key: '/agent/run-audits',
                icon: <NodeIndexOutlined/>,
                label: '运行审计',
            },
            {
                key: '/agent/memory',
                icon: <ProfileOutlined/>,
                label: '记忆管理',
            },
            {
                key: '/agent/memory/archive',
                icon: <InboxOutlined/>,
                label: '归档会话',
            },
            {
                key: '/agent/mcp/servers',
                icon: <CloudServerOutlined/>,
                label: 'MCP 服务配置',
            },
            {
                key: '/agent/mcp/tools',
                icon: <ToolOutlined/>,
                label: 'MCP 工具策略',
            },
            {
                key: '/agent/mcp/logs',
                icon: <AuditOutlined/>,
                label: 'MCP 调用日志',
            },
        ],
    },
    {
        key: 'rag',
        icon: <DatabaseOutlined/>,
        label: 'RAG 管理',
        children: [
            {
                key: '/rag/chat',
                icon: <MessageOutlined/>,
                label: 'RAG 问答',
            },
            {
                key: '/rag/knowledge-bases',
                icon: <DatabaseOutlined/>,
                label: '知识库管理',
            },
            {
                key: '/rag/documents',
                icon: <FileTextOutlined/>,
                label: '文档管理',
            },
            {
                key: '/rag/ingestion-jobs',
                icon: <SyncOutlined/>,
                label: '入库任务',
            },
            {
                key: '/rag/retrieval-lab',
                icon: <SearchOutlined/>,
                label: '检索调试',
            },
            {
                key: '/rag/arxiv-logs',
                icon: <FileSearchOutlined/>,
                label: 'arXiv 日志',
            },
            {
                key: '/rag/settings',
                icon: <SettingOutlined/>,
                label: 'RAG 设置',
            },
        ],
    },
    {
        key: 'clocktower',
        icon: <PlayCircleOutlined/>,
        label: '钟楼',
        children: [
            {
                key: '/clocktower/boards',
                icon: <ControlOutlined/>,
                label: '钟楼配板',
            },
            {
                key: '/clocktower/rooms',
                icon: <TeamOutlined/>,
                label: '钟楼房间',
            },
            {
                key: '/clocktower/rules',
                icon: <ReadOutlined/>,
                label: '钟楼规则',
            },
            {
                key: '/clocktower/replays',
                icon: <FileSearchOutlined/>,
                label: '钟楼回放',
            },
        ],
    },
    {
        key: 'rbac',
        icon: <SafetyCertificateOutlined/>,
        label: 'RBAC 管理',
        children: [
            {
                key: '/rbac/users',
                icon: <TeamOutlined/>,
                label: '用户管理',
            },
            {
                key: '/rbac/roles',
                icon: <BranchesOutlined/>,
                label: '角色管理',
            },
            {
                key: '/rbac/permissions',
                icon: <ControlOutlined/>,
                label: '权限管理',
            },
            {
                key: '/rbac/menus',
                icon: <MenuOutlined/>,
                label: '菜单管理',
            },
            {
                key: '/rbac/buttons',
                icon: <AppstoreOutlined/>,
                label: '按钮管理',
            },
            {
                key: '/rbac/apis',
                icon: <ApiOutlined/>,
                label: 'API 权限',
            },
        ],
    },
]

export function findMenuPath(key: string) {
    return menuPathByKey[key]
}

export function buildAuthorizedAdminMenuItems(menus: MenuTreeResponse[], canBypass: boolean, roleCodes: string[] = []) {
    const superAdminOnlyPaths = superAdminMenuPathSet(roleCodes)
    if (canBypass) {
        return filterMenuItems(adminMenuItems, menuPathSet(menus), superAdminOnlyPaths, true)
    }
    const allowedPaths = menuPathSet(menus)
    return filterMenuItems(adminMenuItems, allowedPaths, superAdminOnlyPaths, false)
}

export function firstAuthorizedMenuPath(menus: MenuTreeResponse[], canBypass: boolean, roleCodes: string[] = []) {
    return flattenMenuKeys(buildAuthorizedAdminMenuItems(menus, canBypass, roleCodes))[0]
}

export function flattenMenuKeys(items: AdminMenuItem[]) {
    const keys: string[] = []
    for (const item of items) {
        if (!item) continue
        if ('children' in item && item.children) {
            keys.push(...flattenMenuKeys(item.children))
            continue
        }
        const key = String(item.key)
        if (findMenuPath(key)) {
            keys.push(key)
        }
    }
    return keys
}

export function selectedAdminMenuKey(pathname: string, menuKeys: string[]) {
    return menuKeys
        .filter((key) => pathname === key || pathname.startsWith(`${key}/`))
        .sort((left, right) => right.length - left.length)[0]
}

export function canAccessAdminPath(pathname: string, menus: MenuTreeResponse[], canBypass: boolean, roleCodes: string[] = []) {
    const canonicalPathname = canonicalAdminPath(pathname)
    if (isSuperAdminOnlyPath(canonicalPathname) && !isSuperAdmin(roleCodes)) {
        return false
    }
    if (canBypass || canonicalPathname === '/' || canonicalPathname === '/account/settings') {
        return true
    }
    return flattenMenuKeys(buildAuthorizedAdminMenuItems(menus, canBypass, roleCodes))
        .some((key) => canonicalPathname === key || canonicalPathname.startsWith(`${key}/`))
}

function canonicalAdminPath(pathname: string) {
    return compatibilityMenuPathAliases[pathname] ?? pathname
}

function filterMenuItems(
    items: AdminMenuItem[],
    allowedPaths: Set<string>,
    superAdminOnlyPaths: Set<string>,
    includeAllPaths: boolean,
): AdminMenuItem[] {
    return items.flatMap((item) => {
        if (!item) {
            return []
        }
        if ('children' in item && item.children) {
            const children = filterMenuItems(item.children, allowedPaths, superAdminOnlyPaths, includeAllPaths)
            return children.length ? [{...item, children}] : []
        }
        const key = String(item.key)
        if (superAdminOnlyPaths.has(key)) {
            return isSuperAdminOnlyAllowed(superAdminOnlyPaths, key) ? [item] : []
        }
        return includeAllPaths || allowedPaths.has(key) ? [item] : []
    })
}

function menuPathSet(menus: MenuTreeResponse[]) {
    const paths = new Set<string>()
    collectMenuPaths(menus, paths)
    return paths
}

function collectMenuPaths(menus: MenuTreeResponse[], paths: Set<string>) {
    for (const menu of menus) {
        if (!menu.hidden && menu.routePath) {
            paths.add(menu.routePath.startsWith('/') ? menu.routePath : `/${menu.routePath}`)
        }
        collectMenuPaths(menu.children ?? [], paths)
    }
}

function superAdminMenuPathSet(roleCodes: string[]) {
    return isSuperAdmin(roleCodes)
        ? new Set<string>()
        : new Set(['/rag/arxiv-logs', '/agent/conversation-audits', '/agent/run-audits'])
}

function isSuperAdmin(roleCodes: string[]) {
    return roleCodes.includes('SUPER_ADMIN')
}

function isSuperAdminOnlyAllowed(superAdminOnlyPaths: Set<string>, key: string) {
    return !superAdminOnlyPaths.has(key)
}

function isSuperAdminOnlyPath(pathname: string) {
    return pathname === '/rag/arxiv-logs'
        || pathname.startsWith('/rag/arxiv-logs/')
        || pathname === '/agent/conversation-audits'
        || pathname.startsWith('/agent/conversation-audits/')
        || pathname === '/agent/run-audits'
        || pathname.startsWith('/agent/run-audits/')
}
