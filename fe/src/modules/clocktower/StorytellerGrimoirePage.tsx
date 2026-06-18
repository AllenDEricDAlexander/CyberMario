import {CheckOutlined, ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {
    App,
    Button,
    Card,
    Col,
    Empty,
    Form,
    Input,
    InputNumber,
    List,
    Popconfirm,
    Row,
    Select,
    Space,
    Switch,
    Tabs,
    Tag,
    Typography,
} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
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

type RulingFormValues = {
    rulingType: ClocktowerRulingCreateRequest['rulingType']
    targetSeatId?: number | null
    nominationId?: number | null
    publicLifeStatus?: ClocktowerRulingCreateRequest['publicLifeStatus']
    winner?: ClocktowerRulingCreateRequest['winner']
    reason: ClocktowerRulingCreateRequest['reason']
    note: string
    publicNote?: string | null
    visibility: ClocktowerRulingCreateRequest['visibility']
    force?: boolean
}

export const rulingTypeOptions: { label: string, value: ClocktowerRulingCreateRequest['rulingType'] }[] = [
    {label: '判死亡', value: 'MARK_DEAD'},
    {label: '复活', value: 'RESTORE_ALIVE'},
    {label: '设置公开生死', value: 'SET_PUBLIC_LIFE'},
    {label: '处决玩家', value: 'EXECUTE_PLAYER'},
    {label: '跳过处决', value: 'SKIP_EXECUTION'},
    {label: '结束游戏', value: 'END_GAME'},
    {label: '关闭提名', value: 'CLOSE_NOMINATION'},
    {label: '重开提名', value: 'REOPEN_NOMINATION'},
    {label: '作废提名', value: 'VOID_NOMINATION'},
]

const rulingReasonOptions: { label: string, value: ClocktowerRulingCreateRequest['reason'] }[] = [
    {label: '投票处决', value: 'VOTE_EXECUTION'},
    {label: '角色能力', value: 'ROLE_ABILITY'},
    {label: '夜晚死亡', value: 'NIGHT_DEATH'},
    {label: '说书人裁定', value: 'STORYTELLER_RULING'},
    {label: '玩家请求', value: 'PLAYER_REQUEST'},
    {label: '误操作修正', value: 'MISTAKE_FIX'},
    {label: '其他', value: 'OTHER'},
]

const rulingVisibilityOptions: { label: string, value: ClocktowerRulingCreateRequest['visibility'] }[] = [
    {label: '公开', value: 'PUBLIC'},
    {label: '仅说书人', value: 'STORYTELLER'},
    {label: '私密', value: 'PRIVATE'},
    {label: '审计', value: 'AUDIT'},
]

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
    const rulingMutationInFlight = useRef(false)
    const numericRoomId = Number(roomId)
    const rulingBusy = rulingLoadingKey !== null || undoingRulingId !== null

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

    async function submitRuling(request: ClocktowerRulingCreateRequest, loadingKey: string): Promise<boolean> {
        if (!Number.isFinite(numericRoomId) || rulingMutationInFlight.current) {
            return false
        }
        rulingMutationInFlight.current = true
        setRulingLoadingKey(loadingKey)
        try {
            const response = await createClocktowerRuling(numericRoomId, request)
            setGrimoire(response.grimoire)
            try {
                const rulingRows = await listClocktowerRulings(numericRoomId)
                setRulings(rulingRows)
                message.success('裁定已生效')
            } catch (caught) {
                message.warning(`裁定已生效，裁定历史刷新失败：${resolveErrorMessage(caught)}`)
            }
            return true
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
            return false
        } finally {
            rulingMutationInFlight.current = false
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
        if (!Number.isFinite(numericRoomId) || rulingMutationInFlight.current) {
            return
        }
        rulingMutationInFlight.current = true
        setUndoingRulingId(rulingId)
        try {
            const response = await undoClocktowerRuling(numericRoomId, rulingId, {note: '撤销裁定', force: false})
            setGrimoire(response.grimoire)
            try {
                const rulingRows = await listClocktowerRulings(numericRoomId)
                setRulings(rulingRows)
                message.success('裁定已撤销')
            } catch (caught) {
                message.warning(`裁定已撤销，裁定历史刷新失败：${resolveErrorMessage(caught)}`)
            }
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            rulingMutationInFlight.current = false
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
                            rulingBusy={rulingBusy}
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
                                    key: 'ruling-form',
                                    label: '裁定',
                                    children: (
                                        <RulingForm
                                            grimoire={grimoire}
                                            loading={rulingBusy}
                                            onSubmit={submitRuling}
                                        />
                                    ),
                                },
                                {
                                    key: 'rulings',
                                    label: '裁定历史',
                                    children: (
                                        <RulingHistory
                                            onUndo={undoRuling}
                                            rulingBusy={rulingBusy}
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
    rulingBusy,
    rulingLoadingKey,
}: {
    grimoire: ClocktowerGrimoireResponse | null
    onQuickRuling: (seatId: number, rulingType: QuickSeatRulingType) => Promise<void>
    rulingBusy: boolean
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
                            disabled={rulingBusy}
                            key="dead"
                            okText="确认"
                            onConfirm={voidify(() => onQuickRuling(seat.seatId, 'MARK_DEAD'))}
                            title="确认判定死亡？"
                        >
                            <Button
                                autoInsertSpace={false}
                                disabled={rulingBusy}
                                loading={rulingLoadingKey === `MARK_DEAD:${seat.seatId}`}
                                size="small"
                            >
                                判死亡
                            </Button>
                        </Popconfirm>,
                        <Popconfirm
                            cancelText="取消"
                            description="会记录为公开裁定，可在裁定历史中撤销。"
                            disabled={rulingBusy}
                            key="alive"
                            okText="确认"
                            onConfirm={voidify(() => onQuickRuling(seat.seatId, 'RESTORE_ALIVE'))}
                            title="确认复活玩家？"
                        >
                            <Button
                                autoInsertSpace={false}
                                disabled={rulingBusy}
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
    rulingBusy,
    undoingRulingId,
}: {
    rulings: ClocktowerRulingResponse[]
    onUndo: (rulingId: number) => Promise<void>
    rulingBusy: boolean
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
                        <Popconfirm
                            cancelText="取消"
                            description="会恢复此裁定前的状态；若已有后续相关裁定，后端会拒绝撤销。"
                            disabled={ruling.status === 'REVOKED' || rulingBusy}
                            key="undo"
                            okText="确认"
                            onConfirm={voidify(() => onUndo(ruling.rulingId))}
                            title="确认撤销裁定？"
                        >
                            <Button
                                autoInsertSpace={false}
                                disabled={ruling.status === 'REVOKED' || rulingBusy}
                                loading={undoingRulingId === ruling.rulingId}
                                size="small"
                                type="link"
                            >
                                撤销
                            </Button>
                        </Popconfirm>,
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

export function RulingForm({
    grimoire,
    loading,
    onSubmit,
}: {
    grimoire: ClocktowerGrimoireResponse | null
    loading: boolean
    onSubmit: (request: ClocktowerRulingCreateRequest, loadingKey: string) => Promise<boolean>
}) {
    const [form] = Form.useForm<RulingFormValues>()

    async function submit() {
        const values = await form.validateFields()
        const success = await onSubmit({
            rulingType: values.rulingType,
            targetSeatId: values.targetSeatId ?? null,
            nominationId: values.nominationId ?? null,
            publicLifeStatus: values.publicLifeStatus ?? null,
            winner: values.winner ?? null,
            reason: values.reason,
            note: values.note.trim(),
            publicNote: values.publicNote?.trim() || null,
            visibility: values.visibility,
            force: values.force ?? false,
        }, `FORM:${values.rulingType}`)
        if (success) {
            form.resetFields(['note', 'publicNote'])
        }
    }

    return (
        <Form
            form={form}
            initialValues={{
                rulingType: 'SET_PUBLIC_LIFE',
                publicLifeStatus: 'DEAD',
                reason: 'STORYTELLER_RULING',
                visibility: 'PUBLIC',
                force: false,
            }}
            layout="vertical"
        >
            <Form.Item label="裁定类型" name="rulingType" rules={[{required: true, message: '请选择裁定类型'}]}>
                <Select options={rulingTypeOptions}/>
            </Form.Item>
            <Form.Item label="目标座位" name="targetSeatId">
                <Select
                    allowClear
                    options={(grimoire?.seats ?? []).map((seat) => ({
                        label: `${seat.seatNo} ${seat.displayName}`,
                        value: seat.seatId,
                    }))}
                    placeholder="可选"
                />
            </Form.Item>
            <Form.Item label="提名 ID" name="nominationId">
                <InputNumber min={1} style={{width: '100%'}}/>
            </Form.Item>
            <Form.Item label="公开生死" name="publicLifeStatus">
                <Select
                    allowClear
                    options={[
                        {label: '公开存活', value: 'ALIVE'},
                        {label: '公开死亡', value: 'DEAD'},
                    ]}
                    placeholder="设置公开生死时填写"
                />
            </Form.Item>
            <Form.Item label="获胜阵营" name="winner">
                <Select
                    allowClear
                    options={[
                        {label: '善良', value: 'GOOD'},
                        {label: '邪恶', value: 'EVIL'},
                    ]}
                    placeholder="结束游戏时填写"
                />
            </Form.Item>
            <Form.Item label="裁定原因" name="reason" rules={[{required: true, message: '请选择裁定原因'}]}>
                <Select options={rulingReasonOptions}/>
            </Form.Item>
            <Form.Item label="可见范围" name="visibility" rules={[{required: true, message: '请选择可见范围'}]}>
                <Select options={rulingVisibilityOptions}/>
            </Form.Item>
            <Form.Item label="内部记录" name="note" rules={[{required: true, message: '请填写内部记录'}]}>
                <Input.TextArea rows={3}/>
            </Form.Item>
            <Form.Item label="公开记录" name="publicNote">
                <Input.TextArea rows={2}/>
            </Form.Item>
            <Form.Item label="强制裁定" name="force" valuePropName="checked">
                <Switch/>
            </Form.Item>
            <Button icon={<SendOutlined/>} loading={loading} onClick={voidify(submit)} type="primary">
                提交裁定
            </Button>
        </Form>
    )
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
