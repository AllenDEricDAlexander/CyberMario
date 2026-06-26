# MCP Tool Policy Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-server "工具策略" drawer on the MCP 服务配置 page so admins can view and maintain tools for one MCP
server without leaving the service list.

**Architecture:** Keep this frontend-only. Extract reusable MCP tool table rendering helpers from the existing global
MCP 工具策略 page, create a focused `McpServerToolPolicyDrawer`, then wire a new row action into `McpServerListPage`.
Reuse existing MCP service APIs, RBAC button codes, Ant Design table/drawer patterns, and `McpToolPolicyDrawer`.

**Tech Stack:** React 19, TypeScript 6, Ant Design 6.4.4, Vitest, Bun, existing CyberMario frontend services.

---

## Scope Check

The approved spec is one frontend workflow enhancement. It does not require backend, database, Flyway, MCP runtime,
route, menu, or RBAC resource changes. Do not start the app or open a browser during implementation; the user will run
runtime testing.

Because this changes Ant Design component code, run the Ant Design CLI checks before and after implementation:

```bash
cd fe
bunx --bun @ant-design/cli info Button --format json
bunx --bun @ant-design/cli info Drawer --format json
bunx --bun @ant-design/cli info Table --format json
bunx --bun @ant-design/cli info Switch --format json
```

Expected: each command exits 0 and prints JSON metadata for the component. If the CLI fails because the package is
unavailable, record the failure and continue with documented Ant Design APIs already used in this repo.

## File Structure

- Create `fe/src/modules/agent/mcp/mcpToolView.tsx`
    - Owns reusable MCP tool table columns, expanded-row rendering, colors, JSON formatting, and the server-page
      tool-policy entrance predicate.
- Create `fe/src/modules/agent/mcp/mcpToolView.test.tsx`
    - Owns focused Vitest coverage for helper behavior without requiring browser DOM tooling.
- Modify `fe/src/modules/agent/mcp/McpToolListPage.tsx`
    - Reuses `mcpToolView.tsx` instead of keeping duplicate local table helpers.
- Create `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.tsx`
    - Owns one service-scoped tool policy drawer, loading by `server.id`, toggling tools, and reusing
      `McpToolPolicyDrawer`.
- Create `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx`
    - Owns pure title formatting coverage for the new drawer.
- Modify `fe/src/modules/agent/mcp/McpServerListPage.tsx`
    - Adds the service-row "工具策略" action and renders `McpServerToolPolicyDrawer`.
- Modify `fe/src/layouts/AdminLayout/permissionImpact.ts`
    - Treats MCP tool-policy button permissions as affecting `/agent/mcp/servers`, because that page now renders a
      tool-policy entry.
- Modify `fe/src/layouts/AdminLayout/permissionImpact.test.ts`
    - Updates regression coverage for the new permission dependency.

## Task 1: Extract MCP Tool View Helpers

**Files:**

- Create: `fe/src/modules/agent/mcp/mcpToolView.test.tsx`
- Create: `fe/src/modules/agent/mcp/mcpToolView.tsx`
- Modify: `fe/src/modules/agent/mcp/McpToolListPage.tsx`

- [ ] **Step 1: Write failing helper tests**

Create `fe/src/modules/agent/mcp/mcpToolView.test.tsx` with:

```tsx
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
```

- [ ] **Step 2: Run the helper tests and verify failure**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/mcpToolView.test.tsx
```

Expected: FAIL because `./mcpToolView` does not exist.

- [ ] **Step 3: Implement `mcpToolView.tsx`**

Create `fe/src/modules/agent/mcp/mcpToolView.tsx` with:

```tsx
import {EditOutlined} from '@ant-design/icons'
import {Button, Space, Switch, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {DateTimeText} from '../../../components/DateTimeText'
import type {McpToolResponse, McpToolRiskLevel, McpToolRuntimeStatus} from './mcpTypes'

type McpToolColumnsOptions = {
    canEditPolicy: boolean
    canToggle: boolean
    switchingId: number | null
    includeServerColumn?: boolean
    onOpenPolicy: (tool: McpToolResponse) => void
    onToggleTool: (tool: McpToolResponse, checked: boolean) => void
}

export function canOpenServerToolPolicy(canEditPolicy: boolean, canToggle: boolean) {
    return canEditPolicy || canToggle
}

export function formatMcpToolInputSchema(value?: string) {
    if (!value) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
        return value
    }
}

