import {EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {
    Alert,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    List,
    Row,
    Select,
    Space,
    Spin,
    Statistic,
    Table,
    Tabs,
    Tag,
    Timeline,
    Typography,
} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useRef, useState} from 'react'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerAuditSummary,
    getClocktowerGameAudit,
    getClocktowerRoomAudit,
    listClocktowerAuditBans,
    listClocktowerAuditConversations,
    listClocktowerAuditEvents,
    listClocktowerAuditGames,
    listClocktowerAuditInvitations,
    listClocktowerAuditMembers,
    listClocktowerAuditMessages,
    listClocktowerAuditRooms,
} from './clocktowerService'
import type {
    ClocktowerAuditBanResponse,
    ClocktowerAuditConversationResponse,
    ClocktowerAuditEventResponse,
    ClocktowerAuditFilter,
    ClocktowerAuditGameResponse,
    ClocktowerAuditInvitationResponse,
    ClocktowerAuditMemberResponse,
    ClocktowerAuditMessageResponse,
    ClocktowerAuditQuery,
    ClocktowerAuditRoomResponse,
    ClocktowerAuditSummaryResponse,
    ClocktowerConversationResponse,
    ClocktowerGameAuditResponse,
    ClocktowerGameEventResponse,
    ClocktowerGameSeatResponse,
    ClocktowerPage,
    ClocktowerRoomAuditResponse,
    ClocktowerRoomAuditSeatResponse,
} from './clocktowerTypes'

type AuditIdValue = string | number

type AuditSearchForm = {
    roomIds?: AuditIdValue[]
    gameIds?: AuditIdValue[]
    conversationIds?: AuditIdValue[]
    roomName?: string
}

type AuditDetail = {
    title: string
    data: Record<string, unknown>
    loading?: boolean
    error?: string
    roomSeats?: ClocktowerRoomAuditSeatResponse[]
    gameSeats?: ClocktowerGameSeatResponse[]
    events?: ClocktowerGameEventResponse[]
    conversations?: ClocktowerConversationResponse[]
}

type PaginationState = {
    page: number
    size: number
}

type AuditResource = 'rooms' | 'games' | 'events' | 'conversations' | 'messages' | 'members' | 'invitations' | 'bans'

type AuditPaginationState = Record<AuditResource, PaginationState>

type PageLoadState<T> = {
    page: ClocktowerPage<T>
    loading: boolean
    error: string
}

const DEFAULT_PAGE_SIZE = 20
const MAX_AUDIT_IDS = 50
const EMPTY_SUMMARY: ClocktowerAuditSummaryResponse = {
    roomCount: 0,
    gameCount: 0,
    eventCount: 0,
    conversationCount: 0,
    messageCount: 0,
    memberCount: 0,
    invitationCount: 0,
    banCount: 0,
}
const INITIAL_PAGINATION: AuditPaginationState = {
    rooms: {page: 1, size: DEFAULT_PAGE_SIZE},
    games: {page: 1, size: DEFAULT_PAGE_SIZE},
    events: {page: 1, size: DEFAULT_PAGE_SIZE},
    conversations: {page: 1, size: DEFAULT_PAGE_SIZE},
    messages: {page: 1, size: DEFAULT_PAGE_SIZE},
    members: {page: 1, size: DEFAULT_PAGE_SIZE},
    invitations: {page: 1, size: DEFAULT_PAGE_SIZE},
    bans: {page: 1, size: DEFAULT_PAGE_SIZE},
}

