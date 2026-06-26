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
            reportGlobalError(requestError)
        }
    }

    async function loadTools(serverId = selectedServerId) {
        setLoading(true)
        try {
            setTools(await getMcpTools(serverId))
        } catch (requestError) {
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
        onOpenPolicy: openPolicy,
        onToggleTool: (tool, checked) => void toggleTool(tool, checked),
    })

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
                    expandedRowRender: renderMcpToolExpandedRow,
                    rowExpandable: isMcpToolRowExpandable,
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

export const Component = McpToolListPage
