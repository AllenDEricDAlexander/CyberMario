import {ReloadOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Col, Empty, List, Row, Space, Tabs, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {useParams} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {
    getClocktowerPlayerView,
    streamClocktowerEvents,
    submitClocktowerPlayerAction,
} from './clocktowerService'
import type {
    ClocktowerEventResponse,
    ClocktowerGameEventResponse,
    ClocktowerGameSeatResponse,
    ClocktowerGameViewResponse,
    ClocktowerPlayerViewResponse,
    ClocktowerRoleType,
    PublicSeatResponse,
} from './clocktowerTypes'
import {ClocktowerChatPanel} from './components/ClocktowerChatPanel'
import {EventTimeline} from './components/EventTimeline'
import {RoleTypeTag} from './components/RoleTypeTag'
import {type VotePanelValues, VotePanel} from './components/VotePanel'

function GameRoomPage() {
    const {roomId} = useParams()
    const {message} = App.useApp()
    const [view, setView] = useState<ClocktowerPlayerViewResponse | null>(null)
    const [events, setEvents] = useState<ClocktowerEventResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const numericRoomId = Number(roomId)
    const lastEventSeqRef = useRef<number | undefined>(undefined)

    const loadView = useCallback(async () => {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            const response = await getClocktowerPlayerView(numericRoomId)
            setView(response)
            setEvents(response.recentEvents)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [numericRoomId])

    useEffect(() => {
        void loadView()
    }, [loadView])

    useEffect(() => {
        lastEventSeqRef.current = events.at(-1)?.seqNo
    }, [events])

    useEffect(() => {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        const controller = new AbortController()
        void streamClocktowerEvents(
            numericRoomId,
            {seatId: view?.mySeat?.seatId, lastEventSeq: lastEventSeqRef.current},
            controller.signal,
            (event) => setEvents((current) => [...current, event]),
        ).catch((caught) => {
            if (!controller.signal.aborted) {
                reportGlobalError(caught)
            }
        })
        return () => controller.abort()
    }, [numericRoomId, view?.mySeat?.seatId])

    async function submitAction(values: VotePanelValues) {
        if (!view?.mySeat || !Number.isFinite(numericRoomId)) {
            message.warning('当前视角没有绑定座位')
            return
        }
        setSubmitting(true)
        try {
            const response = await submitClocktowerPlayerAction(numericRoomId, {
                seatId: view.mySeat.seatId,
                actionType: values.actionType,
                targetSeatIds: values.targetSeatIds ?? [],
                content: values.content,
                payload: {},
                clientActionId: crypto.randomUUID(),
            })
            if (response.event) {
                setEvents((current) => [...current, response.event as ClocktowerEventResponse])
            }
            message.success(response.accepted ? '操作已提交' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSubmitting(false)
        }
    }

    const phaseLabel = useMemo(() => view ? `${view.phase.phase} · 第 ${view.phase.dayNo} 天 / 第 ${view.phase.nightNo} 夜` : '-', [view])

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadView)}>刷新</Button>}
                description={phaseLabel}
                title={view?.room.name ?? '游戏房间'}
            />
            <Row gutter={[16, 16]}>
                <Col lg={16} xs={24}>
                    <Card loading={loading} title="公共座位">
                        <SeatPublicList seats={view?.publicSeats ?? []}/>
                    </Card>
                    <Card style={{marginTop: 16}} title="公共事件">
                        <EventTimeline events={events}/>
                    </Card>
                </Col>
                <Col lg={8} xs={24}>
                    <Card title="我的身份">
                        {view?.mySeat ? (
                            <Space orientation="vertical">
                                <Typography.Text strong>{view.mySeat.displayName}</Typography.Text>
                                <Space wrap>
                                    <Tag>{view.mySeat.roleCode ?? '未知角色'}</Tag>
                                    <RoleTypeTag value={view.mySeat.roleType}/>
                                    <Tag color={view.mySeat.lifeStatus === 'ALIVE' ? 'success' : 'error'}>{view.mySeat.lifeStatus}</Tag>
                                    <Tag color={view.mySeat.hasDeadVote ? 'warning' : 'default'}>
                                        {view.mySeat.hasDeadVote ? '死票可用' : '死票已用'}
                                    </Tag>
                                </Space>
                            </Space>
                        ) : <Empty description="暂无座位身份"/>}
                    </Card>
                    <Card style={{marginTop: 16}}>
                        <Tabs
                            items={[
                                {
                                    key: 'actions',
                                    label: '操作',
                                    children: (
                                        <VotePanel
                                            actions={view?.availableActions ?? []}
                                            loading={submitting}
                                            onSubmit={submitAction}
                                            seats={view?.publicSeats ?? []}
                                        />
                                    ),
                                },
                                {
                                    key: 'private',
                                    label: '私聊',
                                    children: <PrivateThreadList threads={view?.privateThreads ?? []}/>,
                                },
                                {
                                    key: 'recent',
                                    label: '最近事件',
                                    children: <EventTimeline events={events.slice(-5)}/>,
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </>
    )
}

export function SeatPublicList({seats}: { seats: ClocktowerPlayerViewResponse['publicSeats'] }) {
    if (seats.length === 0) {
        return <Empty description="暂无座位"/>
    }
    return (
        <List
            dataSource={seats}
            renderItem={(seat) => (
                <List.Item>
                    <Space wrap>
                        <Tag>{seat.seatNo}</Tag>
                        <Typography.Text strong>{seat.displayName}</Typography.Text>
                        <Tag color={seat.connected ? 'success' : 'default'}>{seat.connected ? '在线' : '离线'}</Tag>
                        <Tag color={seat.lifeStatus === 'ALIVE' ? 'success' : 'error'}>{seat.lifeStatus}</Tag>
                        {seat.hasDeadVote ? <Tag color="warning">死票可用</Tag> : <Tag>死票已用</Tag>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

export function GameRoomSurface({
    actionControlsEnabled = true,
    roomName,
    view,
}: {
    actionControlsEnabled?: boolean
    roomName?: string
    view: ClocktowerGameViewResponse
}) {
    const {message} = App.useApp()
    const [events, setEvents] = useState<ClocktowerEventResponse[]>(() => mapGameEvents(view))
    const [submitting, setSubmitting] = useState(false)
    const publicSeats = useMemo(() => mapGamePublicSeats(view.publicSeats), [view.publicSeats])
    const viewerModeLabel = view.viewerMode === 'SPECTATOR' ? '旁观视角' : '玩家视角'

    useEffect(() => {
        setEvents(mapGameEvents(view))
    }, [view])

    async function submitAction(values: VotePanelValues) {
        if (!view.mySeat) {
            message.warning('当前视角没有绑定座位')
            return
        }
        setSubmitting(true)
        try {
            const response = await submitClocktowerPlayerAction(view.roomId, {
                seatId: view.mySeat.roomSeatId,
                actionType: values.actionType,
                targetSeatIds: values.targetSeatIds ?? [],
                content: values.content,
                payload: {},
                clientActionId: crypto.randomUUID(),
            })
            if (response.event) {
                setEvents((current) => [...current, response.event as ClocktowerEventResponse])
            }
            message.success(response.accepted ? '操作已提交' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <>
            <PageToolbar
                description={`${viewerModeLabel} · ${view.phase}`}
                title={roomName ?? `游戏 #${view.gameNo}`}
            />
            <Row gutter={[16, 16]}>
                <Col lg={16} xs={24}>
                    <Card title="公共座位">
                        <SeatPublicList seats={publicSeats}/>
                    </Card>
                    <Card style={{marginTop: 16}} title="公共事件">
                        <EventTimeline events={events}/>
                    </Card>
                </Col>
                <Col lg={8} xs={24}>
                    {view.viewerMode === 'SPECTATOR' ? (
                        <Alert
                            title="旁观视角"
                            showIcon
                            style={{marginBottom: 16}}
                            type="info"
                        />
                    ) : (
                        <Card title="我的身份">
                            <GameSeatIdentity seat={view.mySeat}/>
                        </Card>
                    )}
                    <Card style={{marginTop: view.viewerMode === 'SPECTATOR' ? 0 : 16}}>
                        <Tabs
                            items={[
                                ...(view.viewerMode === 'PLAYER' && actionControlsEnabled ? [{
                                    key: 'actions',
                                    label: '操作',
                                    children: (
                                        <Space orientation="vertical" style={{width: '100%'}}>
                                            <ActionSummary actions={view.availableActions}/>
                                            <VotePanel
                                                actions={view.availableActions}
                                                loading={submitting}
                                                onSubmit={submitAction}
                                                seats={publicSeats}
                                            />
                                        </Space>
                                    ),
                                }] : []),
                                {
                                    key: 'chat',
                                    label: '聊天',
                                    forceRender: true,
                                    children: (
                                        <ClocktowerChatPanel
                                            conversations={view.conversations}
                                            gameId={view.gameId}
                                            phase={view.phase}
                                            roomId={view.roomId}
                                            viewerMode={view.viewerMode}
                                        />
                                    ),
                                },
                                {
                                    key: 'recent',
                                    label: '最近事件',
                                    children: <EventTimeline events={events.slice(-5)}/>,
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </>
    )
}

function GameSeatIdentity({seat}: { seat?: ClocktowerGameSeatResponse | null }) {
    if (!seat) {
        return <Empty description="暂无座位身份"/>
    }
    return (
        <Space orientation="vertical">
            <Typography.Text strong>{seat.displayName}</Typography.Text>
            <Space wrap>
                <Tag>{seat.roleCode ?? '未知角色'}</Tag>
                <RoleTypeTag value={seat.roleType as ClocktowerRoleType | null}/>
                <Tag color={seat.lifeStatus === 'ALIVE' ? 'success' : 'error'}>{seat.lifeStatus}</Tag>
                <Tag color={seat.hasDeadVote ? 'warning' : 'default'}>
                    {seat.hasDeadVote ? '死票可用' : '死票已用'}
                </Tag>
            </Space>
        </Space>
    )
}

function ActionSummary({actions}: { actions: ClocktowerGameViewResponse['availableActions'] }) {
    if (actions.length === 0) {
        return <Empty description="暂无可用操作"/>
    }
    return (
        <Space wrap>
            {actions.map((action) => (
                <Tag color={action.enabled ? 'processing' : 'default'} key={action.actionType}>
                    {action.label}
                </Tag>
            ))}
        </Space>
    )
}

function mapGamePublicSeats(seats: ClocktowerGameSeatResponse[]): PublicSeatResponse[] {
    return seats.map((seat) => ({
        seatId: seat.roomSeatId,
        seatNo: seat.seatNo,
        displayName: seat.displayName,
        roleCode: null,
        lifeStatus: seat.publicLifeStatus || seat.lifeStatus,
        connected: seat.status === 'ACTIVE',
        hasDeadVote: seat.hasDeadVote,
    }))
}

function mapGameEvents(view: ClocktowerGameViewResponse): ClocktowerEventResponse[] {
    return view.events.map((event) => mapGameEvent(view.roomId, event))
}

function mapGameEvent(roomId: number, event: ClocktowerGameEventResponse): ClocktowerEventResponse {
    return {
        eventId: event.eventId,
        roomId,
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
    }
}

function PrivateThreadList({threads}: { threads: ClocktowerPlayerViewResponse['privateThreads'] }) {
    if (threads.length === 0) {
        return <Empty description="暂无私聊"/>
    }
    return (
        <List
            dataSource={threads}
            renderItem={(thread) => (
                <List.Item>
                    <Space wrap>
                        <Tag>{thread.threadId}</Tag>
                        <Typography.Text>{thread.displayName}</Typography.Text>
                        <Tag color={thread.unreadCount > 0 ? 'processing' : 'default'}>{thread.unreadCount} 未读</Tag>
                    </Space>
                </List.Item>
            )}
        />
    )
}

export const Component = GameRoomPage