function ClocktowerAdminAuditPage() {
    const [form] = Form.useForm<AuditSearchForm>()
    const [criteria, setCriteria] = useState<ClocktowerAuditFilter>({})
    const [pagination, setPagination] = useState<AuditPaginationState>(INITIAL_PAGINATION)
    const [refreshVersion, setRefreshVersion] = useState(0)
    const [detail, setDetail] = useState<AuditDetail | null>(null)
    const detailRequestRef = useRef(0)

    const summary = useAuditSummary(criteria, refreshVersion)
    const rooms = useAuditPage(criteria, pagination.rooms, refreshVersion, listClocktowerAuditRooms)
    const games = useAuditPage(criteria, pagination.games, refreshVersion, listClocktowerAuditGames)
    const events = useAuditPage(criteria, pagination.events, refreshVersion, listClocktowerAuditEvents)
    const conversations = useAuditPage(
        criteria,
        pagination.conversations,
        refreshVersion,
        listClocktowerAuditConversations,
    )
    const messages = useAuditPage(criteria, pagination.messages, refreshVersion, listClocktowerAuditMessages)
    const members = useAuditPage(criteria, pagination.members, refreshVersion, listClocktowerAuditMembers)
    const invitations = useAuditPage(
        criteria,
        pagination.invitations,
        refreshVersion,
        listClocktowerAuditInvitations,
    )
    const bans = useAuditPage(criteria, pagination.bans, refreshVersion, listClocktowerAuditBans)
    const loading = summary.loading || rooms.loading || games.loading || events.loading || conversations.loading
        || messages.loading || members.loading || invitations.loading || bans.loading

    async function search() {
        const values = await form.validateFields()
        const nextCriteria = normalizeAuditCriteria(values)
        form.setFieldsValue({
            ...values,
            roomIds: nextCriteria.roomIds?.map(String),
            gameIds: nextCriteria.gameIds?.map(String),
            conversationIds: nextCriteria.conversationIds?.map(String),
            roomName: nextCriteria.roomName,
        })
        setCriteria(nextCriteria)
        setPagination((current) => resetPaginationPages(current))
        setRefreshVersion((current) => current + 1)
    }

    function changePage(resource: AuditResource, page: number, size: number) {
        setPagination((current) => ({
            ...current,
            [resource]: {page, size},
        }))
    }

    async function openRoomDetail(record: ClocktowerAuditRoomResponse) {
        const requestId = ++detailRequestRef.current
        setDetail({title: `房间 #${record.roomId}`, data: toRecord(record), loading: true})
        try {
            const audit = await getClocktowerRoomAudit(record.roomId)
            if (requestId !== detailRequestRef.current) {
                return
            }
            setDetail({
                title: `房间 #${record.roomId}`,
                data: auditRecord(audit),
                roomSeats: audit.seats,
                conversations: audit.conversations,
            })
        } catch (caught) {
            if (requestId === detailRequestRef.current) {
                setDetail({
                    title: `房间 #${record.roomId}`,
                    data: toRecord(record),
                    error: `房间详情加载失败：${resolveErrorMessage(caught)}`,
                })
            }
        }
    }

    async function openGameDetail(record: ClocktowerAuditGameResponse) {
        const requestId = ++detailRequestRef.current
        setDetail({title: `游戏 #${record.gameId}`, data: toRecord(record), loading: true})
        try {
            const audit = await getClocktowerGameAudit(record.gameId)
            if (requestId !== detailRequestRef.current) {
                return
            }
            setDetail({
                title: `游戏 #${record.gameId}`,
                data: auditRecord(audit),
                gameSeats: audit.seats,
                events: audit.events,
                conversations: audit.conversations,
            })
        } catch (caught) {
            if (requestId === detailRequestRef.current) {
                setDetail({
                    title: `游戏 #${record.gameId}`,
                    data: toRecord(record),
                    error: `游戏详情加载失败：${resolveErrorMessage(caught)}`,
                })
            }
        }
    }

    const roomColumns: ColumnsType<ClocktowerAuditRoomResponse> = [
        {title: '房间 ID', dataIndex: 'roomId', width: 100},
        {title: '房间码', dataIndex: 'roomCode', width: 120},
        {title: '房间名', dataIndex: 'roomName', width: 180, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '可见性', dataIndex: 'visibility', width: 110, render: renderTag},
        {title: '房主', dataIndex: 'ownerUserId', width: 100, render: valueOrDash},
        {
            title: '成员 / 容量',
            width: 120,
            render: (_, record) => `${record.currentMemberCount}/${record.capacity}`,
        },
        {title: '最后活跃', dataIndex: 'lastActiveAt', width: 180, render: renderDateTime},
        {title: '创建时间', dataIndex: 'createdAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button icon={<EyeOutlined/>} onClick={() => void openRoomDetail(record)} size="small">详情</Button>
            ),
        },
    ]
    const gameColumns: ColumnsType<ClocktowerAuditGameResponse> = [
        {title: '游戏 ID', dataIndex: 'gameId', width: 100},
        {title: '房间 ID', dataIndex: 'roomId', width: 100},
        {title: '房间名', dataIndex: 'roomName', width: 160, render: valueOrDash},
        {title: '游戏编号', dataIndex: 'gameNo', width: 110},
        {title: '剧本', dataIndex: 'scriptCode', width: 170, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '阶段', dataIndex: 'phase', width: 120, render: renderTag},
        {title: '日 / 夜', width: 100, render: (_, record) => `${record.dayNo} / ${record.nightNo}`},
        {title: '开始时间', dataIndex: 'startedAt', width: 180, render: renderDateTime},
        {title: '结束时间', dataIndex: 'endedAt', width: 180, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button icon={<EyeOutlined/>} onClick={() => void openGameDetail(record)} size="small">详情</Button>
            ),
        },
    ]
    const eventColumns: ColumnsType<ClocktowerAuditEventResponse> = [
        {title: '事件 ID', dataIndex: 'eventId', width: 100},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '游戏 ID', dataIndex: 'gameId', width: 100},
        {title: '#', dataIndex: 'eventSeq', width: 80},
        {title: '类型', dataIndex: 'eventType', width: 180, render: renderTag},
        {title: '阶段', dataIndex: 'phase', width: 120, render: renderTag},
        {title: '日 / 夜', width: 100, render: (_, record) => `${record.dayNo} / ${record.nightNo}`},
        {title: '行动座位', dataIndex: 'actorGameSeatId', width: 110, render: valueOrDash},
        {title: '目标座位', dataIndex: 'targetGameSeatId', width: 110, render: valueOrDash},
        {title: '可见性', dataIndex: 'visibility', width: 110, render: renderTag},
        {title: '状态', dataIndex: 'status', width: 100, render: renderTag},
        {title: '发生时间', dataIndex: 'occurredAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('事件', record.eventId, record, setDetail)},
    ]
    const conversationColumns: ColumnsType<ClocktowerAuditConversationResponse> = [
        {title: '会话 ID', dataIndex: 'conversationId', width: 110},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '游戏 ID', dataIndex: 'gameId', width: 100, render: valueOrDash},
        {title: '频道', width: 180, render: (_, record) => namedKey(record.channelName, record.channelKey)},
        {title: '分组', width: 180, render: (_, record) => namedKey(record.groupName, record.groupKey)},
        {title: '类型', dataIndex: 'conversationType', width: 120, render: renderTag},
        {title: '状态', dataIndex: 'status', width: 100, render: renderTag},
        {title: '消息序号', dataIndex: 'messageSeq', width: 110},
        {title: '最后消息', dataIndex: 'lastMessageAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('会话', record.conversationId, record, setDetail)},
    ]
    const messageColumns: ColumnsType<ClocktowerAuditMessageResponse> = [
        {title: '消息 ID', dataIndex: 'messageId', width: 110},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '游戏 ID', dataIndex: 'gameId', width: 100, render: valueOrDash},
        {title: '会话 ID', dataIndex: 'conversationId', width: 110},
        {title: '#', dataIndex: 'messageSeq', width: 80},
        {title: '发送用户', dataIndex: 'senderUserId', width: 110, render: valueOrDash},
        {title: '类型', dataIndex: 'messageType', width: 110, render: renderTag},
        {
            title: '内容',
            dataIndex: 'content',
            width: 320,
            render: (value: string) => <Typography.Text ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '状态', dataIndex: 'status', width: 100, render: renderTag},
        {title: '发送时间', dataIndex: 'sentAt', width: 180, render: renderDateTime},
        {title: '编辑时间', dataIndex: 'editedAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('消息', record.messageId, record, setDetail)},
    ]
    const memberColumns: ColumnsType<ClocktowerAuditMemberResponse> = [
        {title: '成员 ID', dataIndex: 'memberId', width: 110},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '用户 ID', dataIndex: 'userId', width: 100},
        {title: '昵称', dataIndex: 'displayName', width: 140, render: valueOrDash},
        {title: '类型', dataIndex: 'memberType', width: 120, render: renderTag},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '有效', dataIndex: 'activeStatus', width: 90, render: renderBoolean},
        {title: '座位号', dataIndex: 'seatNo', width: 90, render: valueOrDash},
        {title: '加入时间', dataIndex: 'joinedAt', width: 180, render: renderDateTime},
        {title: '离开时间', dataIndex: 'leftAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('成员', record.memberId, record, setDetail)},
    ]
    const invitationColumns: ColumnsType<ClocktowerAuditInvitationResponse> = [
        {title: '邀请 ID', dataIndex: 'invitationId', width: 110},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '邀请码', dataIndex: 'invitationCode', width: 140},
        {title: '邀请人', dataIndex: 'inviterUserId', width: 110, render: valueOrDash},
        {title: '被邀请人', dataIndex: 'inviteeUserId', width: 120, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '有效', dataIndex: 'activeStatus', width: 90, render: renderBoolean},
        {title: '目标座位', dataIndex: 'targetSeatNo', width: 110, render: valueOrDash},
        {title: '过期时间', dataIndex: 'expiresAt', width: 180, render: renderDateTime},
        {title: '接受时间', dataIndex: 'acceptedAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('邀请', record.invitationId, record, setDetail)},
    ]
    const banColumns: ColumnsType<ClocktowerAuditBanResponse> = [
        {title: '封禁 ID', dataIndex: 'banId', width: 110},
        {title: '房间', width: 190, render: (_, record) => roomLabel(record.roomId, record.roomName)},
        {title: '用户 ID', dataIndex: 'userId', width: 100},
        {title: '操作人', dataIndex: 'bannedByUserId', width: 110, render: valueOrDash},
        {title: '原因', dataIndex: 'reason', width: 240, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
        {title: '过期时间', dataIndex: 'expiresAt', width: 180, render: renderDateTime},
        {title: '创建时间', dataIndex: 'createdAt', width: 180, render: renderDateTime},
        {title: '操作', fixed: 'right', width: 100, render: (_, record) => detailButton('封禁', record.banId, record, setDetail)},
    ]

    return (
        <>
            <PageToolbar
                actions={(
                    <Button
                        icon={<ReloadOutlined/>}
                        loading={loading}
                        onClick={() => setRefreshVersion((current) => current + 1)}
                    >
                        刷新
                    </Button>
                )}
                description="统一查询房间及其全部历史游戏、事件、会话和消息；不填写条件时查询全部数据。"
                title="钟楼审计"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space align="end" wrap>
                        <AuditIdSelect label="房间 ID" name="roomIds"/>
                        <AuditIdSelect label="游戏 ID" name="gameIds"/>
                        <AuditIdSelect label="会话 ID" name="conversationIds"/>
                        <Form.Item label="房间名称" name="roomName">
                            <Input allowClear placeholder="支持模糊查询" style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item label=" ">
                            <Button icon={<SearchOutlined/>} onClick={voidify(search)} type="primary">
                                查询
                            </Button>
                        </Form.Item>
                    </Space>
                </Form>
            </Card>
            <AuditSummary state={summary}/>
            <Card style={{marginTop: 16}}>
                <Tabs
                    items={[
                        auditTab('rooms', '房间', summary.data.roomCount, rooms, roomColumns, pagination.rooms, changePage, 1420),
                        auditTab('games', '游戏', summary.data.gameCount, games, gameColumns, pagination.games, changePage, 1580),
                        auditTab('events', '事件', summary.data.eventCount, events, eventColumns, pagination.events, changePage, 1640),
                        auditTab(
                            'conversations',
                            '会话',
                            summary.data.conversationCount,
                            conversations,
                            conversationColumns,
                            pagination.conversations,
                            changePage,
                            1530,
                        ),
                        auditTab('messages', '消息', summary.data.messageCount, messages, messageColumns, pagination.messages, changePage, 1820),
                        auditTab('members', '成员', summary.data.memberCount, members, memberColumns, pagination.members, changePage, 1560),
                        auditTab(
                            'invitations',
                            '邀请',
                            summary.data.invitationCount,
                            invitations,
                            invitationColumns,
                            pagination.invitations,
                            changePage,
                            1640,
                        ),
                        auditTab('bans', '封禁', summary.data.banCount, bans, banColumns, pagination.bans, changePage, 1320),
                    ]}
                />
            </Card>
            <Drawer
                onClose={() => {
                    detailRequestRef.current += 1
                    setDetail(null)
                }}
                open={!!detail}
                size="large"
                title={detail?.title}
            >
                {detail && <AuditDetailContent detail={detail}/>}
            </Drawer>
        </>
    )
}

