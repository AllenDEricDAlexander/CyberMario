import {describe, expect, test} from 'vitest'
import type {MenuTreeResponse} from '../../modules/rbac/rbacTypes'
import {
    buildAuthorizedAdminMenuItems,
    canAccessAdminPath,
    firstAuthorizedMenuPath,
    flattenMenuKeys,
    selectedAdminMenuKey,
} from './menu'

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
    {
        permissionId: 6,
        permCode: 'menu:agent:debug',
        permName: 'Agent 调试',
        routePath: '/agent/debug',
        hidden: false,
        cacheable: true,
        sortNo: 6,
        children: [],
    },
    {
        permissionId: 7,
        permCode: 'menu:agent:conversation-audit',
        permName: '对话审计',
        routePath: '/agent/conversation-audits',
        hidden: false,
        cacheable: true,
        sortNo: 7,
        children: [],
    },
    {
        permissionId: 8,
        permCode: 'menu:agent:run-audit',
        permName: '运行审计',
        routePath: '/agent/run-audits',
        hidden: false,
        cacheable: true,
        sortNo: 8,
        children: [],
    },
    {
        permissionId: 12,
        permCode: 'menu:agent:memory',
        permName: '记忆管理',
        routePath: '/agent/memory',
        hidden: false,
        cacheable: true,
        sortNo: 12,
        children: [],
    },
    {
        permissionId: 13,
        permCode: 'menu:agent:memory-archive',
        permName: '归档会话',
        routePath: '/agent/memory/archive',
        hidden: false,
        cacheable: true,
        sortNo: 13,
        children: [],
    },
    {
        permissionId: 9,
        permCode: 'menu:agent:mcp-servers',
        permName: 'MCP 服务配置',
        routePath: '/agent/mcp/servers',
        hidden: false,
        cacheable: true,
        sortNo: 9,
        children: [],
    },
    {
        permissionId: 10,
        permCode: 'menu:agent:mcp-tools',
        permName: 'MCP 工具策略',
        routePath: '/agent/mcp/tools',
        hidden: false,
        cacheable: true,
        sortNo: 10,
        children: [],
    },
    {
        permissionId: 11,
        permCode: 'menu:agent:mcp-logs',
        permName: 'MCP 调用日志',
        routePath: '/agent/mcp/logs',
        hidden: false,
        cacheable: true,
        sortNo: 11,
        children: [],
    },
]

const menuTreeWithoutMcpLogs = menuTree.filter((menu) => menu.routePath !== '/agent/mcp/logs')

