import {
    ApiOutlined,
    AppstoreOutlined,
    BranchesOutlined,
    CommentOutlined,
    ControlOutlined,
    MenuOutlined,
    SafetyCertificateOutlined,
    TeamOutlined,
} from '@ant-design/icons'
import type {MenuProps} from 'antd'
import type {MenuTreeResponse} from '../../modules/rbac/rbacTypes'

export type AdminMenuItem = Required<MenuProps>['items'][number]

const menuPathByKey: Record<string, string> = {
    '/chat': '/chat',
    '/rbac/users': '/rbac/users',
    '/rbac/roles': '/rbac/roles',
    '/rbac/permissions': '/rbac/permissions',
    '/rbac/menus': '/rbac/menus',
    '/rbac/buttons': '/rbac/buttons',
    '/rbac/apis': '/rbac/apis',
}

export const adminMenuItems: AdminMenuItem[] = [
    {
        key: '/chat',
        icon: <CommentOutlined/>,
        label: 'Agent Chat',
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
            keys.push(...flattenMenuKeys(item.children as AdminMenuItem[]))
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
    if (canBypass || pathname === '/') {
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
            const children = filterMenuItems(item.children as AdminMenuItem[], allowedPaths)
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
