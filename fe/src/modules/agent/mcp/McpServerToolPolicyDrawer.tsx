import {ReloadOutlined} from '@ant-design/icons'
import {App, Button, Drawer, Table, Typography} from 'antd'
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {disableMcpTool, enableMcpTool, getMcpTools, updateMcpToolPolicy} from './mcpService'
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

type McpServerToolPolicyDrawerProps = {
    open: boolean
    server?: McpServerResponse | null
    canEditPolicy: boolean
    canToggle: boolean
    onClose: () => void
}

type ToggleToolRequest = {
    tool: McpToolResponse
    checked: boolean
    seq: number
}

export function getMcpServerToolPolicyDrawerTitle(server?: McpServerResponse | null) {
    return server ? `工具策略：${server.serverName}` : '工具策略'
}

export function isMcpToolLoadCurrent(
    expectedServerId: number,
    open: boolean,
    currentServerId?: number | null,
) {
    return open && currentServerId === expectedServerId
}

export function canQueueMcpToolToggle(
    toolServerId: number,
    switchingId: number | null,
    open: boolean,
    currentServerId?: number | null,
) {
    return switchingId === null && isMcpToolLoadCurrent(toolServerId, open, currentServerId)
}

export function McpServerToolPolicyDrawer({
    open,
    server,
    canEditPolicy,
    canToggle,
    onClose,
}: McpServerToolPolicyDrawerProps) {
    const {message} = App.useApp()
    const serverId = server?.id
    const lifecycleRef = useRef({open, serverId})
    const loadSeqRef = useRef(0)
    const [tools, setTools] = useState<McpToolResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [editingTool, setEditingTool] = useState<McpToolResponse | null>(null)
    const [policyOpen, setPolicyOpen] = useState(false)
    const [switchingId, setSwitchingId] = useState<number | null>(null)
    const [toggleRequest, setToggleRequest] = useState<ToggleToolRequest | null>(null)

    useLayoutEffect(() => {
        lifecycleRef.current = {open, serverId}
    }, [open, serverId])

    const resetTransientState = useCallback(() => {
        setPolicyOpen(false)
        setEditingTool(null)
        setSaving(false)
        setSwitchingId(null)
        setToggleRequest(null)
    }, [])

    const isCurrentLoad = useCallback((loadSeq: number, expectedServerId: number) => {
        const lifecycle = lifecycleRef.current
        return loadSeqRef.current === loadSeq
            && isMcpToolLoadCurrent(expectedServerId, lifecycle.open, lifecycle.serverId)
    }, [])

    const loadTools = useCallback(async (targetServerId = serverId) => {
        if (!targetServerId) return
        const lifecycle = lifecycleRef.current
        if (!isMcpToolLoadCurrent(targetServerId, lifecycle.open, lifecycle.serverId)) return
        const loadSeq = loadSeqRef.current + 1
        loadSeqRef.current = loadSeq
        setLoading(true)
        try {
            const nextTools = await getMcpTools(targetServerId)
            if (isCurrentLoad(loadSeq, targetServerId)) {
                setTools(nextTools)
            }
        } catch (requestError) {
            if (isCurrentLoad(loadSeq, targetServerId)) {
                setTools([])
                reportGlobalError(requestError)
            }
        } finally {
            if (isCurrentLoad(loadSeq, targetServerId)) {
                setLoading(false)
            }
        }
    }, [isCurrentLoad, serverId])

    useEffect(() => {
        resetTransientState()
        if (!open || !serverId) {
            loadSeqRef.current += 1
            setTools([])
            setLoading(false)
            return
        }
        setTools([])
        void loadTools(serverId)
    }, [loadTools, open, resetTransientState, serverId])

    const openPolicy = useCallback((tool: McpToolResponse) => {
        if (!isMcpToolLoadCurrent(tool.serverId, open, serverId)) return
        setEditingTool(tool)
        setPolicyOpen(true)
    }, [open, serverId])

    const queueToggleTool = useCallback((tool: McpToolResponse, checked: boolean) => {
        if (!canQueueMcpToolToggle(tool.serverId, switchingId, open, serverId)) return
        setSwitchingId(tool.id)
        setToggleRequest((current) => ({
            tool,
            checked,
            seq: (current?.seq ?? 0) + 1,
        }))
    }, [open, serverId, switchingId])

    const savePolicy = useCallback(async (request: UpdateMcpToolPolicyRequest) => {
        const tool = editingTool
        if (!tool || !isMcpToolLoadCurrent(tool.serverId, open, serverId)) return
        setSaving(true)
        try {
            await updateMcpToolPolicy(tool.id, request)
            const lifecycle = lifecycleRef.current
            if (isMcpToolLoadCurrent(tool.serverId, lifecycle.open, lifecycle.serverId)) {
                message.success('策略已保存')
                setPolicyOpen(false)
                setEditingTool(null)
                await loadTools(tool.serverId)
            }
        } catch (requestError) {
            const lifecycle = lifecycleRef.current
            if (isMcpToolLoadCurrent(tool.serverId, lifecycle.open, lifecycle.serverId)) {
                reportGlobalError(requestError)
                throw requestError
            }
        } finally {
            const lifecycle = lifecycleRef.current
            if (isMcpToolLoadCurrent(tool.serverId, lifecycle.open, lifecycle.serverId)) {
                setSaving(false)
            }
        }
    }, [editingTool, loadTools, message, open, serverId])

    useEffect(() => {
        if (!toggleRequest) return
        let active = true
        const {tool, checked} = toggleRequest
        async function runToggle() {
            const lifecycle = lifecycleRef.current
            if (!isMcpToolLoadCurrent(tool.serverId, lifecycle.open, lifecycle.serverId)) return
            setSwitchingId(tool.id)
            try {
                if (checked) {
                    await enableMcpTool(tool.id)
                } else {
                    await disableMcpTool(tool.id)
                }
                const nextLifecycle = lifecycleRef.current
                if (active && isMcpToolLoadCurrent(tool.serverId, nextLifecycle.open, nextLifecycle.serverId)) {
                    message.success(checked ? '工具已启用' : '工具已禁用')
                    await loadTools(tool.serverId)
                }
            } catch (requestError) {
                const nextLifecycle = lifecycleRef.current
                if (active && isMcpToolLoadCurrent(tool.serverId, nextLifecycle.open, nextLifecycle.serverId)) {
                    reportGlobalError(requestError)
                }
            } finally {
                const nextLifecycle = lifecycleRef.current
                if (active && isMcpToolLoadCurrent(tool.serverId, nextLifecycle.open, nextLifecycle.serverId)) {
                    setSwitchingId(null)
                }
            }
        }
        void runToggle()
        return () => {
            active = false
        }
    }, [loadTools, message, toggleRequest])

    const columns = createMcpToolColumns({
        canEditPolicy,
        canToggle: canToggle && switchingId === null,
        switchingId,
        includeServerColumn: false,
        onOpenPolicy: openPolicy,
        onToggleTool: queueToggleTool,
    })
    const currentTools = open && server ? tools.filter((tool) => tool.serverId === server.id) : []
    const currentEditingTool = editingTool && isMcpToolLoadCurrent(editingTool.serverId, open, serverId)
        ? editingTool
        : null
    const currentPolicyOpen = policyOpen && Boolean(currentEditingTool)

    return (
        <Drawer
            destroyOnHidden
            extra={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void loadTools()}>刷新</Button>}
            onClose={onClose}
            open={open}
            title={getMcpServerToolPolicyDrawerTitle(server)}
            width={960}
        >
            <Typography.Paragraph style={{marginTop: 0}} type="secondary">
                服务编码：{server?.serverCode ?? '-'}
            </Typography.Paragraph>
            <Table<McpToolResponse>
                columns={columns}
                dataSource={currentTools}
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
                onClose={() => {
                    setPolicyOpen(false)
                    setEditingTool(null)
                }}
                onSubmit={savePolicy}
                open={currentPolicyOpen}
                tool={currentEditingTool}
            />
        </Drawer>
    )
}