describe('admin menu authorization', () => {
    test('keeps only authorized non-hidden menu paths', () => {
        const items = buildAuthorizedAdminMenuItems(menuTree, false)

        expect(flattenMenuKeys(items)).toEqual([
            '/dashboard',
            '/chat',
            '/agent/debug',
            '/agent/memory',
            '/agent/memory/archive',
            '/agent/mcp/servers',
            '/agent/mcp/logs',
            '/rbac/users',
        ])
    })

    test('allows detail routes under an authorized menu path', () => {
        expect(canAccessAdminPath('/rbac/users', menuTree, false)).toBe(true)
        expect(canAccessAdminPath('/rbac/users/12', menuTree, false)).toBe(true)
        expect(canAccessAdminPath('/rbac/roles', menuTree, false)).toBe(false)
    })

    test('returns the first authorized path for default navigation', () => {
        expect(firstAuthorizedMenuPath(menuTree, false)).toBe('/dashboard')
    })

    test('selects the most specific menu key for nested routes', () => {
        expect(selectedAdminMenuKey('/agent/memory/archive', [
            '/agent/memory',
            '/agent/memory/archive',
        ])).toBe('/agent/memory/archive')
        expect(selectedAdminMenuKey('/agent/memory/archive/session-1', [
            '/agent/memory',
            '/agent/memory/archive',
        ])).toBe('/agent/memory/archive')
    })

    test('hides arxiv logs unless user has super admin role', () => {
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, [])))
            .not.toContain('/rag/arxiv-logs')
        expect(canAccessAdminPath('/rag/arxiv-logs', menuTree, true, ['RAG_ADMIN'])).toBe(false)

        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN'])))
            .toContain('/rag/arxiv-logs')
        expect(canAccessAdminPath('/rag/arxiv-logs', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    })

    test('shows agent debug to chat users and conversation audits only to super admin', () => {
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['CHAT_BASIC'])))
            .toContain('/agent/debug')
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['CHAT_BASIC'])))
            .toContain('/agent/memory')
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['CHAT_BASIC'])))
            .toContain('/agent/memory/archive')
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['CHAT_BASIC'])))
            .not.toContain('/agent/conversation-audits')
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['CHAT_BASIC'])))
            .not.toContain('/agent/run-audits')
        expect(canAccessAdminPath('/agent/conversation-audits', menuTree, true, ['CHAT_BASIC'])).toBe(false)
        expect(canAccessAdminPath('/agent/run-audits', menuTree, true, ['CHAT_BASIC'])).toBe(false)

        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN'])))
            .toContain('/agent/conversation-audits')
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN'])))
            .toContain('/agent/run-audits')
        expect(canAccessAdminPath('/agent/conversation-audits', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
        expect(canAccessAdminPath('/agent/run-audits', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    })

    test('does not expose agent memory menu to rag users unless menu permission is present', () => {
        const ragOnlyTree = menuTree.filter((menu) => !String(menu.routePath).startsWith('/agent/memory'))

        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(ragOnlyTree, false, ['RAG_USER'])))
            .not.toContain('/agent/memory')
        expect(canAccessAdminPath('/agent/memory', ragOnlyTree, false, ['RAG_USER'])).toBe(false)
    })

    test('shows MCP logs to non-super-admin users with MCP log menu permission', () => {
        const mcpAdminKeys = flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, false, ['AGENT_MCP_ADMIN']))

        expect(mcpAdminKeys).toContain('/agent/mcp/servers')
        expect(mcpAdminKeys).toContain('/agent/mcp/logs')
        expect(canAccessAdminPath('/agent/mcp/servers', menuTree, false, ['AGENT_MCP_ADMIN'])).toBe(true)
        expect(canAccessAdminPath('/agent/mcp/logs', menuTree, false, ['AGENT_MCP_ADMIN'])).toBe(true)
    })

    test('hides MCP logs from non-super-admin users without MCP log menu permission', () => {
        const mcpUserKeys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
            menuTreeWithoutMcpLogs,
            false,
            ['AGENT_MCP_USER'],
        ))

        expect(mcpUserKeys).toContain('/agent/mcp/servers')
        expect(mcpUserKeys).not.toContain('/agent/mcp/logs')
        expect(canAccessAdminPath('/agent/mcp/servers', menuTreeWithoutMcpLogs, false, ['AGENT_MCP_USER'])).toBe(true)
        expect(canAccessAdminPath('/agent/mcp/logs', menuTreeWithoutMcpLogs, false, ['AGENT_MCP_USER'])).toBe(false)

        const superAdminKeys = flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN']))
        expect(superAdminKeys).toContain('/agent/mcp/logs')
        expect(canAccessAdminPath('/agent/mcp/logs', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    })

    test('shows clocktower pages when clocktower menu permissions are present', () => {
        const clocktowerMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 20,
                permCode: 'menu:clocktower:boards',
                permName: '钟楼配板',
                routePath: '/clocktower/boards',
                hidden: false,
                cacheable: true,
                sortNo: 20,
                children: [],
            },
            {
                permissionId: 21,
                permCode: 'menu:clocktower:rooms',
                permName: '钟楼房间',
                routePath: '/clocktower/rooms',
                hidden: false,
                cacheable: true,
                sortNo: 21,
                children: [],
            },
            {
                permissionId: 22,
                permCode: 'menu:clocktower:rules',
                permName: '钟楼规则',
                routePath: '/clocktower/rules',
                hidden: false,
                cacheable: true,
                sortNo: 22,
                children: [],
            },
            {
                permissionId: 23,
                permCode: 'menu:clocktower:replays',
                permName: '钟楼回放',
                routePath: '/clocktower/replays',
                hidden: false,
                cacheable: true,
                sortNo: 23,
                children: [],
            },
        ]

        const clocktowerKeys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
            clocktowerMenuTree,
            false,
            ['CLOCKTOWER_STORYTELLER'],
        ))

        expect(clocktowerKeys).toContain('/clocktower/boards')
        expect(clocktowerKeys).toContain('/clocktower/rooms')
        expect(clocktowerKeys).toContain('/clocktower/rules')
        expect(clocktowerKeys).toContain('/clocktower/replays')
        expect(clocktowerKeys).not.toContain('/clocktower/admin/audit')
        expect(canAccessAdminPath('/clocktower/rule', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
            .toBe(true)
        expect(canAccessAdminPath('/clocktower/replay', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
            .toBe(true)
        expect(canAccessAdminPath('/clocktower/rooms/7/play', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
            .toBe(true)
        expect(canAccessAdminPath('/clocktower/games/42/replay', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
            .toBe(true)
        expect(canAccessAdminPath('/clocktower/admin/audit', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
            .toBe(false)
    })

    test('shows clocktower audit only when menu permission is present or bypass applies', () => {
        const clocktowerAuditMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 24,
                permCode: 'menu:admin:clocktower:audit',
                permName: '钟楼审计',
                routePath: '/clocktower/admin/audit',
                hidden: false,
                cacheable: true,
                sortNo: 24,
                children: [],
            },
        ]

        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(clocktowerAuditMenuTree, false, ['CLOCKTOWER_ADMIN'])))
            .toContain('/clocktower/admin/audit')
        expect(canAccessAdminPath('/clocktower/admin/audit', clocktowerAuditMenuTree, false, ['CLOCKTOWER_ADMIN']))
            .toBe(true)
        expect(flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN'])))
            .toContain('/clocktower/admin/audit')
        expect(canAccessAdminPath('/clocktower/admin/audit', menuTree, true, ['SUPER_ADMIN']))
            .toBe(true)
    })

    test('authorizes every family nutrition path through the family home menu', () => {
        const familyMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 30,
                permCode: 'menu:nutrition:families',
                permName: '家庭营养',
                routePath: '/nutrition/home',
                hidden: false,
                cacheable: true,
                sortNo: 30,
                children: [],
            },
        ]
        const familyPaths = [
            '/nutrition/home',
            '/nutrition/families',
            '/nutrition/members',
            '/nutrition/recipes',
            '/nutrition/ai-menus',
            '/nutrition/confirmations',
            '/nutrition/meal-summary',
            '/nutrition/shopping',
            '/nutrition/budget',
            '/nutrition/records',
        ]

        const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(familyMenuTree, false, ['NUTRITION_USER']))

        expect(keys).toContain('/nutrition/home')
        expect(keys).not.toContain('/nutrition/platform')
        familyPaths.forEach((path) => {
            expect(canAccessAdminPath(path, familyMenuTree, false, ['NUTRITION_USER'])).toBe(true)
        })
        expect(canAccessAdminPath('/nutrition/recipes/7', familyMenuTree, false, ['NUTRITION_USER']))
            .toBe(true)
        expect(canAccessAdminPath('/nutrition/platform', familyMenuTree, false, ['NUTRITION_USER']))
            .toBe(false)
        expect(canAccessAdminPath('/nutrition/unknown', familyMenuTree, false, ['NUTRITION_USER']))
            .toBe(false)
        expect(selectedAdminMenuKey('/nutrition/records', keys)).toBe('/nutrition/home')
    })

    test('keeps nutrition platform separate from the family workspace permission', () => {
        const platformMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 31,
                permCode: 'menu:nutrition:platform',
                permName: '营养平台',
                routePath: '/nutrition/platform',
                hidden: false,
                cacheable: true,
                sortNo: 31,
                children: [],
            },
        ]

        const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
            platformMenuTree,
            false,
            ['NUTRITION_PLATFORM_ADMIN'],
        ))

        expect(keys).toContain('/nutrition/platform')
        expect(keys).not.toContain('/nutrition/home')
        expect(canAccessAdminPath('/nutrition/platform', platformMenuTree, false, ['NUTRITION_PLATFORM_ADMIN']))
            .toBe(true)
        expect(canAccessAdminPath('/nutrition/members', platformMenuTree, false, ['NUTRITION_PLATFORM_ADMIN']))
            .toBe(false)
    })

    test('keeps both nutrition entries in the super admin bypass menu', () => {
        const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN']))

        expect(keys).toContain('/nutrition/home')
        expect(keys).toContain('/nutrition/platform')
        expect(canAccessAdminPath('/nutrition/budget', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
        expect(canAccessAdminPath('/nutrition/platform', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    })

    test('authorizes every Investment workspace child through the single workspace menu entry', () => {
        const workspaceMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 40,
                permCode: 'menu:investment:workspace',
                permName: '投资工作台',
                routePath: '/investment/overview',
                hidden: false,
                cacheable: true,
                sortNo: 40,
                children: [],
            },
        ]
        const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
            workspaceMenuTree, false, ['INVESTMENT_USER'],
        ))

        expect(keys).toContain('/investment/overview')
        expect(keys).not.toContain('/investment/platform')
        for (const path of [
            '/investment/overview',
            '/investment/market',
            '/investment/instruments/11',
            '/investment/research',
            '/investment/quant',
            '/investment/portfolio',
            '/investment/agent',
        ]) {
            expect(canAccessAdminPath(path, workspaceMenuTree, false, ['INVESTMENT_USER'])).toBe(true)
            expect(selectedAdminMenuKey(path, keys)).toBe('/investment/overview')
        }
        expect(canAccessAdminPath('/investment/platform', workspaceMenuTree, false, ['INVESTMENT_USER']))
            .toBe(false)
        expect(canAccessAdminPath('/investment/unknown', workspaceMenuTree, false, ['INVESTMENT_USER']))
            .toBe(false)
    })

    test('keeps Investment platform independent and exposes only two sidebar destinations', () => {
        const platformMenuTree: MenuTreeResponse[] = [
            ...menuTree,
            {
                permissionId: 41,
                permCode: 'menu:investment:platform',
                permName: '投资平台',
                routePath: '/investment/platform',
                hidden: false,
                cacheable: true,
                sortNo: 41,
                children: [],
            },
        ]
        const platformKeys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
            platformMenuTree, false, ['INVESTMENT_PLATFORM_ADMIN'],
        ))

        expect(platformKeys).toContain('/investment/platform')
        expect(platformKeys).not.toContain('/investment/overview')
        expect(canAccessAdminPath(
            '/investment/platform', platformMenuTree, false, ['INVESTMENT_PLATFORM_ADMIN'],
        )).toBe(true)
        expect(canAccessAdminPath(
            '/investment/market', platformMenuTree, false, ['INVESTMENT_PLATFORM_ADMIN'],
        )).toBe(false)

        const bypassInvestmentKeys = flattenMenuKeys(
            buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN']),
        ).filter((key) => key.startsWith('/investment/'))
        expect(bypassInvestmentKeys).toEqual(['/investment/overview', '/investment/platform'])
    })
})
