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
