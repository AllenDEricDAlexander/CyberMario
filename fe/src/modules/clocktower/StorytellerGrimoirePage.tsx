import {CheckOutlined, ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {
    App,
    Button,
    Card,
    Col,
    Empty,
    Flex,
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
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    advanceClocktowerFlow,
    confirmClocktowerExecution,
    createClocktowerRuling,
    getClocktowerFlow,
    getClocktowerGrimoire,
    getClocktowerNightChecklist,
    listClocktowerRulings,
    submitClocktowerStorytellerAction,
    undoClocktowerRuling,
} from './clocktowerService'
import type {
    ClocktowerEventResponse,
    ClocktowerExecutionDeathPolicy,
    ClocktowerFlowResponse,
    ClocktowerGameSeatResponse,
    ClocktowerGameViewResponse,
    ClocktowerGrimoireResponse,
    ClocktowerNightChecklistResponse,
    ClocktowerRoleType,
    ClocktowerRulingCreateRequest,
    ClocktowerRulingResponse,
    ClocktowerStorytellerActionRequest,
} from './clocktowerTypes'
import {ClocktowerChatPanel} from './components/ClocktowerChatPanel'
import {EventTimeline} from './components/EventTimeline'
import {NightChecklist} from './components/NightChecklist'
import {RoleTypeTag} from './components/RoleTypeTag'
import {StorytellerAgentPanel} from './components/StorytellerAgentPanel'
import {StorytellerMicControlPanel} from './components/StorytellerMicControlPanel'
import {StorytellerNightTaskPanel} from './components/StorytellerNightTaskPanel'

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
    const [flow, setFlow] = useState<ClocktowerFlowResponse | null>(null)
    const [rulings, setRulings] = useState<ClocktowerRulingResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [flowLoading, setFlowLoading] = useState(false)
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
            const [grimoireResponse, checklistResponse, flowResponse, rulingRows] = await Promise.all([
                getClocktowerGrimoire(numericRoomId),
                getClocktowerNightChecklist(numericRoomId),
                getClocktowerFlow(numericRoomId),
                listClocktowerRulings(numericRoomId),
            ])
            setGrimoire(grimoireResponse)
            setChecklist(checklistResponse)
            setFlow(flowResponse)
            setRulings(rulingRows)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [numericRoomId])

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
            reportGlobalError(caught)
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
            const [checklistResponse, flowResponse] = await Promise.all([
                getClocktowerNightChecklist(numericRoomId),
                getClocktowerFlow(numericRoomId),
            ])
            setGrimoire(response.grimoire)
            setChecklist(checklistResponse)
            setFlow(flowResponse)
            message.success(response.accepted ? '任务已处理' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setResolvingTaskId(null)
        }
    }

    async function advanceFlow() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setFlowLoading(true)
        try {
            const response = await advanceClocktowerFlow(numericRoomId)
            setFlow(response)
            await load()
            message.success('流程已推进')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setFlowLoading(false)
        }
    }

    async function confirmExecution(deathPolicy: ClocktowerExecutionDeathPolicy, note: string) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setFlowLoading(true)
        try {
            const response = await confirmClocktowerExecution(numericRoomId, {execute: true, deathPolicy, note})
            setFlow(response)
            await load()
            message.success('处决已结算')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setFlowLoading(false)
        }
    }

    async function confirmNoExecution(note: string) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setFlowLoading(true)
        try {
            const response = await confirmClocktowerExecution(numericRoomId, {
                execute: false,
                deathPolicy: 'NO_CHANGE',
                note,
            })
            setFlow(response)
            await load()
            message.success('无人处决已确认')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setFlowLoading(false)
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
                const [rulingRows, flowResponse] = await Promise.all([
                    listClocktowerRulings(numericRoomId),
                    getClocktowerFlow(numericRoomId),
                ])
                setRulings(rulingRows)
                setFlow(flowResponse)
                message.success('裁定已生效')
            } catch (caught) {
                message.warning(`裁定已生效，裁定历史刷新失败：${resolveErrorMessage(caught)}`)
            }
            return true
        } catch (caught) {
            reportGlobalError(caught)
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
                const [rulingRows, flowResponse] = await Promise.all([
                    listClocktowerRulings(numericRoomId),
                    getClocktowerFlow(numericRoomId),
                ])
                setRulings(rulingRows)
                setFlow(flowResponse)
                message.success('裁定已撤销')
            } catch (caught) {
                message.warning(`裁定已撤销，裁定历史刷新失败：${resolveErrorMessage(caught)}`)
            }
        } catch (caught) {
            reportGlobalError(caught)
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
                                    key: 'flow',
                                    label: '流程',
                                    children: (
                                        <FlowPanel
                                            flow={flow}
                                            loading={flowLoading}
                                            onAdvance={advanceFlow}
                                            onConfirmExecution={confirmExecution}
                                            onConfirmNoExecution={confirmNoExecution}
                                        />
                                    ),
                                },
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

