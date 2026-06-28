import {EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {Button, Card, Descriptions, Drawer, Empty, Form, InputNumber, List, Space, Table, Tabs, Tag, Timeline, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useMemo, useState} from 'react'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerGameAudit,
    getClocktowerRoomAudit,
    listClocktowerAdminChatMessages,
} from './clocktowerService'
import type {
    ClocktowerConversationResponse,
    ClocktowerGameAuditResponse,
    ClocktowerGameEventResponse,
    ClocktowerGameHistoryResponse,
    ClocktowerGameSeatResponse,
    ClocktowerMessageResponse,
    ClocktowerRoomAuditBanResponse,
    ClocktowerRoomAuditInvitationResponse,
    ClocktowerRoomAuditMemberResponse,
    ClocktowerRoomAuditResponse,
    ClocktowerRoomAuditSeatResponse,
} from './clocktowerTypes'

type AuditSearchForm = {
    roomId?: number
    gameId?: number
    conversationId?: number
}

type AuditDetail = {
    title: string
    data: Record<string, unknown>
    events?: ClocktowerGameEventResponse[]
    messages?: ClocktowerMessageResponse[]
}

function ClocktowerAdminAuditPage() {
    const [form] = Form.useForm<AuditSearchForm>()
    const [criteria, setCriteria] = useState<AuditSearchForm>({})
    const [roomAudit, setRoomAudit] = useState<ClocktowerRoomAuditResponse | null>(null)
    const [gameAudit, setGameAudit] = useState<ClocktowerGameAuditResponse | null>(null)
    const [chatMessages, setChatMessages] = useState<ClocktowerMessageResponse[]>([])
    const [chatPage, setChatPage] = useState(1)
    const [chatSize, setChatSize] = useState(20)
    const [chatTotal, setChatTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [detail, setDetail] = useState<AuditDetail | null>(null)

    const conversations = useMemo(
        () => dedupeConversations([
            ...(roomAudit?.conversations ?? []),
            ...(gameAudit?.conversations ?? []),
        ]),
        [gameAudit, roomAudit],
    )
    const games = useMemo(
        () => dedupeGames([
            ...(roomAudit?.games ?? []),
            ...(gameAudit ? [gameAudit] : []),
        ]),
        [gameAudit, roomAudit],
    )

    async function search() {
        const values = await form.validateFields()
        setCriteria(values)
        await loadAudit(values, 1, chatSize)
    }

    async function refresh() {
        await loadAudit(criteria, chatPage, chatSize)
    }

    async function loadAudit(values: AuditSearchForm, nextChatPage: number, nextChatSize: number) {
        setLoading(true)
        setError('')
        const errors: string[] = []
        if (!values.roomId && !values.gameId && !values.conversationId) {
            setRoomAudit(null)
            setGameAudit(null)
            setChatMessages([])
            setChatTotal(0)
            setError('请至少输入一个查询条件')
            setLoading(false)
            return
        }
        if (!values.roomId) {
            setRoomAudit(null)
        }
        if (!values.gameId) {
            setGameAudit(null)
        }
        if (!values.conversationId) {
            setChatMessages([])
            setChatTotal(0)
        }

        try {
            if (values.roomId) {
                try {
                    setRoomAudit(await getClocktowerRoomAudit(values.roomId))
                } catch (caught) {
                    setRoomAudit(null)
                    errors.push(`房间审计查询失败：${resolveErrorMessage(caught)}`)
                }
            }
            if (values.gameId) {
                try {
                    setGameAudit(await getClocktowerGameAudit(values.gameId))
                } catch (caught) {
                    setGameAudit(null)
                    errors.push(`游戏审计查询失败：${resolveErrorMessage(caught)}`)
                }
            }
            if (values.conversationId) {
                try {
                    const page = await listClocktowerAdminChatMessages(values.conversationId, {
                        page: nextChatPage,
                        size: nextChatSize,
                    })
                    setChatMessages(page.records)
                    setChatPage(page.page)
                    setChatSize(page.size)
                    setChatTotal(page.total)
                } catch (caught) {
                    setChatMessages([])
                    setChatTotal(0)
                    errors.push(`聊天审计查询失败：${resolveErrorMessage(caught)}`)
                }
            }
        } finally {
            setError(errors.join('；'))
            setLoading(false)
        }
    }

    const roomColumns: ColumnsType<ClocktowerRoomAuditResponse> = [
        {title: '房间 ID', dataIndex: 'roomId', width: 100},
        {title: '房间码', dataIndex: 'roomCode', width: 120},
        {title: '房间名', dataIndex: 'name', width: 180, render: valueOrDash},
        {title: '房间状态', dataIndex: 'roomStatus', width: 120, render: renderTag},
        {title: '档案状态', dataIndex: 'profileStatus', width: 120, render: renderTag},
        {title: '可见性', dataIndex: 'visibility', width: 110, render: renderTag},
        {title: '说书人', dataIndex: 'storytellerUserId', width: 110, render: valueOrDash},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {title: '当前游戏', dataIndex: 'currentGameId', width: 110, render: valueOrDash},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => setDetail({
                        title: `房间 #${record.roomId}`,
                        data: toRecord(record),
                    })}
                    size="small"
                >
                    详情
                </Button>
            ),
        },
    ]
    const roomSeatColumns: ColumnsType<ClocktowerRoomAuditSeatResponse> = [
        {title: '座位 ID', dataIndex: 'seatId', width: 100},
        {title: '座位号', dataIndex: 'seatNo', width: 90},
        {title: '成员 ID', dataIndex: 'roomMemberId', width: 110, render: valueOrDash},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {title: '昵称', dataIndex: 'displayName', width: 140, render: valueOrDash},
        {title: '角色', dataIndex: 'roleCode', width: 140, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '旅行者', dataIndex: 'traveler', width: 100, render: renderBoolean},
    ]
    const gameColumns: ColumnsType<ClocktowerGameHistoryResponse | ClocktowerGameAuditResponse> = [
        {title: '游戏 ID', dataIndex: 'gameId', width: 100},
        {title: '房间 ID', dataIndex: 'roomId', width: 100},
        {title: '游戏编号', dataIndex: 'gameNo', width: 110},
        {title: '剧本', dataIndex: 'scriptCode', width: 170, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '阶段', dataIndex: 'phase', width: 120, render: renderTag},
        {title: '开始时间', dataIndex: 'startedAt', width: 180, render: renderDateTime},
        {title: '结束时间', dataIndex: 'endedAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => setDetail({
                        title: `游戏 #${record.gameId}`,
                        data: toRecord(record),
                        events: 'events' in record ? record.events : undefined,
                    })}
                    size="small"
                >
                    详情
                </Button>
            ),
        },
    ]
    const gameSeatColumns: ColumnsType<ClocktowerGameSeatResponse> = [
        {title: '游戏座位', dataIndex: 'gameSeatId', width: 110},
        {title: '房间座位', dataIndex: 'roomSeatId', width: 110},
        {title: '座位号', dataIndex: 'seatNo', width: 90},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {title: '昵称', dataIndex: 'displayName', width: 140, render: valueOrDash},
        {title: '角色', dataIndex: 'roleCode', width: 130, render: valueOrDash},
        {title: '阵营', dataIndex: 'alignment', width: 100, render: valueOrDash},
        {title: '生命', dataIndex: 'lifeStatus', width: 100, render: renderTag},
        {title: '公开生命', dataIndex: 'publicLifeStatus', width: 110, render: renderTag},
        {title: '死票', dataIndex: 'hasDeadVote', width: 90, render: renderBoolean},
        {title: '旅行者', dataIndex: 'traveler', width: 100, render: renderBoolean},
    ]
    const eventColumns: ColumnsType<ClocktowerGameEventResponse> = [
        {title: '#', dataIndex: 'eventSeq', width: 80},
        {title: '事件 ID', dataIndex: 'eventId', width: 100},
        {title: '类型', dataIndex: 'eventType', width: 180, render: renderTag},
        {title: '阶段', dataIndex: 'phase', width: 120, render: renderTag},
        {title: '日', dataIndex: 'dayNo', width: 70},
        {title: '夜', dataIndex: 'nightNo', width: 70},
        {title: '行动座位', dataIndex: 'actorGameSeatId', width: 110, render: valueOrDash},
        {title: '目标座位', dataIndex: 'targetGameSeatId', width: 110, render: valueOrDash},
        {title: '可见性', dataIndex: 'visibility', width: 110, render: renderTag},
        {title: '发生时间', dataIndex: 'occurredAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => setDetail({title: `事件 #${record.eventSeq}`, data: toRecord(record)})}
                    size="small"
                >
                    详情
                </Button>
            ),
        },
    ]
    const conversationColumns: ColumnsType<ClocktowerConversationResponse> = [
        {title: '会话 ID', dataIndex: 'conversationId', width: 110},
        {title: '房间 ID', dataIndex: 'roomId', width: 100},
        {title: '游戏 ID', dataIndex: 'gameId', width: 100, render: valueOrDash},
        {title: '频道', dataIndex: 'channelKey', width: 160, render: valueOrDash},
        {title: '分组', dataIndex: 'groupKey', width: 160, render: valueOrDash},
        {title: '类型', dataIndex: 'conversationType', width: 140, render: renderTag},
        {title: '对端标识', dataIndex: 'displayPeerKey', width: 160, render: valueOrDash},
        {title: '消息序号', dataIndex: 'messageSeq', width: 110},
        {title: '最后消息', dataIndex: 'lastMessageAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => setDetail({
                        title: `会话 #${record.conversationId}`,
                        data: toRecord(record),
                        messages: criteria.conversationId === record.conversationId ? chatMessages : undefined,
                    })}
                    size="small"
                >
                    详情
                </Button>
            ),
        },
    ]
    const messageColumns: ColumnsType<ClocktowerMessageResponse> = [
        {title: '#', dataIndex: 'messageSeq', width: 80},
        {title: '消息 ID', dataIndex: 'messageId', width: 110},
        {title: '会话 ID', dataIndex: 'conversationId', width: 110},
        {title: '发送用户', dataIndex: 'senderUserId', width: 110, render: valueOrDash},
        {title: '类型', dataIndex: 'messageType', width: 120, render: renderTag},
        {
            title: '内容',
            dataIndex: 'content',
            render: (value: string) => <Typography.Text ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '发送时间', dataIndex: 'sentAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => setDetail({title: `消息 #${record.messageSeq}`, data: toRecord(record)})}
                    size="small"
                >
                    详情
                </Button>
            ),
        },
    ]
    const invitationColumns: ColumnsType<ClocktowerRoomAuditInvitationResponse> = [
        {title: '邀请 ID', dataIndex: 'invitationId', width: 110},
        {title: '邀请人', dataIndex: 'inviterUserId', width: 110, render: valueOrDash},
        {title: '被邀请人', dataIndex: 'inviteeUserId', width: 120},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '有效', dataIndex: 'activeStatus', width: 90, render: renderBoolean},
        {title: '目标座位', dataIndex: 'targetSeatNo', width: 110, render: valueOrDash},
        {title: '过期时间', dataIndex: 'expiresAt', width: 180, render: renderDateTime},
        {title: '接受时间', dataIndex: 'acceptedAt', width: 180, render: renderDateTime},
    ]
    const memberColumns: ColumnsType<ClocktowerRoomAuditMemberResponse> = [
        {title: '成员 ID', dataIndex: 'memberId', width: 110},
        {title: '用户 ID', dataIndex: 'userId', width: 100},
        {title: '类型', dataIndex: 'memberType', width: 120, render: renderTag},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '有效', dataIndex: 'activeStatus', width: 90, render: renderBoolean},
        {title: '座位号', dataIndex: 'seatNo', width: 100, render: valueOrDash},
        {title: '昵称', dataIndex: 'displayName', width: 140, render: valueOrDash},
        {title: '加入时间', dataIndex: 'joinedAt', width: 180, render: renderDateTime},
        {title: '离开时间', dataIndex: 'leftAt', width: 180, render: renderDateTime},
    ]
    const banColumns: ColumnsType<ClocktowerRoomAuditBanResponse> = [
        {title: '封禁 ID', dataIndex: 'banId', width: 110},
        {title: '用户 ID', dataIndex: 'userId', width: 100},
        {title: '操作人', dataIndex: 'bannedByUserId', width: 110, render: valueOrDash},
        {title: '原因', dataIndex: 'reason', render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '过期时间', dataIndex: 'expiresAt', width: 180, render: renderDateTime},
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(refresh)}>刷新</Button>}
                description="按房间、游戏或会话 ID 查询钟楼审计数据。后台当前提供精确查询接口，不提供全量列表。"
                title="钟楼审计"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space wrap>
                        <Form.Item label="房间 ID" name="roomId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="游戏 ID" name="gameId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="会话 ID" name="conversationId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label=" ">
                            <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(search)} type="primary">
                                查询
                            </Button>
                        </Form.Item>
                    </Space>
                </Form>
                {error && <Typography.Text type="danger">{error}</Typography.Text>}
            </Card>
            <Card style={{marginTop: 16}}>
                <Tabs
                    items={[
                        {
                            key: 'rooms',
                            label: '房间',
                            children: (
                                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                                    <Table<ClocktowerRoomAuditResponse>
                                        columns={roomColumns}
                                        dataSource={roomAudit ? [roomAudit] : []}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无房间审计数据"/>}}
                                        pagination={false}
                                        rowKey="roomId"
                                        scroll={{x: 1320}}
                                    />
                                    <Table<ClocktowerRoomAuditSeatResponse>
                                        columns={roomSeatColumns}
                                        dataSource={roomAudit?.seats ?? []}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无房间座位数据"/>}}
                                        pagination={{pageSize: 10}}
                                        rowKey="seatId"
                                        scroll={{x: 960}}
                                        title={() => '房间座位'}
                                    />
                                </Space>
                            ),
                        },
                        {
                            key: 'games',
                            label: '游戏',
                            children: (
                                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                                    <Table<ClocktowerGameHistoryResponse | ClocktowerGameAuditResponse>
                                        columns={gameColumns}
                                        dataSource={games}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无游戏审计数据"/>}}
                                        pagination={{pageSize: 10}}
                                        rowKey="gameId"
                                        scroll={{x: 1380}}
                                    />
                                    <Table<ClocktowerGameSeatResponse>
                                        columns={gameSeatColumns}
                                        dataSource={gameAudit?.seats ?? []}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无游戏座位数据"/>}}
                                        pagination={{pageSize: 10}}
                                        rowKey="gameSeatId"
                                        scroll={{x: 1300}}
                                        title={() => '游戏座位'}
                                    />
                                    <Table<ClocktowerGameEventResponse>
                                        columns={eventColumns}
                                        dataSource={gameAudit?.events ?? []}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无游戏事件数据"/>}}
                                        pagination={{pageSize: 10}}
                                        rowKey="eventId"
                                        scroll={{x: 1380}}
                                        title={() => '游戏事件'}
                                    />
                                </Space>
                            ),
                        },
                        {
                            key: 'chat',
                            label: '聊天',
                            children: (
                                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                                    <Table<ClocktowerConversationResponse>
                                        columns={conversationColumns}
                                        dataSource={conversations}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="暂无会话数据"/>}}
                                        pagination={{pageSize: 10}}
                                        rowKey="conversationId"
                                        scroll={{x: 1420}}
                                        title={() => '会话'}
                                    />
                                    <Table<ClocktowerMessageResponse>
                                        columns={messageColumns}
                                        dataSource={chatMessages}
                                        loading={loading}
                                        locale={{emptyText: <Empty description="输入会话 ID 后查询聊天消息"/>}}
                                        pagination={{
                                            current: chatPage,
                                            pageSize: chatSize,
                                            total: chatTotal,
                                            showSizeChanger: true,
                                            onChange: (page, size) => {
                                                void loadAudit(criteria, page, size)
                                            },
                                        }}
                                        rowKey="messageId"
                                        scroll={{x: 1200}}
                                        title={() => '聊天消息'}
                                    />
                                </Space>
                            ),
                        },
                        {
                            key: 'invitations',
                            label: '邀请',
                            children: (
                                <Table<ClocktowerRoomAuditInvitationResponse>
                                    columns={invitationColumns}
                                    dataSource={roomAudit?.invitations ?? []}
                                    loading={loading}
                                    locale={{emptyText: <Empty description="暂无邀请数据"/>}}
                                    pagination={{pageSize: 10}}
                                    rowKey="invitationId"
                                    scroll={{x: 1180}}
                                />
                            ),
                        },
                        {
                            key: 'members',
                            label: '成员',
                            children: (
                                <Table<ClocktowerRoomAuditMemberResponse>
                                    columns={memberColumns}
                                    dataSource={roomAudit?.members ?? []}
                                    loading={loading}
                                    locale={{emptyText: <Empty description="暂无成员数据"/>}}
                                    pagination={{pageSize: 10}}
                                    rowKey="memberId"
                                    scroll={{x: 1260}}
                                />
                            ),
                        },
                        {
                            key: 'bans',
                            label: '封禁',
                            children: (
                                <Table<ClocktowerRoomAuditBanResponse>
                                    columns={banColumns}
                                    dataSource={roomAudit?.bans ?? []}
                                    loading={loading}
                                    locale={{emptyText: <Empty description="暂无封禁数据"/>}}
                                    pagination={{pageSize: 10}}
                                    rowKey="banId"
                                    scroll={{x: 900}}
                                />
                            ),
                        },
                    ]}
                />
            </Card>
            <Drawer onClose={() => setDetail(null)} open={!!detail} size="large" title={detail?.title}>
                {detail && <AuditDetailContent detail={detail}/>}
            </Drawer>
        </>
    )
}

