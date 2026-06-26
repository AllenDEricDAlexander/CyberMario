import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import type {McpToolResponse} from './mcpTypes'
import {
    canOpenServerToolPolicy,
    createMcpToolColumns,
    formatMcpToolInputSchema,
    isMcpToolRowExpandable,
    mcpRiskLevelColor,
    mcpRuntimeStatusColor,
    renderMcpToolExpandedRow,
} from './mcpToolView'

const baseTool: McpToolResponse = {
    id: 9,
    serverId: 3,
    serverCode: 'docs',
    toolName: 'search',
    toolKey: 'docs_search',
    displayName: 'Docs Search',
    description: 'Search documentation',
    inputSchemaJson: '{"type":"object","properties":{"query":{"type":"string"}}}',
    enabled: true,
    riskLevel: 'LOW',
    readonly: true,
    requireConfirm: false,
    runtimeStatus: 'AVAILABLE',
    lastDiscoveredAt: '2026-06-25T08:28:06Z',
}

describe('mcpToolView', () => {
    test('formats MCP tool input schemas for display', () => {
        expect(formatMcpToolInputSchema()).toBe('-')
        expect(formatMcpToolInputSchema('')).toBe('-')
        expect(formatMcpToolInputSchema('{"type":"object"}')).toBe('{\n  "type": "object"\n}')
        expect(formatMcpToolInputSchema('{invalid')).toBe('{invalid')
    })

    test('maps MCP tool risk and runtime states to Ant Design tag colors', () => {
        expect(mcpRiskLevelColor('LOW')).toBe('success')
        expect(mcpRiskLevelColor('MEDIUM')).toBe('warning')
        expect(mcpRiskLevelColor('HIGH')).toBe('error')

        expect(mcpRuntimeStatusColor('AVAILABLE')).toBe('success')
        expect(mcpRuntimeStatusColor('DISABLED')).toBe('default')
        expect(mcpRuntimeStatusColor('SERVER_DISABLED')).toBe('default')
        expect(mcpRuntimeStatusColor('POLICY_BLOCKED')).toBe('warning')
        expect(mcpRuntimeStatusColor('SERVER_FAILED')).toBe('error')
    })

    test('allows the server-page tool policy entry when tool edit or toggle permission exists', () => {
        expect(canOpenServerToolPolicy(false, false)).toBe(false)
        expect(canOpenServerToolPolicy(true, false)).toBe(true)
        expect(canOpenServerToolPolicy(false, true)).toBe(true)
        expect(canOpenServerToolPolicy(true, true)).toBe(true)
    })

    test('builds tool table columns with the server column by default', () => {
        const columns = createMcpToolColumns({
            canEditPolicy: true,
            canToggle: true,
            switchingId: null,
            onOpenPolicy: vi.fn(),
            onToggleTool: vi.fn(),
        })

        expect(columns.map((column) => column.title)).toEqual([
            'Tool Key',
            '服务',
            '工具名',
            '风险',
            '只读',
            '确认',
            '运行状态',
            '启用',
            '最近发现',
            '操作',
        ])
    })

    test('omits the server column for service-scoped drawers', () => {
        const columns = createMcpToolColumns({
            canEditPolicy: true,
            canToggle: true,
            switchingId: null,
            includeServerColumn: false,
            onOpenPolicy: vi.fn(),
            onToggleTool: vi.fn(),
        })

        expect(columns.map((column) => column.title)).toEqual([
            'Tool Key',
            '工具名',
            '风险',
            '只读',
            '确认',
            '运行状态',
            '启用',
            '最近发现',
            '操作',
        ])
    })

    test('renders no policy action when edit permission is missing', () => {
        const columns = createMcpToolColumns({
            canEditPolicy: false,
            canToggle: true,
            switchingId: null,
            onOpenPolicy: vi.fn(),
            onToggleTool: vi.fn(),
        })
        const actionColumn = columns.find((column) => column.title === '操作')

        expect(actionColumn?.render?.(undefined, baseTool, 0)).toBe('-')
    })

    test('renders expanded row content with description and formatted schema', () => {
        const markup = renderToStaticMarkup(renderMcpToolExpandedRow(baseTool))

        expect(markup).toContain('Search documentation')
        expect(markup).toContain('properties')
        expect(markup).toContain('query')
    })

    test('only expands rows that have descriptions or schemas', () => {
        expect(isMcpToolRowExpandable(baseTool)).toBe(true)
        expect(isMcpToolRowExpandable({
            ...baseTool,
            description: undefined,
            inputSchemaJson: undefined,
        })).toBe(false)
    })
})