export function mcpRiskLevelColor(value: McpToolRiskLevel) {
    if (value === 'HIGH') {
        return 'error'
    }
    if (value === 'MEDIUM') {
        return 'warning'
    }
    return 'success'
}

export function mcpRuntimeStatusColor(value: McpToolRuntimeStatus) {
    if (value === 'AVAILABLE') {
        return 'success'
    }
    if (value === 'DISABLED' || value === 'SERVER_DISABLED') {
        return 'default'
    }
    if (value === 'POLICY_BLOCKED') {
        return 'warning'
    }
    return 'error'
}

export function renderMcpToolExpandedRow(record: McpToolResponse) {
    return (
        <Space direction="vertical" size={12} style={{width: '100%'}}>
            <Typography.Paragraph style={{marginBottom: 0}}>
                {record.description || '暂无描述'}
            </Typography.Paragraph>
            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                {formatMcpToolInputSchema(record.inputSchemaJson)}
            </Typography.Paragraph>
        </Space>
    )
}

export function isMcpToolRowExpandable(record: McpToolResponse) {
    return Boolean(record.description || record.inputSchemaJson)
}

export function createMcpToolColumns({
    canEditPolicy,
    canToggle,
    switchingId,
    includeServerColumn = true,
    onOpenPolicy,
    onToggleTool,
}: McpToolColumnsOptions): ColumnsType<McpToolResponse> {
    const columns: ColumnsType<McpToolResponse> = [
        {
            title: 'Tool Key',
            dataIndex: 'toolKey',
            fixed: 'left',
            width: 260,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
    ]

    if (includeServerColumn) {
        columns.push({title: '服务', dataIndex: 'serverCode', width: 150})
    }

    columns.push(
        {title: '工具名', dataIndex: 'toolName', width: 180},
        {
            title: '风险',
            dataIndex: 'riskLevel',
            width: 110,
            render: (value: McpToolRiskLevel) => <Tag color={mcpRiskLevelColor(value)}>{value}</Tag>,
        },
        {
            title: '只读',
            dataIndex: 'readonly',
            width: 90,
            render: (value: boolean) => <Tag color={value ? 'success' : 'warning'}>{value ? '是' : '否'}</Tag>,
        },
        {
            title: '确认',
            dataIndex: 'requireConfirm',
            width: 90,
            render: (value: boolean) => <Tag color={value ? 'warning' : 'default'}>{value ? '是' : '否'}</Tag>,
        },
        {
            title: '运行状态',
            dataIndex: 'runtimeStatus',
            width: 160,
            render: (value: McpToolRuntimeStatus) => <Tag color={mcpRuntimeStatusColor(value)}>{value}</Tag>,
        },
        {
            title: '启用',
            dataIndex: 'enabled',
            width: 90,
            render: (_, record) => (
                <Switch
                    checked={record.enabled}
                    disabled={!canToggle}
                    loading={switchingId === record.id}
                    onChange={(checked) => onToggleTool(record, checked)}
                    size="small"
                />
            ),
        },
        {
            title: '最近发现',
            dataIndex: 'lastDiscoveredAt',
            width: 190,
            render: (value?: string | number | null) => <DateTimeText value={value}/>,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 110,
            render: (_, record) => canEditPolicy
                ? <Button icon={<EditOutlined/>} onClick={() => onOpenPolicy(record)} size="small">策略</Button>
                : '-',
        },
    )

    return columns
}
```

- [ ] **Step 4: Refactor `McpToolListPage.tsx` imports**

In `fe/src/modules/agent/mcp/McpToolListPage.tsx`, replace the first imports with:

```tsx
import {ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Select, Space, Table} from 'antd'
import {useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {PageToolbar} from '../../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {mcpButtonCodes} from './mcpPermissionCodes'
import {disableMcpTool, enableMcpTool, getMcpServers, getMcpTools, updateMcpToolPolicy} from './mcpService'
import type {
    McpServerResponse,
    McpToolResponse,
    UpdateMcpToolPolicyRequest,
} from './mcpTypes'
import {McpToolPolicyDrawer} from './McpToolPolicyDrawer'
import {
    createMcpToolColumns,
    isMcpToolRowExpandable,
    renderMcpToolExpandedRow,
} from './mcpToolView'
```

- [ ] **Step 5: Refactor `McpToolListPage.tsx` columns**

In `fe/src/modules/agent/mcp/McpToolListPage.tsx`, replace the full `const columns = ...` block with:

```tsx
    const columns = createMcpToolColumns({
        canEditPolicy,
        canToggle,
        switchingId,
        onOpenPolicy: openPolicy,
        onToggleTool: (tool, checked) => void toggleTool(tool, checked),
    })
```

- [ ] **Step 6: Refactor `McpToolListPage.tsx` table expandable config**

In `fe/src/modules/agent/mcp/McpToolListPage.tsx`, replace the `expandable` object in the `<Table<McpToolResponse>>`
with:

```tsx
                expandable={{
                    expandedRowRender: renderMcpToolExpandedRow,
                    rowExpandable: isMcpToolRowExpandable,
                }}
```

- [ ] **Step 7: Remove obsolete local helpers from `McpToolListPage.tsx`**

Delete these functions from the bottom of `fe/src/modules/agent/mcp/McpToolListPage.tsx`:

```tsx
function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function formatJson(value?: string) {
    if (!value) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
        return value
    }
}

function riskLevelColor(value: McpToolRiskLevel) {
    if (value === 'HIGH') {
        return 'error'
    }
    if (value === 'MEDIUM') {
        return 'warning'
    }
    return 'success'
}

function runtimeStatusColor(value: McpToolRuntimeStatus) {
    if (value === 'AVAILABLE') {
        return 'success'
    }
    if (value === 'DISABLED' || value === 'SERVER_DISABLED') {
        return 'default'
    }
    if (value === 'POLICY_BLOCKED') {
        return 'warning'
    }
    return 'error'
}
```

- [ ] **Step 8: Run focused helper tests**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/mcpToolView.test.tsx
```

Expected: PASS. The output includes `mcpToolView.test.tsx` and zero failed tests.

- [ ] **Step 9: Run the existing MCP service/editor tests**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/mcpService.test.ts src/modules/agent/mcp/McpServerEditorDrawer.test.ts
```

Expected: PASS. These tests confirm the refactor did not disturb existing MCP service or server editor behavior.

- [ ] **Step 10: Commit Task 1**

Run:

```bash
git add fe/src/modules/agent/mcp/mcpToolView.tsx \
  fe/src/modules/agent/mcp/mcpToolView.test.tsx \
  fe/src/modules/agent/mcp/McpToolListPage.tsx
git commit -m "refactor(fe): share mcp tool table view"
```

Expected: commit succeeds and includes only the three listed files.

## Task 2: Add Service-Scoped Tool Policy Drawer

**Files:**

- Create: `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx`
- Create: `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.tsx`

- [ ] **Step 1: Write failing drawer title tests**

Create `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx` with:

```tsx
import {describe, expect, test} from 'vitest'
import type {McpServerResponse} from './mcpTypes'
import {getMcpServerToolPolicyDrawerTitle} from './McpServerToolPolicyDrawer'

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
})
```

- [ ] **Step 2: Run drawer tests and verify failure**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx
```