function AuditIdSelect({label, name}: { label: string; name: keyof AuditSearchForm }) {
    return (
        <Form.Item
            label={label}
            name={name}
            rules={[{
                validator: (_, values?: AuditIdValue[]) => {
                    const result = parseAuditIds(values)
                    if (result.invalid.length > 0) {
                        return Promise.reject(new Error(`${label} 只支持正整数`))
                    }
                    if (result.ids.length > MAX_AUDIT_IDS) {
                        return Promise.reject(new Error(`${label} 最多输入 ${MAX_AUDIT_IDS} 个`))
                    }
                    return Promise.resolve()
                },
            }]}
        >
            <Select
                maxTagCount="responsive"
                mode="tags"
                open={false}
                placeholder="支持多个 ID"
                style={{width: 260}}
                tokenSeparators={[',', '，', ' ', '\n', '\t']}
            />
        </Form.Item>
    )
}

function AuditSummary({state}: { state: { data: ClocktowerAuditSummaryResponse; loading: boolean; error: string } }) {
    const items = [
        ['房间', state.data.roomCount],
        ['游戏', state.data.gameCount],
        ['事件', state.data.eventCount],
        ['会话', state.data.conversationCount],
        ['消息', state.data.messageCount],
        ['成员', state.data.memberCount],
        ['邀请', state.data.invitationCount],
        ['封禁', state.data.banCount],
    ] as const
    return (
        <Card loading={state.loading} style={{marginTop: 16}} title="查询汇总">
            {state.error && <Alert message={state.error} showIcon type="error"/>}
            <Row gutter={[16, 16]}>
                {items.map(([title, value]) => (
                    <Col key={title} lg={3} md={6} sm={12} xs={12}>
                        <Statistic title={title} value={value}/>
                    </Col>
                ))}
            </Row>
        </Card>
    )
}

