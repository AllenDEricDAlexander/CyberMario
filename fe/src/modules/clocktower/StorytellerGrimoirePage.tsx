import {CheckOutlined, ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {App, Button, Card, Col, Empty, Form, Input, List, Popconfirm, Row, Select, Space, Tabs, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {useParams} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    createClocktowerRuling,
    getClocktowerGrimoire,
    getClocktowerNightChecklist,
    listClocktowerRulings,
    submitClocktowerStorytellerAction,
    undoClocktowerRuling,
} from './clocktowerService'
import type {
    ClocktowerGrimoireResponse,
    ClocktowerNightChecklistResponse,
    ClocktowerRulingCreateRequest,
    ClocktowerRulingResponse,
    ClocktowerStorytellerActionRequest,
} from './clocktowerTypes'
import {EventTimeline} from './components/EventTimeline'
import {NightChecklist} from './components/NightChecklist'
import {RoleTypeTag} from './components/RoleTypeTag'

type QuickSeatRulingType = Extract<ClocktowerRulingCreateRequest['rulingType'], 'MARK_DEAD' | 'RESTORE_ALIVE'>

function StorytellerGrimoirePage() {
    const {roomId} = useParams()
    const {message} = App.useApp()
    const [form] = Form.useForm<ClocktowerStorytellerActionRequest>()
    const [grimoire, setGrimoire] = useState<ClocktowerGrimoireResponse | null>(null)
    const [checklist, setChecklist] = useState<ClocktowerNightChecklistResponse | null>(null)
    const [rulings, setRulings] = useState<ClocktowerRulingResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [resolvingTaskId, setResolvingTaskId] = useState<number | null>(null)
    const [rulingLoadingKey, setRulingLoadingKey] = useState<string | null>(null)
    const [undoingRulingId, setUndoingRulingId] = useState<number | null>(null)
    const numericRoomId = Number(roomId)

    const load = useCallback(async () => {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            const [grimoireResponse, checklistResponse, rulingRows] = await Promise.all([
                getClocktowerGrimoire(numericRoomId),
                getClocktowerNightChecklist(numericRoomId),
                listClocktowerRulings(numericRoomId),
            ])
            setGrimoire(grimoireResponse)
            setChecklist(checklistResponse)
            setRulings(rulingRows)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }, [message, numericRoomId])

    useEffect(() => {
        void load()
    }, [load])

    async function submitAction() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        const values = await form.validateFields()
        setSubmitting(true)
        try {
            const response = await submitClocktowerStorytellerAction(numericRoomId, {
                ...values,
                targetSeatIds: values.targetSeatIds ?? [],
                payload: {},
            })
            setGrimoire(response.grimoire)
            message.success(response.accepted ? '说书人操作已提交' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setSubmitting(false)
        }
    }

    async function resolveTask(taskId: number) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setResolvingTaskId(taskId)
        try {
            const response = await submitClocktowerStorytellerAction(numericRoomId, {
                actionType: 'RESOLVE_TASK',
                targetSeatIds: [],
                note: null,
                payload: {taskId},
            })
            const checklistResponse = await getClocktowerNightChecklist(numericRoomId)
            setGrimoire(response.grimoire)
            setChecklist(checklistResponse)
            message.success(response.accepted ? '任务已处理' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setResolvingTaskId(null)
        }
    }

    async function submitRuling(request: ClocktowerRulingCreateRequest, loadingKey: string) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setRulingLoadingKey(loadingKey)
        try {
            const response = await createClocktowerRuling(numericRoomId, request)
            const rulingRows = await listClocktowerRulings(numericRoomId)
            setGrimoire(response.grimoire)
            setRulings(rulingRows)
            message.success('裁定已生效')
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setRulingLoadingKey(null)
        }
    }

    async function quickSeatRuling(seatId: number, rulingType: QuickSeatRulingType) {
        await submitRuling({
            rulingType,
            targetSeatId: seatId,
            reason: rulingType === 'MARK_DEAD' ? 'NIGHT_DEATH' : 'ROLE_ABILITY',
            note: rulingType === 'MARK_DEAD' ? '判死亡' : '复活',
            publicNote: rulingType === 'MARK_DEAD' ? '一名玩家死亡' : '一名玩家复活',
            visibility: 'PUBLIC',
            force: false,
        }, `${rulingType}:${seatId}`)
    }

    async function undoRuling(rulingId: number) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setUndoingRulingId(rulingId)
        try {
            const response = await undoClocktowerRuling(numericRoomId, rulingId, {note: '撤销裁定', force: true})
            const rulingRows = await listClocktowerRulings(numericRoomId)
            setGrimoire(response.grimoire)
            setRulings(rulingRows)
            message.success('裁定已撤销')
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setUndoingRulingId(null)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>}
                description={grimoire ? `${grimoire.phase.phase} · 第 ${grimoire.phase.dayNo} 天 / 第 ${grimoire.phase.nightNo} 夜` : '管理身份、标记、夜晚顺序和说书人裁定。'}
                title="说书人魔典"
            />
            <Row gutter={[16, 16]}>
                <Col lg={15} xs={24}>
                    <Card loading={loading} title="魔典座位">
                        <GrimoireSeatList
                            grimoire={grimoire}
                            onQuickRuling={quickSeatRuling}
                            rulingLoadingKey={rulingLoadingKey}
                        />
                    </Card>
                </Col>
                <Col lg={9} xs={24}>
                    <Card>
                        <Tabs
                            items={[
                                {
                                    key: 'tasks',
                                    label: '待处理任务',
                                    children: (
                                        <TaskList
                                            grimoire={grimoire}
                                            onResolve={resolveTask}
                                            resolvingTaskId={resolvingTaskId}
                                        />
                                    ),
                                },
                                {
                                    key: 'night',
                                    label: '夜晚顺序',
                                    children: <NightChecklist checklist={checklist} loading={loading}/>,
                                },
                                {
                                    key: 'rulings',
                                    label: '裁定历史',
                                    children: (
                                        <RulingHistory
                                            onUndo={undoRuling}
                                            rulings={rulings}
                                            undoingRulingId={undoingRulingId}
                                        />
                                    ),
                                },
                                {
                                    key: 'action',
                                    label: '说书人动作',
                                    children: (
                                        <StorytellerActionForm
                                            form={form}
                                            grimoire={grimoire}
                                            loading={submitting}
                                            onSubmit={submitAction}
                                        />
                                    ),
                                },
                                {
                                    key: 'events',
                                    label: '事件',
                                    children: <EventTimeline events={[]}/>,
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </>
    )
}

export function GrimoireSeatList({
    grimoire,
    onQuickRuling,
    rulingLoadingKey,
}: {
    grimoire: ClocktowerGrimoireResponse | null
    onQuickRuling: (seatId: number, rulingType: QuickSeatRulingType) => Promise<void>
    rulingLoadingKey: string | null
}) {
    if (!grimoire || grimoire.seats.length === 0) {
        return <Empty description="暂无座位"/>
    }
    return (
        <List
            dataSource={grimoire.seats}
            renderItem={(seat) => (
                <List.Item
                    actions={[
                        <Popconfirm
                            cancelText="取消"
                            description="会记录为公开裁定，可在裁定历史中撤销。"
                            key="dead"
                            okText="确认"
                            onConfirm={voidify(() => onQuickRuling(seat.seatId, 'MARK_DEAD'))}
                            title="确认判定死亡？"
                        >
                            <Button
                                autoInsertSpace={false}
                                loading={rulingLoadingKey === `MARK_DEAD:${seat.seatId}`}
                                size="small"
                            >
                                判死亡
                            </Button>
                        </Popconfirm>,
                        <Popconfirm
                            cancelText="取消"
                            description="会记录为公开裁定，可在裁定历史中撤销。"
                            key="alive"
                            okText="确认"
                            onConfirm={voidify(() => onQuickRuling(seat.seatId, 'RESTORE_ALIVE'))}
                            title="确认复活玩家？"
                        >
                            <Button
                                autoInsertSpace={false}
                                loading={rulingLoadingKey === `RESTORE_ALIVE:${seat.seatId}`}
                                size="small"
                            >
                                复活
                            </Button>
                        </Popconfirm>,
                    ]}
                >
                    <Space wrap>
                        <Tag>{seat.seatNo}</Tag>
                        <Typography.Text strong>{seat.displayName}</Typography.Text>
                        <Tag>{seat.roleCode ?? '未分配'}</Tag>
                        <RoleTypeTag value={seat.roleType}/>
                        <Tag color={seat.alive ? 'success' : 'error'}>{seat.alive ? '真实存活' : '真实死亡'}</Tag>
                        <Tag color={seat.publicAlive ? 'success' : 'error'}>
                            {seat.publicAlive ? '公开存活' : '公开死亡'}
                        </Tag>
                        <Tag color={seat.hasDeadVote ? 'warning' : 'default'}>
                            {seat.hasDeadVote ? '死票可用' : '死票已用'}
                        </Tag>
                        {seat.notes && <Typography.Text type="secondary">{seat.notes}</Typography.Text>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

export function RulingHistory({
    rulings,
    onUndo,
    undoingRulingId,
}: {
    rulings: ClocktowerRulingResponse[]
    onUndo: (rulingId: number) => Promise<void>
    undoingRulingId: number | null
}) {
    if (rulings.length === 0) {
        return <Empty description="暂无裁定"/>
    }
    return (
        <List
            dataSource={rulings}
            renderItem={(ruling) => (
                <List.Item
                    actions={[
                        <Button
                            autoInsertSpace={false}
                            disabled={ruling.status === 'REVOKED'}
                            key="undo"
                            loading={undoingRulingId === ruling.rulingId}
                            onClick={voidify(() => onUndo(ruling.rulingId))}
                            size="small"
                            type="link"
                        >
                            撤销
                        </Button>,
                    ]}
                >
                    <Space wrap>
                        <Tag>{ruling.rulingType}</Tag>
                        <Tag color={ruling.status === 'APPLIED' ? 'processing' : 'default'}>{ruling.status}</Tag>
                        <Tag>{ruling.reason}</Tag>
                        {ruling.targetSeatId != null && <Typography.Text>座位 {ruling.targetSeatId}</Typography.Text>}
                        <Typography.Text type="secondary">{ruling.publicNote ?? ruling.note}</Typography.Text>
                    </Space>
                </List.Item>
            )}
        />
    )
}

export function TaskList({
                             grimoire,
                             onResolve,
                             resolvingTaskId,
                         }: {
    grimoire: ClocktowerGrimoireResponse | null
    onResolve: (taskId: number) => Promise<void>
    resolvingTaskId: number | null
}) {
    if (!grimoire || grimoire.pendingTasks.length === 0) {
        return <Empty description="暂无待处理任务"/>
    }
    return (
        <List
            dataSource={grimoire.pendingTasks}
            renderItem={(task) => (
                <List.Item
                    actions={[
                        <Button
                            disabled={task.status !== 'PENDING'}
                            icon={<CheckOutlined/>}
                            key="resolve"
                            loading={resolvingTaskId === task.taskId}
                            onClick={voidify(() => onResolve(task.taskId))}
                            size="small"
                            type="link"
                        >
                            完成
                        </Button>,
                    ]}
                >
                    <Space wrap>
                        <Tag>{taskTypeText(task.taskType)}</Tag>
                        <Tag color={task.status === 'DONE' ? 'success' : 'warning'}>{taskStatusText(task.status)}</Tag>
                        <Typography.Text>{task.roleCode ?? '-'}</Typography.Text>
                        {task.note && <Typography.Text type="secondary">{task.note}</Typography.Text>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

function taskTypeText(taskType: string) {
    return taskType === 'WAKE_ROLE' ? '唤醒角色' : taskType
}

function taskStatusText(status: string) {
    if (status === 'PENDING') {
        return '待处理'
    }
    return status === 'DONE' ? '已完成' : status
}

function StorytellerActionForm({
                                   form,
                                   grimoire,
                                   loading,
                                   onSubmit,
                               }: {
    form: ReturnType<typeof Form.useForm<ClocktowerStorytellerActionRequest>>[0]
    grimoire: ClocktowerGrimoireResponse | null
    loading: boolean
    onSubmit: () => Promise<void>
}) {
    return (
        <Form form={form} layout="vertical">
            <Form.Item label="动作" name="actionType" rules={[{required: true, message: '请选择动作'}]}>
                <Select options={[
                    {label: '阶段推进', value: 'ADVANCE_PHASE'},
                    {label: '说书人裁定', value: 'STORYTELLER_RULING'},
                    {label: '添加标记', value: 'ADD_MARKER'},
                    {label: '移除标记', value: 'REMOVE_MARKER'},
                ]}/>
            </Form.Item>
            <Form.Item label="目标座位" name="targetSeatIds">
                <Select
                    mode="multiple"
                    options={(grimoire?.seats ?? []).map((seat) => ({
                        label: `${seat.seatNo} ${seat.displayName}`,
                        value: seat.seatId,
                    }))}
                    placeholder="可选"
                />
            </Form.Item>
            <Form.Item label="备注" name="note">
                <Input.TextArea rows={3}/>
            </Form.Item>
            <Button icon={<SendOutlined/>} loading={loading} onClick={voidify(onSubmit)} type="primary">
                提交说书人操作
            </Button>
        </Form>
    )
}

export const Component = StorytellerGrimoirePage