Expected: FAIL because `./McpServerToolPolicyDrawer` does not exist.

- [ ] **Step 3: Implement `McpServerToolPolicyDrawer.tsx`**

Create `fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.tsx` with:

```tsx
import {ReloadOutlined} from '@ant-design/icons'
import {App, Button, Drawer, Table, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {disableMcpTool, enableMcpTool, getMcpTools, updateMcpToolPolicy} from './mcpService'
import type {McpServerResponse, McpToolResponse, UpdateMcpToolPolicyRequest} from './mcpTypes'
import {McpToolPolicyDrawer} from './McpToolPolicyDrawer'
import {
    createMcpToolColumns,
    isMcpToolRowExpandable,
    renderMcpToolExpandedRow,
} from './mcpToolView'

type McpServerToolPolicyDrawerProps = {
    open: boolean
    server?: McpServerResponse | null
    canEditPolicy: boolean
    canToggle: boolean
    onClose: () => void
}

export function getMcpServerToolPolicyDrawerTitle(server?: McpServerResponse | null) {
    return server ? `工具策略：${server.serverName}` : '工具策略'
}

export function McpServerToolPolicyDrawer({
    open,
    server,
    canEditPolicy,
    canToggle,
    onClose,
}: McpServerToolPolicyDrawerProps) {
    const {message} = App.useApp()
    const [tools, setTools] = useState<McpToolResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [editingTool, setEditingTool] = useState<McpToolResponse | null>(null)
    const [policyOpen, setPolicyOpen] = useState(false)
    const [switchingId, setSwitchingId] = useState<number | null>(null)

    useEffect(() => {
        if (!open || !server) {
            setTools([])
            return
        }
        void loadTools(server.id)
    }, [open, server?.id])

    async function loadTools(serverId = server?.id) {
        if (!serverId) return
        setLoading(true)
        try {
            setTools(await getMcpTools(serverId))
        } catch (requestError) {
            setTools([])
            reportGlobalError(requestError)
        } finally {
            setLoading(false)
        }
    }

    function openPolicy(tool: McpToolResponse) {
        setEditingTool(tool)
        setPolicyOpen(true)
    }

    async function savePolicy(request: UpdateMcpToolPolicyRequest) {
        if (!editingTool) return
        setSaving(true)
        try {
            await updateMcpToolPolicy(editingTool.id, request)
            message.success('策略已保存')
            setPolicyOpen(false)
            await loadTools()
        } catch (requestError) {
            reportGlobalError(requestError)
            throw requestError
        } finally {
            setSaving(false)
        }
    }

    async function toggleTool(tool: McpToolResponse, checked: boolean) {
        setSwitchingId(tool.id)
        try {
            if (checked) {
                await enableMcpTool(tool.id)
            } else {
                await disableMcpTool(tool.id)
            }
            message.success(checked ? '工具已启用' : '工具已禁用')
            await loadTools()
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setSwitchingId(null)
        }
    }

    const columns = createMcpToolColumns({
        canEditPolicy,
        canToggle,
        switchingId,
        includeServerColumn: false,
        onOpenPolicy: openPolicy,
        onToggleTool: (tool, checked) => void toggleTool(tool, checked),
    })

    return (
        <Drawer
            destroyOnHidden
            extra={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void loadTools()}>刷新</Button>}
            onClose={onClose}
            open={open}
            title={getMcpServerToolPolicyDrawerTitle(server)}
            width={960}
        >
            <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                {server?.serverCode ?? '-'}
            </Typography.Paragraph>
            <Table<McpToolResponse>
                columns={columns}
                dataSource={tools}
                expandable={{
                    expandedRowRender: renderMcpToolExpandedRow,
                    rowExpandable: isMcpToolRowExpandable,
                }}
                loading={loading}
                locale={{emptyText: '暂无工具，请先在服务行点击“发现”'}}
                pagination={false}
                rowKey="id"
                scroll={{x: 1400}}
            />
            <McpToolPolicyDrawer
                loading={saving}
                onClose={() => setPolicyOpen(false)}
                onSubmit={savePolicy}
                open={policyOpen}
                tool={editingTool}
            />
        </Drawer>
    )
}
```