export function FlowPanel({
    flow,
    loading,
    onAdvance,
    onConfirmExecution,
    onConfirmNoExecution,
}: {
    flow: ClocktowerFlowResponse | null
    loading: boolean
    onAdvance: () => Promise<void>
    onConfirmExecution: (deathPolicy: ClocktowerExecutionDeathPolicy, note: string) => Promise<void>
    onConfirmNoExecution: (note: string) => Promise<void>
}) {
    const [executionNote, setExecutionNote] = useState('')
    useEffect(() => {
        setExecutionNote('')
    }, [
        flow?.phase.phase,
        flow?.phase.dayNo,
        flow?.phase.nightNo,
        flow?.executionCandidate?.nominationId,
        flow?.executionCandidate?.resolved,
    ])
    if (!flow) {
        return <Empty description="暂无流程信息"/>
    }
    const canSubmitExecution = executionNote.trim().length > 0
    return (
        <Flex gap="middle" style={{width: '100%'}} vertical>
            <Space wrap>
                <Typography.Text strong>流程</Typography.Text>
                <Tag color="blue">{phaseText(flow.phase.phase)}</Tag>
                <Typography.Text type="secondary">
                    第 {flow.phase.dayNo} 天 / 第 {flow.phase.nightNo} 夜
                </Typography.Text>
                <Tag color={flow.advanceAllowed ? 'success' : 'warning'}>
                    {flow.advanceAllowed ? '可推进' : '待处理'}
                </Tag>
            </Space>
            <Space wrap>
                <Tag>夜晚任务 {flow.nightTaskSummary.total}</Tag>
                <Tag color={flow.nightTaskSummary.pending > 0 ? 'warning' : 'success'}>
                    待处理 {flow.nightTaskSummary.pending}
                </Tag>
                <Tag color="success">完成 {flow.nightTaskSummary.done}</Tag>
                <Tag>跳过 {flow.nightTaskSummary.skipped}</Tag>
            </Space>
            {flow.openNomination && (
                <Space wrap>
                    <Tag color="processing">提名进行中</Tag>
                    <Typography.Text type="secondary">
                        提名 {flow.openNomination.nominationId} · 当前 {flow.openNomination.voteCount} 票
                    </Typography.Text>
                </Space>
            )}
            {flow.blockingReasons.map((reason) => (
                <Tag color="warning" key={reason}>{flowBlockingText(reason)}</Tag>
            ))}
            {flow.executionCandidate && (
                <>
                    <Space wrap>
                        <Typography.Text strong>处决结算</Typography.Text>
                        <Tag color={flow.executionCandidate.executable ? 'processing' : 'default'}>
                            {flow.executionCandidate.executable ? '有处决候选' : '无人处决'}
                        </Tag>
                        <Typography.Text type="secondary">
                            票数 {flow.executionCandidate.voteCount} / 门槛 {flow.executionCandidate.threshold}
                        </Typography.Text>
                    </Space>
                    {flow.phase.phase === 'EXECUTION' && !flow.executionCandidate.resolved && (
                        <Flex gap="small" style={{width: '100%'}} vertical>
                            <Input.TextArea
                                autoSize={{minRows: 2, maxRows: 4}}
                                onChange={(event) => setExecutionNote(event.target.value)}
                                placeholder="结算原因"
                                value={executionNote}
                            />
                            {flow.executionCandidate.executable ? (
                                <Space wrap>
                                    <Button
                                        disabled={!canSubmitExecution}
                                        loading={loading}
                                        onClick={voidify(() => onConfirmExecution('NO_CHANGE', executionNote.trim()))}
                                    >
                                        确认处决但不死亡
                                    </Button>
                                    <Button
                                        danger
                                        disabled={!canSubmitExecution}
                                        loading={loading}
                                        onClick={voidify(() => onConfirmExecution('MARK_DEAD', executionNote.trim()))}
                                    >
                                        确认处决并标记死亡
                                    </Button>
                                </Space>
                            ) : (
                                <Button
                                    disabled={!canSubmitExecution}
                                    loading={loading}
                                    onClick={voidify(() => onConfirmNoExecution(executionNote.trim()))}
                                >
                                    确认无人处决
                                </Button>
                            )}
                        </Flex>
                    )}
                </>
            )}
            {flow.victoryCandidate && (
                <Tag color="error">胜负建议：{flow.victoryCandidate.winner}</Tag>
            )}
            <Button disabled={!flow.advanceAllowed} loading={loading} onClick={voidify(onAdvance)} type="primary">
                {transitionText(flow.nextTransition)}
            </Button>
        </Flex>
    )
}