function AuditDetailContent({detail}: { detail: AuditDetail }) {
    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Descriptions bordered column={1} items={Object.entries(detail.data).map(([key, value]) => ({
                key,
                label: key,
                children: renderDetailValue(key, value),
            }))}/>
            {detail.events && (
                <Card size="small" title="事件时间线">
                    <GameEventTimeline events={detail.events}/>
                </Card>
            )}
            {detail.messages && (
                <Card size="small" title="消息列表">
                    <List<ClocktowerMessageResponse>
                        dataSource={detail.messages}
                        locale={{emptyText: '暂无消息'}}
                        renderItem={(message) => (
                            <List.Item>
                                <List.Item.Meta
                                    description={<DateTimeText value={message.sentAt}/>}
                                    title={`#${message.messageSeq} ${message.messageType}`}
                                />
                                <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                    {message.content}
                                </Typography.Paragraph>
                            </List.Item>
                        )}
                        rowKey="messageId"
                    />
                </Card>
            )}
        </Space>
    )
}

function GameEventTimeline({events}: { events: ClocktowerGameEventResponse[] }) {
    if (events.length === 0) {
        return <Empty description="暂无事件"/>
    }

    return (
        <Timeline
            items={events.map((event) => ({
                color: event.visibility === 'PUBLIC' ? 'green' : 'blue',
                key: event.eventId,
                title: (
                    <Space wrap>
                        <Tag>#{event.eventSeq}</Tag>
                        <Tag color="blue">{event.eventType}</Tag>
                        <Tag>{event.visibility}</Tag>
                    </Space>
                ),
                content: (
                    <Space orientation="vertical" size={4}>
                        <Typography.Text type="secondary">
                            {event.phase} · 第 {event.dayNo} 天 / 第 {event.nightNo} 夜 · <DateTimeText value={event.occurredAt}/>
                        </Typography.Text>
                        <Typography.Text code>{formatPayload(event.payload)}</Typography.Text>
                    </Space>
                ),
            }))}
        />
    )
}

