import {readFileSync} from 'node:fs'
import {describe, expect, test} from 'vitest'

const routesSource = readFileSync(new URL('./routes.tsx', import.meta.url), 'utf8')

describe('admin routes', () => {
    test('does not register the legacy global MCP tool policy page', () => {
        expect(routesSource).not.toContain("path: 'agent/mcp/tools'")
        expect(routesSource).not.toContain('McpToolListPage')
    })
})