- [ ] **Step 4: Run drawer tests**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Run focused MCP helper and drawer tests together**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/mcpToolView.test.tsx src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.tsx \
  fe/src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx
git commit -m "feat(fe): add mcp server tool policy drawer"
```

Expected: commit succeeds and includes only the two listed files.

## Task 3: Wire the Service Page Entry and Permission Impact

**Files:**

- Modify: `fe/src/modules/agent/mcp/McpServerListPage.tsx`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.ts`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.test.ts`

- [ ] **Step 1: Update permission impact tests first**

In `fe/src/layouts/AdminLayout/permissionImpact.test.ts`, replace the first test with:

```ts
    test('matches lost button permissions by current route family', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.user.edit])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/rag/documents/12', [ragButtonCodes.chunk.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.server.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.tool.editPolicy])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.tool.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/tools', [mcpButtonCodes.tool.editPolicy])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/logs', [mcpButtonCodes.log.view])).toBe(true)
    })
```

Replace the second test with:

```ts
    test('ignores unrelated routes and empty permission changes', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.role.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/logs', [mcpButtonCodes.tool.toggle])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/chat', [rbacButtonCodes.user.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [])).toBe(false)
    })
```

- [ ] **Step 2: Run permission impact tests and verify failure**

Run:

```bash
cd fe && bun run test src/layouts/AdminLayout/permissionImpact.test.ts
```

Expected: FAIL because `/agent/mcp/servers` does not yet include MCP tool button permissions.

- [ ] **Step 3: Update `permissionImpact.ts`**

In `fe/src/layouts/AdminLayout/permissionImpact.ts`, replace the MCP servers route entry:

```ts
    {path: '/agent/mcp/servers', buttonCodes: Object.values(mcpButtonCodes.server)},
