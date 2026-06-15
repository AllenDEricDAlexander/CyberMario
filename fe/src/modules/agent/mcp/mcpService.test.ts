import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createMcpServer,
    deleteMcpServer,
    disableMcpServer,
    disableMcpTool,
    discoverMcpTools,
    enableMcpServer,
    enableMcpTool,
    getMcpServers,
    getMcpToolCallLogs,
    getMcpTools,
    testMcpServer,
    updateMcpServer,
    updateMcpToolPolicy,
} from './mcpService'

vi.mock('../../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('mcpService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('calls server management endpoints', async () => {
        const {requestJson} = await import('../../../services/request')
        const createRequest = {
            serverCode: 'docs',
            serverName: 'Docs MCP',
            transportType: 'STREAMABLE_HTTP' as const,
            baseUrl: 'https://mcp.example.com',
            endpoint: '/mcp',
        }
        const updateRequest = {
            serverName: 'Docs MCP',
            transportType: 'SSE' as const,
            baseUrl: 'https://mcp.example.com',
            endpoint: '/sse',
        }

        void getMcpServers()
        void createMcpServer(createRequest)
        void updateMcpServer(12, updateRequest)
        void deleteMcpServer(12)
        void enableMcpServer(12)
        void disableMcpServer(12)
        void testMcpServer(12)
        void discoverMcpTools(12)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/admin/agent/mcp/servers')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/agent/mcp/servers', {
            method: 'POST',
            body: createRequest,
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/admin/agent/mcp/servers/12', {
            method: 'PUT',
            body: updateRequest,
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/admin/agent/mcp/servers/12', {method: 'DELETE'})
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/admin/agent/mcp/servers/12/enable', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/admin/agent/mcp/servers/12/disable', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(7, '/api/admin/agent/mcp/servers/12/test', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(8, '/api/admin/agent/mcp/servers/12/discover-tools', {method: 'POST'})
    })

    test('builds tool and log endpoint requests', async () => {
        const {requestJson} = await import('../../../services/request')
        const policyRequest = {
            riskLevel: 'HIGH' as const,
            readonly: false,
            requireConfirm: true,
        }

        void getMcpTools()
        void getMcpTools(12)
        void updateMcpToolPolicy(30, policyRequest)
        void enableMcpTool(30)
        void disableMcpTool(30)
        void getMcpToolCallLogs({page: 3, size: 40})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/admin/agent/mcp/tools')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/agent/mcp/tools?serverId=12')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/admin/agent/mcp/tools/30/policy', {
            method: 'PUT',
            body: policyRequest,
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/admin/agent/mcp/tools/30/enable', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/admin/agent/mcp/tools/30/disable', {method: 'POST'})
        expect(requestJson).toHaveBeenNthCalledWith(6, '/api/admin/agent/mcp/tool-calls?page=3&size=40')
    })
})
