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