```

with:

```ts
    {
        path: '/agent/mcp/servers',
        buttonCodes: [...Object.values(mcpButtonCodes.server), ...Object.values(mcpButtonCodes.tool)],
    },
```

- [ ] **Step 4: Run permission impact tests**

Run:

```bash
cd fe && bun run test src/layouts/AdminLayout/permissionImpact.test.ts
```

Expected: PASS.

- [ ] **Step 5: Update `McpServerListPage.tsx` imports**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, replace the icon import with:

```tsx
import {
    DeleteOutlined,
    EditOutlined,
    ExperimentOutlined,
    PlusOutlined,
    SearchOutlined,
    ToolOutlined,
} from '@ant-design/icons'
```

Add these imports near the existing MCP imports:

```tsx
import {McpServerToolPolicyDrawer} from './McpServerToolPolicyDrawer'
import {canOpenServerToolPolicy} from './mcpToolView'
```

- [ ] **Step 6: Add service-page drawer state and permissions**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, after the existing `editorOpen` state, add:

```tsx
    const [policyServer, setPolicyServer] = useState<McpServerResponse | null>(null)
    const [policyOpen, setPolicyOpen] = useState(false)
```

Replace:

```tsx
    const canToggle = canUseRbacButton(auth, mcpButtonCodes.server.toggle)
```

with:

```tsx
    const canToggleServer = canUseRbacButton(auth, mcpButtonCodes.server.toggle)
    const canEditPolicy = canUseRbacButton(auth, mcpButtonCodes.tool.editPolicy)
    const canToggleTool = canUseRbacButton(auth, mcpButtonCodes.tool.toggle)
    const canManageToolPolicy = canOpenServerToolPolicy(canEditPolicy, canToggleTool)
```

- [ ] **Step 7: Add open and close handlers**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, after `openEditor`, add:

```tsx
    function openToolPolicy(server: McpServerResponse) {
        setPolicyServer(server)
        setPolicyOpen(true)
    }

    function closeToolPolicy() {
        setPolicyOpen(false)
    }
```

- [ ] **Step 8: Update server toggle permission reference**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, inside the server enabled `Switch`, replace:

```tsx
                    disabled={!canToggle}
```

with:

```tsx
                    disabled={!canToggleServer}
```

- [ ] **Step 9: Add the row action button**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, inside `renderActions`, after the existing `canDiscover` action
block and before the delete action block, add:

```tsx
        if (canManageToolPolicy) {
            actions.push(
                <Button icon={<ToolOutlined/>} key="tool-policy" onClick={() => openToolPolicy(record)} size="small">
                    工具策略
                </Button>,
            )
        }
```

Replace the return statement:

```tsx
        return actions.length ? <Space>{actions}</Space> : '-'
```

with:

```tsx
        return actions.length ? <Space wrap>{actions}</Space> : '-'
```

- [ ] **Step 10: Increase operation column width and table scroll**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, replace the operation column width:

```tsx
            width: 310,
```

with:

```tsx
            width: 430,
```

Replace the table scroll:

```tsx
                scroll={{x: 1650}}
```

with:

```tsx
                scroll={{x: 1770}}
