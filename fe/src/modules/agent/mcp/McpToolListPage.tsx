import {EditOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Select, Space, Switch, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {DateTimeText} from '../../../components/DateTimeText'
import {PageToolbar} from '../../../components/PageToolbar'
import {resolveErrorMessage} from '../../../services/request'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {mcpButtonCodes} from './mcpPermissionCodes'
import {disableMcpTool, enableMcpTool, getMcpServers, getMcpTools, updateMcpToolPolicy} from './mcpService'
import type {
    McpServerResponse,
    McpToolResponse,
    McpToolRiskLevel,
    McpToolRuntimeStatus,
    UpdateMcpToolPolicyRequest,
} from './mcpTypes'
import {McpToolPolicyDrawer} from './McpToolPolicyDrawer'

type McpToolFilterForm = {
    serverId?: number
}

function McpToolListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [form] = Form.useForm<McpToolFilterForm>()
    const [servers, setServers] = useState<McpServerResponse[]>([])
    const [tools, setTools] = useState<McpToolResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [selectedServerId, setSelectedServerId] = useState<number>()
    const [editingTool, setEditingTool] = useState<McpToolResponse | null>(null)
    const [policyOpen, setPolicyOpen] = useState(false)
    const [switchingId, setSwitchingId] = useState<number | null>(null)

    const canEditPolicy = canUseRbacButton(auth, mcpButtonCodes.tool.editPolicy)
    const canToggle = canUseRbacButton(auth, mcpButtonCodes.tool.toggle)

    useEffect(() => {
        void loadServers()
    }, [])

    useEffect(() => {
        void loadTools(selectedServerId)
    }, [selectedServerId])

    async function loadServers() {
        try {
            setServers(await getMcpServers())
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        }
    }

    async function loadTools(serverId = selectedServerId) {
        setLoading(true)
        try {
            setTools(await getMcpTools(serverId))
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
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
            message.error(resolveErrorMessage(requestError))
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
            message.error(resolveErrorMessage(requestError))
        } finally {
            setSwitchingId(null)
        }
    }

    const columns: ColumnsType<McpToolResponse> = [
        {
            title: 'Tool Key',
            dataIndex: 'toolKey',
            fixed: 'left',
            width: 260,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '服务', dataIndex: 'serverCode', width: 150},
        {title: '工具名', dataIndex: 'toolName', width: 180},
        {
            title: '风险',
            dataIndex: 'riskLevel',
            width: 110,
            render: (value: McpToolRiskLevel) => <Tag color={riskLevelColor(value)}>{value}</Tag>,
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
            render: (value: McpToolRuntimeStatus) => <Tag color={runtimeStatusColor(value)}>{value}</Tag>,
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
                    onChange={(checked) => void toggleTool(record, checked)}
                    size="small"
                />
            ),
        },
        {title: '最近发现', dataIndex: 'lastDiscoveredAt', width: 190, render: renderDateTime},
        {
            title: '操作',
            fixed: 'right',
            width: 110,
            render: (_, record) => canEditPolicy
                ? <Button icon={<EditOutlined/>} onClick={() => openPolicy(record)} size="small">策略</Button>
                : '-',
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading}
                                 onClick={() => void loadTools()}>刷新</Button>}
                description="查看已发现的 MCP 工具，并维护风险等级、只读和启用策略。"
                title="MCP 工具策略"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space wrap>
                        <Form.Item label="MCP 服务" name="serverId">
                            <Select
                                allowClear
                                onChange={(value?: number) => setSelectedServerId(value)}
                                options={servers.map((server) => ({
                                    label: `${server.serverName} (${server.serverCode})`,
                                    value: server.id,
                                }))}
                                placeholder="全部服务"
                                style={{width: 280}}
                            />
                        </Form.Item>
                    </Space>
                </Form>
            </Card>
            <Table<McpToolResponse>
                columns={columns}
                dataSource={tools}
                expandable={{
                    expandedRowRender: (record) => (
                        <Space direction="vertical" size={12} style={{width: '100%'}}>
                            <Typography.Paragraph style={{marginBottom: 0}}>
                                {record.description || '暂无描述'}
                            </Typography.Paragraph>
                            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                {formatJson(record.inputSchemaJson)}
                            </Typography.Paragraph>
                        </Space>
                    ),
                    rowExpandable: (record) => Boolean(record.description || record.inputSchemaJson),
                }}
                loading={loading}
                pagination={false}
                rowKey="id"
                scroll={{x: 1600}}
                style={{marginTop: 16}}
            />
            <McpToolPolicyDrawer
                loading={saving}
                onClose={() => setPolicyOpen(false)}
                onSubmit={savePolicy}
                open={policyOpen}
                tool={editingTool}
            />
        </>
    )
}

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

export const Component = McpToolListPage
