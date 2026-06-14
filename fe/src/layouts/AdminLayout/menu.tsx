import {
    ApiOutlined,
    AppstoreOutlined,
    BranchesOutlined,
    CommentOutlined,
    ControlOutlined,
    DashboardOutlined,
    DatabaseOutlined,
    FileTextOutlined,
    MenuOutlined,
    MessageOutlined,
    SafetyCertificateOutlined,
    SearchOutlined,
    SettingOutlined,
    SyncOutlined,
    TeamOutlined,
} from '@ant-design/icons'
import type {MenuProps} from 'antd'
import type {MenuTreeResponse} from '../../modules/rbac/rbacTypes'

export type AdminMenuItem = Required<MenuProps>['items'][number]

const menuPathByKey: Record<string, string> = {
    '/dashboard': '/dashboard',
    '/chat': '/chat',
    '/rag/chat': '/rag/chat',
    '/rag/knowledge-bases': '/rag/knowledge-bases',
    '/rag/documents': '/rag/documents',
    '/rag/ingestion-jobs': '/rag/ingestion-jobs',
    '/rag/retrieval-lab': '/rag/retrieval-lab',
    '/rag/settings': '/rag/settings',
    '/rbac/users': '/rbac/users',
    '/rbac/roles': '/rbac/roles',
    '/rbac/permissions': '/rbac/permissions',
    '/rbac/menus': '/rbac/menus',
    '/rbac/buttons': '/rbac/buttons',
    '/rbac/apis': '/rbac/apis',
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
                key: '/rag/settings',
                icon: <SettingOutlined/>,
                label: 'RAG 设置',
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

export function buildAuthorizedAdminMenuItems(menus: MenuTreeResponse[], canBypass: boolean) {
    if (canBypass) {
        return adminMenuItems
    }
    const allowedPaths = menuPathSet(menus)
    return filterMenuItems(adminMenuItems, allowedPaths)
}

export function firstAuthorizedMenuPath(menus: MenuTreeResponse[], canBypass: boolean) {
    return flattenMenuKeys(buildAuthorizedAdminMenuItems(menus, canBypass))[0]
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

export function canAccessAdminPath(pathname: string, menus: MenuTreeResponse[], canBypass: boolean) {
    if (canBypass || pathname === '/' || pathname === '/account/settings') {
        return true
    }
    return flattenMenuKeys(buildAuthorizedAdminMenuItems(menus, canBypass))
        .some((key) => pathname === key || pathname.startsWith(`${key}/`))
}

function filterMenuItems(items: AdminMenuItem[], allowedPaths: Set<string>): AdminMenuItem[] {
    return items.flatMap((item) => {
        if (!item) {
            return []
        }
        if ('children' in item && item.children) {
            const children = filterMenuItems(item.children, allowedPaths)
            return children.length ? [{...item, children}] : []
        }
        const key = String(item.key)
        return allowedPaths.has(key) ? [item] : []
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
