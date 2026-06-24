import {
    HeartOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
    SendOutlined,
    TeamOutlined,
} from '@ant-design/icons'
import {Alert, App, Badge, Button, Card, Empty, Flex, Space, Tabs, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {useAuth} from '../auth/authStore'
import {
    claimClocktowerSeat,
    createClocktowerInvitation,
    enterClocktowerRoom,
    heartbeatClocktowerRoom,
    kickClocktowerRoomMember,
    startClocktowerGame,
} from './clocktowerService'
import type {
    ClocktowerRoomInvitationCreateRequest,
    ClocktowerRoomMemberActionRequest,
    ClocktowerRoomResponse,
} from './clocktowerTypes'
import {ClocktowerInvitationDrawer} from './components/ClocktowerInvitationDrawer'
import {ClocktowerMemberDrawer} from './components/ClocktowerMemberDrawer'
import {ClocktowerSeatGrid} from './components/ClocktowerSeatGrid'

type LoadClocktowerRoomForLobbyOptions = {
    roomId: number
    enterRoom?: typeof enterClocktowerRoom
    onLoadingChange: (loading: boolean) => void
    onRoomLoaded: (room: ClocktowerRoomResponse) => void
    onError?: (caught: unknown) => void
}

function RoomLobbyPage() {
    const {roomId} = useParams()
    const navigate = useNavigate()
    const {message} = App.useApp()
    const auth = useAuth()
    const [room, setRoom] = useState<ClocktowerRoomResponse | null>(null)
    const [loading, setLoading] = useState(false)
    const [heartbeating, setHeartbeating] = useState(false)
    const [lastHeartbeatAt, setLastHeartbeatAt] = useState<string | null>(null)
    const [claimingSeatNo, setClaimingSeatNo] = useState<number | null>(null)
    const [starting, setStarting] = useState(false)
    const [invitationOpen, setInvitationOpen] = useState(false)
    const [memberOpen, setMemberOpen] = useState(false)
    const [creatingInvitation, setCreatingInvitation] = useState(false)
    const [memberActionUserId, setMemberActionUserId] = useState<number | null>(null)
    const numericRoomId = Number(roomId)
    const currentUserId = auth.user?.id ?? null
    const canManageRoom = canManageClocktowerRoom(room, currentUserId)

    const loadRoom = useCallback(async () => {
        await loadClocktowerRoomForLobby({
            roomId: numericRoomId,
            onLoadingChange: setLoading,
            onRoomLoaded: setRoom,
        })
    }, [numericRoomId])

    useEffect(() => {
        void loadRoom()
    }, [loadRoom])

    async function sendHeartbeat() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setHeartbeating(true)
        try {
            await heartbeatClocktowerRoom(numericRoomId)
            setLastHeartbeatAt(new Date().toLocaleTimeString())
            message.success('心跳已发送')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setHeartbeating(false)
        }
    }

    async function claimSeat(seatNo: number) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setClaimingSeatNo(seatNo)
        try {
            await claimClocktowerSeat(numericRoomId, seatNo)
            message.success('座位已认领')
            await loadRoom()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setClaimingSeatNo(null)
        }
    }

    async function saveInvitation(request: ClocktowerRoomInvitationCreateRequest) {
        if (!canManageRoom || !Number.isFinite(numericRoomId)) {
            return
        }
        setCreatingInvitation(true)
        try {
            await createClocktowerInvitation(numericRoomId, request)
            message.success('邀请已创建')
            setInvitationOpen(false)
            await loadRoom()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setCreatingInvitation(false)
        }
    }

    async function kickMember(userId: number, ban: boolean) {
        if (!canManageRoom || !Number.isFinite(numericRoomId)) {
            return
        }
        const request: ClocktowerRoomMemberActionRequest = {
            ban,
            banDurationSeconds: ban ? 3600 : null,
            reason: ban ? '房间管理移出并封禁' : '房间管理移出',
        }
        setMemberActionUserId(userId)
        try {
            await kickClocktowerRoomMember(numericRoomId, userId, request)
            message.success(ban ? '成员已移出并封禁' : '成员已移出')
            await loadRoom()
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setMemberActionUserId(null)
        }
    }

    async function startGame() {
        if (!canManageRoom || !room || !Number.isFinite(numericRoomId) || !canStartClocktowerRoom(room)) {
            return
        }
        setStarting(true)
        try {
            await startClocktowerGame(numericRoomId)
            message.success('游戏已开始')
            void navigate(clocktowerRoomPlayPath(numericRoomId))
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setStarting(false)
        }
    }

    const startDisabled = !room || !canStartClocktowerRoom(room)

    return (
        <>
            <PageToolbar
                actions={
                    <Space wrap>
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadRoom)}>刷新</Button>
                        <Button
                            icon={<HeartOutlined/>}
                            loading={heartbeating}
                            onClick={voidify(sendHeartbeat)}
                        >
                            心跳
                        </Button>
                        <Tag color={lastHeartbeatAt ? 'success' : 'default'}>
                            {lastHeartbeatAt ? `心跳 ${lastHeartbeatAt}` : '心跳待发送'}
                        </Tag>
                        <RoomLobbyManagementActions
                            currentUserId={currentUserId}
                            onOpenInvitation={() => setInvitationOpen(true)}
                            onOpenMembers={() => setMemberOpen(true)}
                            onStartGame={() => void startGame()}
                            room={room}
                            startDisabled={startDisabled}
                            starting={starting}
                        />
                    </Space>
                }
                description={room ? `${room.roomCode} · ${room.scriptCode}` : '进入大厅后可先旁观，再认领开放座位。'}
                title={room?.name ?? '房间大厅'}
            />
            <Card loading={loading}>
                {room ? (
                    <RoomLobbyTabs
                        claimingSeatNo={claimingSeatNo}
                        onClaimSeat={(seatNo) => void claimSeat(seatNo)}
                        room={room}
                    />
                ) : (
                    <Empty description="暂无房间数据"/>
                )}
            </Card>
            {canManageRoom && room && (
                <>
                    <ClocktowerInvitationDrawer
                        loading={creatingInvitation}
                        maxSeatNo={room.playerCount}
                        onClose={() => setInvitationOpen(false)}
                        onSubmit={(request) => void saveInvitation(request)}
                        open={invitationOpen}
                        reservations={room.reservations}
                    />
                    <ClocktowerMemberDrawer
                        actionUserId={memberActionUserId}
                        currentUserId={currentUserId}
                        members={room.members}
                        onClose={() => setMemberOpen(false)}
                        onKickMember={(userId, ban) => void kickMember(userId, ban)}
                        open={memberOpen}
                    />
                </>
            )}
        </>
    )
}

