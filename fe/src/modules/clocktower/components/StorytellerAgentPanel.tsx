import {
    DatabaseOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons'
import {App, Button, Drawer, Empty, List, Space, Spin, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    getClocktowerAgentMemory,
    getClocktowerAgentTasks,
    getClocktowerGameAgents,
    pauseClocktowerAgent,
    resumeClocktowerAgent,
    runClocktowerAgentNow,
} from '../clocktowerService'
import type {
    ClocktowerAgentConsoleView,
    ClocktowerAgentMemoryView,
    ClocktowerAgentTaskView,
} from '../clocktowerTypes'

type AgentAction = 'pause' | 'resume' | 'run-now'

export function StorytellerAgentPanel({gameId}: { gameId: number }) {
    const {message} = App.useApp()
    const [agents, setAgents] = useState<ClocktowerAgentConsoleView[]>([])
    const [loading, setLoading] = useState(false)
    const [actionLoadingKey, setActionLoadingKey] = useState<string | null>(null)
    const [selectedAgent, setSelectedAgent] = useState<ClocktowerAgentConsoleView | null>(null)
    const [memory, setMemory] = useState<ClocktowerAgentMemoryView[]>([])
    const [tasks, setTasks] = useState<ClocktowerAgentTaskView[]>([])
    const [memoryLoading, setMemoryLoading] = useState(false)

    const refresh = useCallback(async () => {
        setLoading(true)
        try {
            setAgents(await getClocktowerGameAgents(gameId))
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [gameId])

    useEffect(() => {
        void refresh()
    }, [refresh])

    async function runAgentAction(agent: ClocktowerAgentConsoleView, action: AgentAction) {
        setActionLoadingKey(`${action}:${agent.agentInstanceId}`)
        try {
            if (action === 'pause') {
                await pauseClocktowerAgent(gameId, agent.agentInstanceId)
                message.success('Agent 已暂停')
            }
            if (action === 'resume') {
                await resumeClocktowerAgent(gameId, agent.agentInstanceId)
                message.success('Agent 已恢复')
            }
            if (action === 'run-now') {
                await runClocktowerAgentNow(gameId, agent.agentInstanceId)
                message.success('Agent 已加入立即运行队列')
            }
            await refresh()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoadingKey(null)
        }
    }

    async function openMemory(agent: ClocktowerAgentConsoleView) {
        setSelectedAgent(agent)
        setMemoryLoading(true)
        try {
            const [nextMemory, nextTasks] = await Promise.all([
                getClocktowerAgentMemory(gameId, agent.agentInstanceId),
                getClocktowerAgentTasks(gameId, agent.agentInstanceId),
            ])
            setMemory(nextMemory)
            setTasks(nextTasks)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setMemoryLoading(false)
        }
    }

    return (
        <>
            <StorytellerAgentPanelContent
                actionLoadingKey={actionLoadingKey}
                agents={agents}
                loading={loading}
                onOpenMemory={openMemory}
                onPause={(agent) => void runAgentAction(agent, 'pause')}
                onRefresh={() => void refresh()}
                onResume={(agent) => void runAgentAction(agent, 'resume')}
                onRunNow={(agent) => void runAgentAction(agent, 'run-now')}
            />
            <Drawer
                destroyOnHidden
                onClose={() => setSelectedAgent(null)}
                open={Boolean(selectedAgent)}
                title={selectedAgent ? `${selectedAgent.displayName ?? selectedAgent.agentInstanceId} · 记忆` : 'Agent 记忆'}
                width={520}
            >
                <Spin spinning={memoryLoading}>
                    <Space direction="vertical" size="middle" style={{width: '100%'}}>
                        <Typography.Text strong>记忆摘要</Typography.Text>
                        <List
                            dataSource={memory}
                            locale={{emptyText: '暂无记忆'}}
                            renderItem={(item) => (
                                <List.Item>
                                    <Space direction="vertical" size={4}>
                                        <Space wrap>
                                            <Tag>{item.memoryType}</Tag>
                                            <Tag>{item.visibility}</Tag>
                                            <Typography.Text type="secondary">信心 {item.confidence}</Typography.Text>
                                        </Space>
                                        <Typography.Text>{compactJson(item.content)}</Typography.Text>
                                    </Space>
                                </List.Item>
                            )}
                        />
                        <Typography.Text strong>最近任务</Typography.Text>
                        <List
                            dataSource={tasks}
                            locale={{emptyText: '暂无任务'}}
                            renderItem={(item) => (
                                <List.Item>
                                    <Space direction="vertical" size={4}>
                                        <Space wrap>
                                            <Tag>{item.status}</Tag>
                                            <Typography.Text>{item.triggerType}</Typography.Text>
                                        </Space>
                                        <Typography.Text type={item.lastError ? 'danger' : 'secondary'}>
                                            {item.lastError ?? compactJson(item.result)}
                                        </Typography.Text>
                                    </Space>
                                </List.Item>
                            )}
                        />
                    </Space>
                </Spin>
            </Drawer>
        </>
    )
}

export function StorytellerAgentPanelContent({
    actionLoadingKey,
    agents,
    loading,
    onOpenMemory,
    onPause,
    onRefresh,
    onResume,
    onRunNow,
}: {
    agents: ClocktowerAgentConsoleView[]
    loading: boolean
    actionLoadingKey: string | null
    onRefresh: () => void
    onPause: (agent: ClocktowerAgentConsoleView) => void
    onResume: (agent: ClocktowerAgentConsoleView) => void
    onRunNow: (agent: ClocktowerAgentConsoleView) => void
    onOpenMemory: (agent: ClocktowerAgentConsoleView) => void
}) {
    return (
        <Spin spinning={loading}>
            <Space direction="vertical" size="middle" style={{width: '100%'}}>
                <Button icon={<ReloadOutlined/>} onClick={onRefresh}>刷新</Button>
                {agents.length === 0 ? (
                    <Empty description="暂无 Agent"/>
                ) : (
                    <List
                        dataSource={agents}
                        renderItem={(agent) => (
                            <List.Item>
                                <Space direction="vertical" size="small" style={{width: '100%'}}>
                                    <Space wrap>
                                        <Typography.Text strong>
                                            #{agent.seatNo ?? '-'} {agent.displayName ?? `Agent ${agent.agentInstanceId}`}
                                        </Typography.Text>
                                        <Tag color={agent.autoMode === 'PAUSED' ? 'red' : 'green'}>{agent.autoMode}</Tag>
                                        <Tag>{agent.status}</Tag>
                                        {agent.roleCode && <Tag color="purple">{agent.roleCode}</Tag>}
                                        {agent.alignment && <Tag>{agent.alignment}</Tag>}
                                    </Space>
                                    <Typography.Text type="secondary">
                                        最近任务：{agent.recentTaskStatus ?? '-'} {agent.recentTaskTriggerType ?? ''}
                                    </Typography.Text>
                                    {agent.recentError && (
                                        <Typography.Text type="danger">{agent.recentError}</Typography.Text>
                                    )}
                                    <Typography.Text type="secondary">
                                        最近结果：{compactJson(agent.recentTaskResult)}
                                    </Typography.Text>
                                    <Space wrap>
                                        <Button
                                            disabled={agent.autoMode === 'PAUSED'}
                                            icon={<PauseCircleOutlined/>}
                                            loading={actionLoadingKey === `pause:${agent.agentInstanceId}`}
                                            onClick={() => onPause(agent)}
                                        >
                                            暂停
                                        </Button>
                                        <Button
                                            disabled={agent.autoMode !== 'PAUSED'}
                                            icon={<PlayCircleOutlined/>}
                                            loading={actionLoadingKey === `resume:${agent.agentInstanceId}`}
                                            onClick={() => onResume(agent)}
                                        >
                                            恢复
                                        </Button>
                                        <Button
                                            disabled={!agent.gameSeatId || agent.status !== 'ACTIVE'}
                                            icon={<ThunderboltOutlined/>}
                                            loading={actionLoadingKey === `run-now:${agent.agentInstanceId}`}
                                            onClick={() => onRunNow(agent)}
                                            type="primary"
                                        >
                                            立即运行
                                        </Button>
                                        <Button icon={<DatabaseOutlined/>} onClick={() => onOpenMemory(agent)}>
                                            查看记忆
                                        </Button>
                                    </Space>
                                </Space>
                            </List.Item>
                        )}
                    />
                )}
            </Space>
        </Spin>
    )
}

function compactJson(value?: Record<string, unknown> | null) {
    if (!value || Object.keys(value).length === 0) {
        return '-'
    }
    return JSON.stringify(value)
}
