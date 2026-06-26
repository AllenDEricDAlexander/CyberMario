import {describe, expect, test} from 'vitest'
import type {McpServerResponse} from './mcpTypes'
import {
    canQueueMcpToolToggle,
    getMcpServerToolPolicyDrawerTitle,
    isMcpToolLoadCurrent,
} from './McpServerToolPolicyDrawer'

const server: McpServerResponse = {
    id: 3,
    serverCode: 'docs',
    serverName: 'Docs MCP',
    transportType: 'STREAMABLE_HTTP',
    baseUrl: 'https://mcp.example.com',
    endpoint: '/mcp',
    enabled: true,
    connectTimeoutMs: 5000,
    requestTimeoutMs: 30000,
    status: 'CONNECTED',
}

describe('McpServerToolPolicyDrawer', () => {
    test('builds the drawer title from the selected server name', () => {
        expect(getMcpServerToolPolicyDrawerTitle(server)).toBe('工具策略：Docs MCP')
    })

    test('keeps a stable fallback title when no server is selected', () => {
        expect(getMcpServerToolPolicyDrawerTitle(null)).toBe('工具策略')
        expect(getMcpServerToolPolicyDrawerTitle(undefined)).toBe('工具策略')
    })

    test('keeps tool loads scoped to the open selected server', () => {
        expect(isMcpToolLoadCurrent(server.id, true, server.id)).toBe(true)
        expect(isMcpToolLoadCurrent(server.id, false, server.id)).toBe(false)
        expect(isMcpToolLoadCurrent(server.id, true, null)).toBe(false)
        expect(isMcpToolLoadCurrent(server.id, true, undefined)).toBe(false)
        expect(isMcpToolLoadCurrent(server.id, true, 4)).toBe(false)
    })

    test('allows only one current-server toggle at a time', () => {
        expect(canQueueMcpToolToggle(server.id, null, true, server.id)).toBe(true)
        expect(canQueueMcpToolToggle(server.id, 9, true, server.id)).toBe(false)
        expect(canQueueMcpToolToggle(server.id, null, false, server.id)).toBe(false)
        expect(canQueueMcpToolToggle(server.id, null, true, 4)).toBe(false)
    })
})