export function RoomLobbyManagementActions({
                                               room,
                                               currentUserId,
                                               startDisabled,
                                               starting,
                                               onOpenInvitation,
                                               onOpenMembers,
                                               onStartGame,
                                           }: {
    room: ClocktowerRoomResponse | null
    currentUserId?: number | null
    startDisabled: boolean
    starting: boolean
    onOpenInvitation: () => void
    onOpenMembers: () => void
    onStartGame: () => void
}) {
    if (!canManageClocktowerRoom(room, currentUserId)) {
        return null
    }

    return (
        <>
            <Button icon={<SendOutlined/>} onClick={onOpenInvitation}>邀请</Button>
            <Button icon={<TeamOutlined/>} onClick={onOpenMembers}>成员</Button>
            <Button
                disabled={startDisabled}
                icon={<PlayCircleOutlined/>}
                loading={starting}
                onClick={onStartGame}
                type="primary"
            >
                开始游戏
            </Button>
        </>
    )
}

export function RoomLobbyTabs({room, claimingSeatNo, onClaimSeat}: {
    room: ClocktowerRoomResponse
    claimingSeatNo: number | null
    onClaimSeat: (seatNo: number) => void
}) {
    return (
        <Tabs
            items={[
                {
                    key: 'seats',
                    label: '座位',
                    children: (
                        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                            <LobbyStatus room={room}/>
                            <ClocktowerSeatGrid
                                claimingSeatNo={claimingSeatNo}
                                onClaimSeat={onClaimSeat}
                                reservations={room.reservations}
                                seats={room.seats}
                            />
                        </Space>
                    ),
                },
                {
                    key: 'chat',
                    label: '房间公聊',
                    children: (
                        <Alert
                            showIcon
                            title="房间公聊将在后续聊天任务中接入。"
                            type="info"
                        />
                    ),
                },
                {
                    key: 'invitations',
                    label: '邀请',
                    children: <InvitationSummary room={room}/>,
                },
                {
                    key: 'members',
                    label: '成员',
                    children: <MemberSummary room={room}/>,
                },
            ]}
        />
    )
}