function auditTab<T extends object>(
    key: AuditResource,
    title: string,
    count: number,
    state: PageLoadState<T>,
    columns: ColumnsType<T>,
    pagination: PaginationState,
    changePage: (resource: AuditResource, page: number, size: number) => void,
    scrollWidth: number,
) {
    return {
        key,
        label: `${title} (${count})`,
        children: (
            <Space orientation="vertical" size={12} style={{width: '100%'}}>
                {state.error && <Alert message={state.error} showIcon type="error"/>}
                <Table<T>
                    columns={columns}
                    dataSource={state.page.records}
                    loading={state.loading}
                    locale={{emptyText: <Empty description={`暂无${title}审计数据`}/>}}
                    pagination={{
                        current: state.page.page || pagination.page,
                        pageSize: state.page.size || pagination.size,
                        total: state.page.total,
                        showSizeChanger: true,
                        showTotal: (total) => `共 ${total} 条`,
                        onChange: (page, size) => changePage(key, page, size),
                    }}
                    rowKey={(record) => auditRowKey(key, record)}
                    scroll={{x: scrollWidth}}
                />
            </Space>
        ),
    }
}

function AuditDetailContent({detail}: { detail: AuditDetail }) {
    const seatColumns: ColumnsType<ClocktowerRoomAuditSeatResponse | ClocktowerGameSeatResponse> = [
        {title: '座位号', dataIndex: 'seatNo', width: 90},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {title: '昵称', dataIndex: 'displayName', width: 140, render: valueOrDash},
        {title: '角色', dataIndex: 'roleCode', width: 140, render: valueOrDash},
        {title: '状态', dataIndex: 'status', width: 110, render: renderTag},
    ]
    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            {detail.error && <Alert message={detail.error} showIcon type="error"/>}
            <Descriptions bordered column={1} items={Object.entries(detail.data).map(([key, value]) => ({
                key,
                label: key,
                children: renderDetailValue(key, value),
            }))}/>
            {detail.loading && <Spin tip="正在加载完整详情"><div style={{height: 60}}/></Spin>}
            {detail.roomSeats && (
                <Card size="small" title="房间座位">
                    <Table columns={seatColumns} dataSource={detail.roomSeats} pagination={false} rowKey="seatId" size="small"/>
                </Card>
            )}
            {detail.gameSeats && (
                <Card size="small" title="游戏座位">
                    <Table columns={seatColumns} dataSource={detail.gameSeats} pagination={false} rowKey="gameSeatId" size="small"/>
                </Card>
            )}
            {detail.events && (
                <Card size="small" title="事件时间线">
                    <GameEventTimeline events={detail.events}/>
                </Card>
            )}
            {detail.conversations && (
                <Card size="small" title="关联会话">
                    <List
                        dataSource={detail.conversations}
                        locale={{emptyText: '暂无会话'}}
                        renderItem={(conversation) => (
                            <List.Item>
                                <List.Item.Meta
                                    description={`消息序号 ${conversation.messageSeq}`}
                                    title={`#${conversation.conversationId} ${conversation.channelKey} / ${conversation.groupKey}`}
                                />
                            </List.Item>
                        )}
                        rowKey="conversationId"
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

function useAuditSummary(criteria: ClocktowerAuditFilter, refreshVersion: number) {
    const requestRef = useRef(0)
    const [state, setState] = useState({data: EMPTY_SUMMARY, loading: true, error: ''})
    useEffect(() => {
        let active = true
        const requestId = ++requestRef.current
        setState((current) => ({...current, loading: true, error: ''}))
        void getClocktowerAuditSummary(criteria).then((data) => {
            if (active && requestId === requestRef.current) {
                setState({data, loading: false, error: ''})
            }
        }).catch((caught: unknown) => {
            if (active && requestId === requestRef.current) {
                setState((current) => ({
                    ...current,
                    loading: false,
                    error: `审计汇总查询失败：${resolveErrorMessage(caught)}`,
                }))
            }
        })
        return () => {
            active = false
        }
    }, [criteria, refreshVersion])
    return state
}

function useAuditPage<T>(
    criteria: ClocktowerAuditFilter,
    pagination: PaginationState,
    refreshVersion: number,
    loader: (query: ClocktowerAuditQuery) => Promise<ClocktowerPage<T>>,
): PageLoadState<T> {
    const requestRef = useRef(0)
    const [state, setState] = useState<PageLoadState<T>>({
        page: emptyPage<T>(pagination),
        loading: true,
        error: '',
    })
    useEffect(() => {
        let active = true
        const requestId = ++requestRef.current
        setState((current) => ({...current, loading: true, error: ''}))
        void loader({...criteria, ...pagination}).then((page) => {
            if (active && requestId === requestRef.current) {
                setState({page, loading: false, error: ''})
            }
        }).catch((caught: unknown) => {
            if (active && requestId === requestRef.current) {
                setState({
                    page: emptyPage<T>(pagination),
                    loading: false,
                    error: `查询失败：${resolveErrorMessage(caught)}`,
                })
            }
        })
        return () => {
            active = false
        }
    }, [criteria, loader, pagination, refreshVersion])
    return state
}

export function parseAuditIds(values?: AuditIdValue[]) {
    const ids: number[] = []
    const invalid: AuditIdValue[] = []
    const seen = new Set<number>()
    values?.forEach((value) => {
        const text = String(value).trim()
        if (!/^\d+$/.test(text)) {
            invalid.push(value)
            return
        }
        const id = Number(text)
        if (!Number.isSafeInteger(id) || id <= 0) {
            invalid.push(value)
            return
        }
        if (!seen.has(id)) {
            seen.add(id)
            ids.push(id)
        }
    })
    return {ids, invalid}
}

export function normalizeAuditCriteria(values: AuditSearchForm): ClocktowerAuditFilter {
    const roomIds = parseAuditIds(values.roomIds).ids
    const gameIds = parseAuditIds(values.gameIds).ids
    const conversationIds = parseAuditIds(values.conversationIds).ids
    const roomName = values.roomName?.trim()
    return {
        ...(roomIds.length > 0 ? {roomIds} : {}),
        ...(gameIds.length > 0 ? {gameIds} : {}),
        ...(conversationIds.length > 0 ? {conversationIds} : {}),
        ...(roomName ? {roomName} : {}),
    }
}

function emptyPage<T>(pagination: PaginationState): ClocktowerPage<T> {
    return {
        records: [],
        page: pagination.page,
        size: pagination.size,
        total: 0,
        totalPages: 0,
    }
}

function resetPaginationPages(current: AuditPaginationState): AuditPaginationState {
    return {
        rooms: {...current.rooms, page: 1},
        games: {...current.games, page: 1},
        events: {...current.events, page: 1},
        conversations: {...current.conversations, page: 1},
        messages: {...current.messages, page: 1},
        members: {...current.members, page: 1},
        invitations: {...current.invitations, page: 1},
        bans: {...current.bans, page: 1},
    }
}

function auditRowKey(resource: AuditResource, record: object) {
    const value = record as Record<string, unknown>
    const keys: Record<AuditResource, string> = {
        rooms: 'roomId',
        games: 'gameId',
        events: 'eventId',
        conversations: 'conversationId',
        messages: 'messageId',
        members: 'memberId',
        invitations: 'invitationId',
        bans: 'banId',
    }
    return String(value[keys[resource]])
}

function detailButton<T extends object>(
    title: string,
    id: number,
    record: T,
    setDetail: (detail: AuditDetail) => void,
) {
    return (
        <Button
            icon={<EyeOutlined/>}
            onClick={() => setDetail({title: `${title} #${id}`, data: toRecord(record)})}
            size="small"
        >
            详情
        </Button>
    )
}

function auditRecord(audit: ClocktowerRoomAuditResponse | ClocktowerGameAuditResponse) {
    const excludedKeys = new Set(['seats', 'conversations', 'events', 'members', 'invitations', 'bans', 'games'])
    return Object.fromEntries(Object.entries(toRecord(audit)).filter(([key]) => !excludedKeys.has(key)))
}

function toRecord(value: object) {
    return value as unknown as Record<string, unknown>
}

function roomLabel(roomId: number, roomName: string) {
    return roomName ? `${roomName} (#${roomId})` : `#${roomId}`
}

function namedKey(name: string, key: string) {
    return name ? `${name} (${key})` : key
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
    if (key.endsWith('Json') && typeof value === 'string') {
        return <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>{value}</Typography.Paragraph>
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
