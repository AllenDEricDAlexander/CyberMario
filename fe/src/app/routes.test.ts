import {readFileSync} from 'node:fs'
import {describe, expect, test} from 'vitest'

const routesSource = readFileSync(new URL('./routes.tsx', import.meta.url), 'utf8')

describe('admin routes', () => {
    test('does not register the legacy global MCP tool policy page', () => {
        expect(routesSource).not.toContain("path: 'agent/mcp/tools'")
        expect(routesSource).not.toContain('McpToolListPage')
    })

    test('nutrition routes are registered under the protected admin layout', () => {
        [
            'nutrition/home',
            'nutrition/families',
            'nutrition/members',
            'nutrition/recipes',
            'nutrition/ai-menus',
            'nutrition/confirmations',
            'nutrition/meal-summary',
            'nutrition/shopping',
            'nutrition/budget',
            'nutrition/records',
            'nutrition/platform',
        ].forEach((path) => {
            expect(routesSource).toContain(`path: '${path}'`)
        })
        expect(routesSource).toContain("import('../modules/nutrition/pages/")
    })
})
