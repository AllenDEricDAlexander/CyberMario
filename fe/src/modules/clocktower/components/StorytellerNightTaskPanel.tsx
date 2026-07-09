import {CheckCircleOutlined, ControlOutlined, ReloadOutlined, RetweetOutlined, StopOutlined} from '@ant-design/icons'
import {App, Button, Empty, List, Modal, Select, Space, Spin, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    getClocktowerNightTasks,
    randomChoiceClocktowerNightTask,
    resolveClocktowerNightTask,
    skipClocktowerGameNightTask,
} from '../clocktowerService'
import type {ClocktowerGameSeatResponse, ClocktowerNightTaskView} from '../clocktowerTypes'

type NightTaskAction = 'resolve' | 'skip' | 'random' | 'manual'

export function StorytellerNightTaskPanel({
    gameId,
    seats,
}: {
    gameId: number
    seats: ClocktowerGameSeatResponse[]
}) {
    const {message} = App.useApp()
    const [tasks, setTasks] = useState<ClocktowerNightTaskView[]>([])
    const [loading, setLoading] = useState(false)
    const [actionLoadingKey, setActionLoadingKey] = useState<string | null>(null)
    const [manualTask, setManualTask] = useState<ClocktowerNightTaskView | null>(null)
    const [targetIds, setTargetIds] = useState<number[]>([])

    const refresh = useCallback(async () => {
        setLoading(true)
        try {
            setTasks(await getClocktowerNightTasks(gameId))
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [gameId])

    useEffect(() => {
        void refresh()
    }, [refresh])

    async function runAction(task: ClocktowerNightTaskView, action: NightTaskAction) {
        setActionLoadingKey(`${action}:${task.taskId}`)
        try {
            if (action === 'resolve') {
                await resolveClocktowerNightTask(gameId, task.taskId)
                message.success('夜晚任务已确认')
            }
            if (action === 'skip') {
                await skipClocktowerGameNightTask(gameId, task.taskId, {reason: 'ST skip'})
                message.success('夜晚任务已跳过')
            }
            if (action === 'random') {
                await randomChoiceClocktowerNightTask(gameId, task.taskId)
                message.success('夜晚任务已随机兜底')
            }
            await refresh()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoadingKey(null)
        }
    }

    async function submitManualTarget() {
        if (!manualTask) {
            return
        }
        setActionLoadingKey(`manual:${manualTask.taskId}`)
        try {
            await resolveClocktowerNightTask(gameId, manualTask.taskId, {
                targetGameSeatIds: targetIds,
                payload: {},
                note: 'ST override',
            })
            message.success('夜晚任务已覆盖目标')
            setManualTask(null)
            setTargetIds([])
            await refresh()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoadingKey(null)
        }
    }

    const options = useMemo(() => seats.map((seat) => ({
        label: `#${seat.seatNo} ${seat.displayName}`,
        value: seat.gameSeatId,
    })), [seats])

    return (
        <>
            <StorytellerNightTaskPanelContent
                actionLoadingKey={actionLoadingKey}
                loading={loading}
                onManualTarget={(task) => {
                    setManualTask(task)
                    setTargetIds([])
                }}
                onRandom={(task) => void runAction(task, 'random')}
                onRefresh={() => void refresh()}
                onResolve={(task) => void runAction(task, 'resolve')}
                onSkip={(task) => void runAction(task, 'skip')}
                seats={seats}
                tasks={tasks}
            />
            <Modal
                confirmLoading={actionLoadingKey === `manual:${manualTask?.taskId}`}
                destroyOnHidden
                okText="确认覆盖"
                onCancel={() => setManualTask(null)}
                onOk={() => void submitManualTarget()}
                open={Boolean(manualTask)}
                title="手动选目标"
            >
                <Select
                    mode="multiple"
                    onChange={setTargetIds}
                    options={options}
                    placeholder="选择目标座位"
                    style={{width: '100%'}}
                    value={targetIds}
                />
            </Modal>
        </>
    )
}

export function StorytellerNightTaskPanelContent({
    actionLoadingKey,
    loading,
    onManualTarget,
    onRandom,
    onRefresh,
    onResolve,
    onSkip,
    seats,
    tasks,
}: {
    tasks: ClocktowerNightTaskView[]
    seats: ClocktowerGameSeatResponse[]
    loading: boolean
    actionLoadingKey: string | null
    onRefresh: () => void
    onResolve: (task: ClocktowerNightTaskView) => void
    onSkip: (task: ClocktowerNightTaskView) => void
    onRandom: (task: ClocktowerNightTaskView) => void
    onManualTarget: (task: ClocktowerNightTaskView) => void
}) {
    return (
        <Spin spinning={loading}>
            <Space direction="vertical" size="middle" style={{width: '100%'}}>
                <Button icon={<ReloadOutlined/>} onClick={onRefresh}>刷新</Button>
                {tasks.length === 0 ? (
                    <Empty description="当前没有夜晚任务"/>
                ) : (
                    <List
                        dataSource={tasks}
                        renderItem={(task) => {
                            const seat = seats.find((item) => item.gameSeatId === task.actorGameSeatId)
                            return (
                                <List.Item>
                                    <Space direction="vertical" size="small" style={{width: '100%'}}>
                                        <Space wrap>
                                            <Typography.Text strong>
                                                #{seat?.seatNo ?? task.actorGameSeatId} {seat?.displayName ?? task.actorGameSeatId}
                                            </Typography.Text>
                                            <Tag color="purple">{task.roleCode}</Tag>
                                            <Tag>{task.taskType}</Tag>
                                            <Tag color={task.status === 'DONE' ? 'green' : 'processing'}>{task.status}</Tag>
                                            <Tag>顺序 {task.sortOrder}</Tag>
                                        </Space>
                                        <Typography.Text type="secondary">
                                            选择：{compactJson(task.choice)}
                                        </Typography.Text>
                                        <Typography.Text type="secondary">
                                            结果：{compactJson(task.result)}
                                        </Typography.Text>
                                        <Typography.Text type="secondary">
                                            元数据：{compactJson(task.metadata)}
                                        </Typography.Text>
                                        <Space wrap>
                                            <Button
                                                disabled={task.status === 'DONE' || task.status === 'SKIPPED'}
                                                icon={<CheckCircleOutlined/>}
                                                loading={actionLoadingKey === `resolve:${task.taskId}`}
                                                onClick={() => onResolve(task)}
                                                type="primary"
                                            >
                                                确认
                                            </Button>
                                            <Button
                                                disabled={task.status === 'DONE' || task.status === 'SKIPPED'}
                                                icon={<StopOutlined/>}
                                                loading={actionLoadingKey === `skip:${task.taskId}`}
                                                onClick={() => onSkip(task)}
                                            >
                                                跳过
                                            </Button>
                                            <Button
                                                disabled={task.status === 'DONE' || task.status === 'SKIPPED'}
                                                icon={<RetweetOutlined/>}
                                                loading={actionLoadingKey === `random:${task.taskId}`}
                                                onClick={() => onRandom(task)}
                                            >
                                                随机
                                            </Button>
                                            <Button
                                                disabled={task.status === 'DONE' || task.status === 'SKIPPED'}
                                                icon={<ControlOutlined/>}
                                                onClick={() => onManualTarget(task)}
                                            >
                                                手动选目标
                                            </Button>
                                        </Space>
                                    </Space>
                                </List.Item>
                            )
                        }}
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