```

- [ ] **Step 11: Render the new drawer**

In `fe/src/modules/agent/mcp/McpServerListPage.tsx`, after `<McpServerEditorDrawer ... />`, add:

```tsx
            <McpServerToolPolicyDrawer
                canEditPolicy={canEditPolicy}
                canToggle={canToggleTool}
                onClose={closeToolPolicy}
                open={policyOpen}
                server={policyServer}
            />
```

- [ ] **Step 12: Run focused route permission and MCP tests**

Run:

```bash
cd fe && bun run test src/layouts/AdminLayout/permissionImpact.test.ts \
  src/modules/agent/mcp/mcpToolView.test.tsx \
  src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx \
  src/modules/agent/mcp/mcpService.test.ts \
  src/modules/agent/mcp/McpServerEditorDrawer.test.ts
```

Expected: PASS.

- [ ] **Step 13: Commit Task 3**

Run:

```bash
git add fe/src/modules/agent/mcp/McpServerListPage.tsx \
  fe/src/layouts/AdminLayout/permissionImpact.ts \
  fe/src/layouts/AdminLayout/permissionImpact.test.ts
git commit -m "feat(fe): open mcp tool policy from server list"
```

Expected: commit succeeds and includes only the three listed files.

## Task 4: Final Verification

**Files:**

- Validate only. No source files should change in this task.

- [ ] **Step 1: Run Ant Design lint for changed frontend files**

Run:

```bash
cd fe
bunx --bun @ant-design/cli lint src/modules/agent/mcp/mcpToolView.tsx --format json
bunx --bun @ant-design/cli lint src/modules/agent/mcp/McpToolListPage.tsx --format json
bunx --bun @ant-design/cli lint src/modules/agent/mcp/McpServerToolPolicyDrawer.tsx --format json
bunx --bun @ant-design/cli lint src/modules/agent/mcp/McpServerListPage.tsx --format json
```

Expected: each command exits 0 and prints JSON. If the Ant Design CLI is unavailable, capture the exact command failure
in the final implementation report and continue with the project test/build commands below.

- [ ] **Step 2: Run all MCP and permission-impact frontend tests touched by this change**

Run:

```bash
cd fe && bun run test src/modules/agent/mcp/mcpToolView.test.tsx \
  src/modules/agent/mcp/McpServerToolPolicyDrawer.test.tsx \
  src/modules/agent/mcp/mcpService.test.ts \
  src/modules/agent/mcp/McpServerEditorDrawer.test.ts \
  src/layouts/AdminLayout/permissionImpact.test.ts
```

Expected: PASS.

- [ ] **Step 3: Run TypeScript typecheck**

Run:

```bash
cd fe && bun run typecheck
```

Expected: PASS.

- [ ] **Step 4: Run frontend build**

Run:

```bash
cd fe && bun run build
```

Expected: PASS. This validates Vite bundling and Ant Design imports without starting the app.

- [ ] **Step 5: Run whitespace diff check**

Run:

```bash
git diff --check
```

Expected: no output and exit 0.

- [ ] **Step 6: Confirm clean commit boundaries**

Run:

```bash
git status --short
```

Expected: no unstaged source changes from this task. If generated files or unrelated workspace files appear, do not
stage them.

## Self-Review Notes

- Spec coverage: The plan adds the service row action, uses `Drawer + Table`, filters tools by `server.id`, reuses
  `McpToolPolicyDrawer`, preserves the global tool policy page, avoids backend/database/runtime changes, and includes
  permission/validation coverage.
- Placeholder scan: The plan contains concrete file paths, code snippets, commands, and expected results. It does not
  rely on open implementation slots.
- Type consistency: `McpServerToolPolicyDrawer`, `canOpenServerToolPolicy`, `createMcpToolColumns`,
  `renderMcpToolExpandedRow`, and `isMcpToolRowExpandable` are defined before later tasks use them.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-25-mcp-tool-policy-drawer.md`. Execute with one of these
approaches:

1. **Subagent-Driven (recommended)** - use `superpowers:subagent-driven-development`, dispatch a fresh subagent per
   task, review between tasks, and commit each task separately.
2. **Inline Execution** - use `superpowers:executing-plans`, execute the tasks in this session, and use the checkboxes
   as checkpoints.

Do not start the project or open a browser during implementation; validation should use the Ant Design CLI, Bun test,
typecheck, and build commands listed in Task 4.