function phaseText(phase: string) {
    if (phase === 'FIRST_NIGHT') {
        return '首夜'
    }
    if (phase === 'DAY') {
        return '白天'
    }
    if (phase === 'NOMINATION') {
        return '提名'
    }
    if (phase === 'EXECUTION') {
        return '处决'
    }
    if (phase === 'NIGHT') {
        return '夜晚'
    }
    return phase
}

function flowBlockingText(reason: string) {
    if (reason === 'CLOCKTOWER_NIGHT_TASKS_PENDING') {
        return '夜晚任务未完成'
    }
    if (reason === 'CLOCKTOWER_OPEN_NOMINATION_EXISTS') {
        return '仍有提名未关闭'
    }
    if (reason === 'CLOCKTOWER_EXECUTION_NOT_RESOLVED') {
        return '处决尚未结算'
    }
    if (reason === 'CLOCKTOWER_GAME_ALREADY_ENDED') {
        return '游戏已结束'
    }
    return reason
}

function transitionText(transition: string) {
    if (transition === 'COMPLETE_FIRST_NIGHT') {
        return '进入第一天'
    }
    if (transition === 'START_NOMINATION') {
        return '进入提名阶段'
    }
    if (transition === 'START_EXECUTION') {
        return '进入处决阶段'
    }
    if (transition === 'START_NIGHT') {
        return '进入下一夜'
    }
    if (transition === 'COMPLETE_NIGHT') {
        return '进入下一天'
    }
    return '无可推进流程'
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

export function StorytellerGameSurface({
    roomName,
    view,
}: {
    roomName?: string
    view: ClocktowerGameViewResponse
}) {
    return (
        <>
            <PageToolbar
                description={`说书人视角 · ${view.phase}`}
                title="说书人魔典"
            />
            <Row gutter={[16, 16]}>
                <Col lg={15} xs={24}>
                    <Card title={roomName ? `${roomName} · 魔典座位` : '魔典座位'}>
                        <StorytellerGameSeatList seats={view.grimoire}/>
                    </Card>
                    <Card style={{marginTop: 16}} title="公开事件">
                        <EventTimeline events={view.events.map((event) => ({
                            eventId: event.eventId,
                            roomId: view.roomId,
                            seqNo: event.eventSeq,
                            eventType: event.eventType as ClocktowerEventResponse['eventType'],
                            phase: event.phase as ClocktowerEventResponse['phase'],
                            dayNo: event.dayNo,
                            nightNo: event.nightNo,
                            actorSeatId: event.actorGameSeatId ?? null,
                            targetSeatId: event.targetGameSeatId ?? null,
                            visibility: event.visibility as ClocktowerEventResponse['visibility'],
                            visibleSeatIds: event.visibleGameSeatIds,
                            payload: event.payload,
                            createdAt: event.occurredAt,
                        }))}/>
                    </Card>
                </Col>
                <Col lg={9} xs={24}>
                    <Card>
                        <Tabs
                            items={[
                                {
                                    key: 'agents',
                                    label: 'Agent',
                                    children: <StorytellerAgentPanel gameId={view.gameId}/>,
                                },
                                {
                                    key: 'mic',
                                    label: '麦序',
                                    children: <StorytellerMicControlPanel gameId={view.gameId}/>,
                                },
                                {
                                    key: 'night-tasks',
                                    label: '夜晚任务',
                                    children: <StorytellerNightTaskPanel gameId={view.gameId} seats={view.grimoire}/>,
                                },
                                {
                                    key: 'flow',
                                    label: '流程',
                                    children: (
                                        <Space orientation="vertical">
                                            <Typography.Text strong>当前阶段</Typography.Text>
                                            <Tag color="blue">{phaseText(view.phase)}</Tag>
                                            <Typography.Text type="secondary">
                                                当前游戏视图展示流程状态、事件和聊天监控。
                                            </Typography.Text>
                                        </Space>
                                    ),
                                },
                                {
                                    key: 'rulings',
                                    label: '裁定',
                                    children: (
                                        <Space orientation="vertical">
                                            <Typography.Text strong>裁定入口</Typography.Text>
                                            <Typography.Text type="secondary">
                                                裁定写入入口迁移完成前暂不在游戏视图开放。
                                            </Typography.Text>
                                        </Space>
                                    ),
                                },
                                {
                                    key: 'chat',
                                    label: '聊天监控',
                                    forceRender: true,
                                    children: (
                                        <ClocktowerChatPanel
                                            conversations={view.conversations}
                                            gameId={view.gameId}
                                            phase={view.phase}
                                            roomId={view.roomId}
                                            title="聊天监控"
                                            viewerMode="STORYTELLER"
                                        />
                                    ),
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </>
    )
}

function StorytellerGameSeatList({seats}: { seats: ClocktowerGameSeatResponse[] }) {
    if (seats.length === 0) {
        return <Empty description="暂无魔典座位"/>
    }
    return (
        <List
            dataSource={seats}
            renderItem={(seat) => (
                <List.Item>
                    <Space wrap>
                        <Tag>{seat.seatNo}</Tag>
                        <Typography.Text strong>{seat.displayName}</Typography.Text>
                        <Tag>{seat.roleCode ?? '未分配'}</Tag>
                        <RoleTypeTag value={seat.roleType as ClocktowerRoleType | null}/>
                        <Tag color={seat.alignment === 'EVIL' ? 'error' : 'success'}>{seat.alignment ?? '未知阵营'}</Tag>
                        <Tag color={seat.lifeStatus === 'ALIVE' ? 'success' : 'error'}>{seat.lifeStatus}</Tag>
                        <Tag color={seat.publicLifeStatus === 'ALIVE' ? 'success' : 'error'}>
                            公开 {seat.publicLifeStatus}
                        </Tag>
                        <Tag color={seat.hasDeadVote ? 'warning' : 'default'}>
                            {seat.hasDeadVote ? '死票可用' : '死票已用'}
                        </Tag>
                    </Space>
                </List.Item>
            )}
            rowKey="gameSeatId"
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
