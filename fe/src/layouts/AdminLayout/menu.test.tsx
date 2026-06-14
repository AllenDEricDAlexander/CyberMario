import {describe, expect, test} from 'vitest'
import type {MenuTreeResponse} from '../../modules/rbac/rbacTypes'
import {buildAuthorizedAdminMenuItems, canAccessAdminPath, firstAuthorizedMenuPath, flattenMenuKeys} from './menu'

const menuTree: MenuTreeResponse[] = [
    {
        permissionId: 10,
        permCode: 'menu:agent',
        permName: '首页控制台',
        routePath: '/dashboard',
        hidden: false,
        cacheable: true,
        sortNo: 0,
        children: [],
    },
    {
        permissionId: 1,
        permCode: 'menu:chat',
        permName: 'Agent Chat',
        routePath: '/chat',
        hidden: false,
        cacheable: true,
        sortNo: 1,
        children: [],
    },
    {
        permissionId: 2,
        permCode: 'menu:rbac:users',
        permName: '用户管理',
        routePath: '/rbac/users',
        hidden: false,
        cacheable: true,
        sortNo: 2,
        children: [],
    },
    {
        permissionId: 4,
        permCode: 'menu:rag',
        permName: 'RAG 管理',
        routePath: undefined,
        hidden: false,
        cacheable: true,
        sortNo: 4,
        children: [
            {
                permissionId: 5,
                permCode: 'menu:rag:arxiv-logs',
                permName: 'arXiv 日志',
                routePath: '/rag/arxiv-logs',
                hidden: false,
                cacheable: true,
                sortNo: 5,
                children: [],
            },
        ],
    },
    {
        permissionId: 3,
        permCode: 'menu:hidden',
        permName: '隐藏菜单',
        routePath: '/rbac/roles',
        hidden: true,
        cacheable: true,
        sortNo: 3,
        children: [],
    },
]

describe('admin menu authorization', () => {
    test('keeps only authorized non-hidden menu paths', () => {
        const items = buildAuthorizedAdminMenuItems(menuTree, false)

        expect(flattenMenuKeys(items)).toEqual(['/dashboard', '/chat', '/rbac/users'])
    })

    test('allows detail routes under an authorized menu path', () => {
        expect(canAccessAdminPath('/rbac/users', menuTree, false)).toBe(true)
        expect(canAccessAdminPath('/rbac/users/12', menuTree, false)).toBe(true)
        expect(canAccessAdminPath('/rbac/roles', menuTree, false)).toBe(false)
    })

    test('returns the first authorized path for default navigation', () => {
        expect(firstAuthorizedMenuPath(menuTree, false)).toBe('/dashboard')
    })

    test('hides arxiv logs unless user has super admin role', () => {
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, [])))
            .not.toContain('/rag/arxiv-logs')
        expect(canAccessAdminPath('/rag/arxiv-logs', menuTree, true, ['RAG_ADMIN'])).toBe(false)

        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN'])))
            .toContain('/rag/arxiv-logs')
        expect(canAccessAdminPath('/rag/arxiv-logs', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    })
})
