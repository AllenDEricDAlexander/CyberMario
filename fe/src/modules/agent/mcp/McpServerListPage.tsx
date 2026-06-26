import {DeleteOutlined, EditOutlined, ExperimentOutlined, PlusOutlined, SearchOutlined, ToolOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Space, Switch, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {DateTimeText} from '../../../components/DateTimeText'
import {PageToolbar} from '../../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {mcpButtonCodes} from './mcpPermissionCodes'
import {
    createMcpServer,
    deleteMcpServer,
    disableMcpServer,
    discoverMcpTools,
    enableMcpServer,
    getMcpServers,
    testMcpServer,
    updateMcpServer,
} from './mcpService'
import type {
    CreateMcpServerRequest,
    McpServerResponse,
    McpServerStatus,
    McpTransportType,
    UpdateMcpServerRequest,
} from './mcpTypes'
import {McpServerEditorDrawer} from './McpServerEditorDrawer'
import {McpServerToolPolicyDrawer} from './McpServerToolPolicyDrawer'
import {canOpenServerToolPolicy} from './mcpToolView'

function McpServerListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [servers, setServers] = useState<McpServerResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [editingServer, setEditingServer] = useState<McpServerResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [policyServer, setPolicyServer] = useState<McpServerResponse | null>(null)
    const [policyOpen, setPolicyOpen] = useState(false)
    const [switchingId, setSwitchingId] = useState<number | null>(null)

    const canCreate = canUseRbacButton(auth, mcpButtonCodes.server.create)
    const canEdit = canUseRbacButton(auth, mcpButtonCodes.server.edit)
    const canDelete = canUseRbacButton(auth, mcpButtonCodes.server.delete)
    const canTest = canUseRbacButton(auth, mcpButtonCodes.server.test)
    const canDiscover = canUseRbacButton(auth, mcpButtonCodes.server.discover)
    const canToggleServer = canUseRbacButton(auth, mcpButtonCodes.server.toggle)
    const canEditPolicy = canUseRbacButton(auth, mcpButtonCodes.tool.editPolicy)
    const canToggleTool = canUseRbacButton(auth, mcpButtonCodes.tool.toggle)
    const canManageToolPolicy = canOpenServerToolPolicy(canEditPolicy, canToggleTool)

    useEffect(() => {
        void loadServers()
    }, [])

    async function loadServers() {
        setLoading(true)
        try {
            setServers(await getMcpServers())
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setLoading(false)
        }
    }

    function openEditor(server?: McpServerResponse) {
        setEditingServer(server ?? null)
        setEditorOpen(true)
    }

    function openToolPolicy(server: McpServerResponse) {
        setPolicyServer(server)
        setPolicyOpen(true)
    }

    function closeToolPolicy() {
        setPolicyOpen(false)
    }

    async function saveServer(request: CreateMcpServerRequest | UpdateMcpServerRequest) {
        setSaving(true)
        try {
            if (editingServer) {
                await updateMcpServer(editingServer.id, request)
            } else {
                await createMcpServer(request as CreateMcpServerRequest)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await loadServers()
        } catch (requestError) {
            reportGlobalError(requestError)
            throw requestError
        } finally {
            setSaving(false)
        }
    }

    async function removeServer(server: McpServerResponse) {
        try {
            await deleteMcpServer(server.id)
            message.success('服务已删除')
            await loadServers()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    async function toggleServer(server: McpServerResponse, checked: boolean) {
        setSwitchingId(server.id)
        try {
            if (checked) {
                await enableMcpServer(server.id)
            } else {
                await disableMcpServer(server.id)
            }
            message.success(checked ? '服务已启用' : '服务已禁用')
            await loadServers()
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setSwitchingId(null)
        }
    }

    async function testServer(server: McpServerResponse) {
        try {
            const result = await testMcpServer(server.id)
            if (result.success) {
                message.success(`连接成功，发现 ${result.toolCount} 个工具`)
            } else {
                reportGlobalError(result.errorMessage || '连接测试失败')
            }
            await loadServers()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    async function discoverTools(server: McpServerResponse) {
        try {
            const result = await discoverMcpTools(server.id)
            message.success(`发现 ${result.discoveredCount} 个工具，新增 ${result.createdCount} 个，更新 ${result.updatedCount} 个`)
            await loadServers()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    const columns: ColumnsType<McpServerResponse> = [
        {title: '编码', dataIndex: 'serverCode', fixed: 'left', width: 170},
        {title: '名称', dataIndex: 'serverName', width: 180},
        {
            title: '传输',
            dataIndex: 'transportType',
            width: 150,
            render: (value: McpTransportType) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: 'Base URL',
            dataIndex: 'baseUrl',
            width: 260,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: 'Endpoint', dataIndex: 'endpoint', width: 160},
        {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (value: McpServerStatus) => <Tag color={serverStatusColor(value)}>{value}</Tag>,
        },
        {
            title: '启用',
            dataIndex: 'enabled',
            width: 100,
            render: (_, record) => (
                <Switch
                    checked={record.enabled}
                    disabled={!canToggleServer}
                    loading={switchingId === record.id}
                    onChange={(checked) => void toggleServer(record, checked)}
                    size="small"
                />
            ),
        },
        {title: '最近连接', dataIndex: 'lastConnectedAt', width: 190, render: renderDateTime},
        {
            title: '操作',
            fixed: 'right',
            width: 430,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: McpServerResponse) {
        const actions: ReactNode[] = []
        if (canEdit) {
            actions.push(
                <Button icon={<EditOutlined/>} key="edit" onClick={() => openEditor(record)} size="small">编辑</Button>,
            )
        }
        if (canTest) {
            actions.push(
                <Button icon={<ExperimentOutlined/>} key="test" onClick={() => void testServer(record)} size="small">
                    测试
                </Button>,
            )
        }
        if (canDiscover) {
            actions.push(
                <Button icon={<SearchOutlined/>} key="discover" onClick={() => void discoverTools(record)} size="small">
                    发现
                </Button>,
            )
        }
        if (canManageToolPolicy) {
            actions.push(
                <Button icon={<ToolOutlined/>} key="tool-policy" onClick={() => openToolPolicy(record)} size="small">
                    工具策略
                </Button>,
            )
        }
        if (canDelete) {
            actions.push(
                <Popconfirm key="delete" title="确认删除该 MCP 服务？" onConfirm={() => void removeServer(record)}>
                    <Button danger icon={<DeleteOutlined/>} size="small">删除</Button>
                </Popconfirm>,
            )
        }
        return actions.length ? <Space wrap>{actions}</Space> : '-'
    }

    return (
        <>
            <PageToolbar
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建服务</Button>}
                description="维护 ReactAgent 可连接的 MCP 服务、连接参数和工具发现状态。"
                title="MCP 服务配置"
            />
            <Table<McpServerResponse>
                columns={columns}
                dataSource={servers}
                loading={loading}
                pagination={false}
                rowKey="id"
                scroll={{x: 1770}}
            />
            <McpServerEditorDrawer
                loading={saving}
                onClose={() => setEditorOpen(false)}
                onSubmit={saveServer}
                open={editorOpen}
                server={editingServer}
            />
            <McpServerToolPolicyDrawer
                canEditPolicy={canEditPolicy}
                canToggle={canToggleTool}
                onClose={closeToolPolicy}
                open={policyOpen}
                server={policyServer}
            />
        </>
    )
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function serverStatusColor(status: McpServerStatus) {
    if (status === 'CONNECTED') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    if (status === 'CONNECTING') {
        return 'processing'
    }
    return 'default'
}

export const Component = McpServerListPage