function LobbyStatus({room}: { room: ClocktowerRoomResponse }) {
    const counts = roomLobbyCounts(room)
    const readyToStart = canStartClocktowerRoom(room)

    return (
        <Flex align="center" justify="space-between" gap="middle" wrap>
            <Space wrap>
                <Tag color={room.status === 'RUNNING' ? 'processing' : 'default'}>{room.status}</Tag>
                <Tag color="blue">{room.phase}</Tag>
                <Badge status={readyToStart ? 'success' : 'warning'} text={readyToStart ? '可开始' : '等待就绪'}/>
            </Space>
            <Space wrap>
                <Typography.Text type="secondary">玩家 {counts.occupied}/{counts.required}</Typography.Text>
                <Typography.Text type="secondary">预留 {counts.reserved}</Typography.Text>
                <Typography.Text type="secondary">说书人 {room.storytellerUserId ?? '-'}</Typography.Text>
            </Space>
        </Flex>
    )
}

function InvitationSummary({room}: { room: ClocktowerRoomResponse }) {
    if (!room.reservations?.length) {
        return <Empty description="暂无活动邀请或座位预留"/>
    }

    return (
        <Space orientation="vertical" size="small" style={{width: '100%'}}>
            {room.reservations.map((reservation) => (
                <Flex key={reservation.invitationId} align="center" justify="space-between" gap="middle" wrap>
                    <Typography.Text>邀请 #{reservation.invitationId}</Typography.Text>
                    <Space wrap>
                        <Tag>用户 {reservation.inviteeUserId}</Tag>
                        {reservation.targetSeatNo && <Tag color="warning">座位 #{reservation.targetSeatNo}</Tag>}
                        <Typography.Text type="secondary">{reservation.expiresAt ?? '不过期'}</Typography.Text>
                    </Space>
                </Flex>
            ))}
        </Space>
    )
}

function MemberSummary({room}: { room: ClocktowerRoomResponse }) {
    if (!room.members?.length) {
        return <Empty description="暂无成员数据"/>
    }

    return (
        <Space orientation="vertical" size="small" style={{width: '100%'}}>
            {room.members.map((member) => (
                <Flex key={member.memberId} align="center" justify="space-between" gap="middle" wrap>
                    <Typography.Text>{member.displayName || `用户 ${member.userId}`}</Typography.Text>
                    <Space wrap>
                        <Tag>{member.memberType}</Tag>
                        <Tag color={member.status === 'ACTIVE' ? 'success' : 'default'}>{member.status}</Tag>
                        <Typography.Text type="secondary">座位 {member.seatNo ?? '-'}</Typography.Text>
                    </Space>
                </Flex>
            ))}
        </Space>
    )
}

export function roomLobbyCounts(room: ClocktowerRoomResponse | null) {
    return {
        occupied: room?.seats.filter((seat) => Boolean(seat.userId)).length ?? 0,
        reserved: room?.reservations?.length ?? 0,
        required: room?.playerCount ?? 0,
    }
}

export function canManageClocktowerRoom(room: ClocktowerRoomResponse | null, currentUserId?: number | null) {
    if (!room || currentUserId == null) {
        return false
    }
    return room.members?.find((member) => member.memberType === 'OWNER')?.userId === currentUserId
}

export function canStartClocktowerRoom(room: ClocktowerRoomResponse) {
    if (room.status !== 'LOBBY' && room.status !== 'SETUP') {
        return false
    }
    if (room.reservations?.length) {
        return false
    }
    if (room.seats.length < room.playerCount) {
        return false
    }
    return room.seats.slice(0, room.playerCount).every((seat) => (
        Boolean(seat.userId)
        && typeof seat.roleCode === 'string'
        && seat.roleCode.trim().length > 0
        && (!('ready' in seat) || seat.ready === true)
    ))
}

export function clocktowerRoomPlayPath(roomId: number) {
    return `/clocktower/rooms/${roomId}/play`
}

export async function loadClocktowerRoomForLobby({
                                                    roomId,
                                                    enterRoom = enterClocktowerRoom,
                                                    onLoadingChange,
                                                    onRoomLoaded,
                                                    onError = reportGlobalError,
                                                }: LoadClocktowerRoomForLobbyOptions) {
    if (!Number.isFinite(roomId)) {
        return
    }
    onLoadingChange(true)
    try {
        onRoomLoaded(await enterRoom(roomId))
    } catch (caught) {
        onError(caught)
    } finally {
        onLoadingChange(false)
    }
}

export const Component = RoomLobbyPage