function dedupeConversations(conversations: ClocktowerConversationResponse[]) {
    return Array.from(new Map(conversations.map((conversation) => [conversation.conversationId, conversation])).values())
}

function dedupeGames(games: Array<ClocktowerGameHistoryResponse | ClocktowerGameAuditResponse>) {
    return Array.from(new Map(games.map((game) => [game.gameId, game])).values())
}

function toRecord(value: object) {
    return value as unknown as Record<string, unknown>
}

function renderDetailValue(key: string, value: unknown) {
    if (value === undefined || value === null) {
        return '-'
    }
    if (typeof value === 'boolean') {
        return renderBoolean(value)
    }
    if (key.endsWith('At') && (typeof value === 'string' || typeof value === 'number')) {
        return <DateTimeText value={value}/>
    }
    if (Array.isArray(value)) {
        return `${value.length} 条`
    }
    if (typeof value === 'object') {
        return (
            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                {formatPayload(value as Record<string, unknown>)}
            </Typography.Paragraph>
        )
    }
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'bigint') {
        return String(value)
    }
    return '-'
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function renderTag(value?: string | number | null) {
    return value === undefined || value === null ? '-' : <Tag>{value}</Tag>
}

function renderBoolean(value?: boolean | null) {
    if (value === undefined || value === null) {
        return '-'
    }
    return value ? '是' : '否'
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function formatPayload(payload: Record<string, unknown>) {
    if (!payload || Object.keys(payload).length === 0) {
        return '{}'
    }
    return JSON.stringify(payload, null, 2)
}

export const Component = ClocktowerAdminAuditPage
