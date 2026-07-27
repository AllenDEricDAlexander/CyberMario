import {ReloadOutlined, ToolOutlined} from '@ant-design/icons'
import {App, Button, Form, Select} from 'antd'
import {useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {DataTable} from '../../../components/DataTable'
import {FilterBar} from '../../../components/FilterBar'
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
                icon={<ToolOutlined/>}
                title="MCP 工具策略"
            />
            <FilterBar<McpToolFilterForm>
                form={form}
                instant
                loading={loading}
                onReset={() => setSelectedServerId(undefined)}
                onSearch={(values) => setSelectedServerId(values.serverId)}
            >
                <Form.Item label="MCP 服务" name="serverId">
                    <Select
                        allowClear
                        options={servers.map((server) => ({
                            label: `${server.serverName} (${server.serverCode})`,
                            value: server.id,
                        }))}
                        placeholder="全部服务"
                    />
                </Form.Item>
            </FilterBar>
            <DataTable<McpToolResponse>
                columns={columns}
                count={tools.length}
                dataSource={tools}
                emptyDescription="先在“MCP 服务配置”里对目标服务执行一次“发现工具”。"
                emptyTitle="没有已发现的工具"
                expandable={{
                    expandedRowRender: renderMcpToolExpandedRow,
                    rowExpandable: isMcpToolRowExpandable,
                }}
                loading={loading}
                pagination={false}
                rowKey="id"
                scroll={{x: 1600}}
                title="工具列表"
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
