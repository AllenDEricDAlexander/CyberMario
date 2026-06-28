import {ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Empty, Input, List, Space, Tabs, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {voidify} from '../../../utils/async'
import {
    approveImJoinRequest,
    cancelImJoinRequest,
    createImJoinRequest,
    leaveImSurface,
    listImChannels,
    listImConversations,
    listImGroups,
    listImMessages,
    markImRead,
    rejectImJoinRequest,
    sendImMessage,
} from '../../im/imService'
import type {
    ChannelView,
    ConversationView,
    GroupView,
    ImMessagePushPayload,
    ImSurfaceType,
    JoinResultView,
    MessageView,
    UnreadView,
} from '../../im/imTypes'
import {useImSocket} from '../../im/useImSocket'
import {ImJoinApplyControls, type PendingReviewRequest} from '../../im/components/ImJoinApplyControls'
import {
    CLOCKTOWER_IM_CONTEXT_TYPE,
    createClocktowerClientMsgId,
    createPendingClocktowerMessage,
    mapImConversationToClocktower,
    mapImMessageToClocktower,
} from '../clocktowerService'
import type {
    ClocktowerConversationResponse,
    ClocktowerMessageResponse,
    ClocktowerViewerMode,
} from '../clocktowerTypes'
import {ClocktowerConversationList, conversationLabel} from './ClocktowerConversationList'
import {ClocktowerMessageList} from './ClocktowerMessageList'

export type ClocktowerChatPolicy = {
    readOnly: boolean
    reason: string
}

type ClocktowerChatMessageState = {
    conversationId: number | null
    records: ClocktowerMessageResponse[]
}

type SurfaceIdentity = {
    surfaceType: ImSurfaceType
    surfaceId: number
}

type DiscoverableSurfaces = {
    channels: ChannelView[]
    groups: GroupView[]
}

type ClocktowerChatPanelProps = {
    channels?: ChannelView[]
    conversations?: ClocktowerConversationResponse[]
    gameId?: number | null
    groups?: GroupView[]
    phase: string
    roomId?: number | null
    viewerMode: ClocktowerViewerMode
    title?: string
}

const MESSAGE_TAB_KEY = 'messages'
const SURFACE_TAB_KEY = 'surfaces'
const MESSAGE_PAGE_SIZE = 50
type ClocktowerMessagePushAction = 'MERGE_MESSAGE' | 'LOAD_ACTIVE_HISTORY' | 'REFRESH_CONVERSATIONS'

export function ClocktowerChatPanel({
    channels = [],
    conversations = [],
    gameId,
    groups = [],
    phase,
    roomId,
    title = '聊天',
    viewerMode,
}: ClocktowerChatPanelProps) {
    const fallbackRoomId = roomId ?? conversations[0]?.roomId ?? null
    const fallbackGameId = gameId ?? conversations[0]?.gameId ?? null
    const contextIds = useMemo(
        () => buildClocktowerContextIds(fallbackRoomId, fallbackGameId),
        [fallbackGameId, fallbackRoomId],
    )
    const [conversationRecords, setConversationRecords] = useState<ClocktowerConversationResponse[]>(conversations)
    const [channelRecords, setChannelRecords] = useState<ChannelView[]>(channels)
    const [groupRecords, setGroupRecords] = useState<GroupView[]>(groups)
    const visibleConversations = useMemo(
        () => filterClocktowerConversations(conversationRecords, viewerMode, gameId),
        [conversationRecords, gameId, viewerMode],
    )
    const [activeTabKey, setActiveTabKey] = useState('conversations')
    const [activeConversationId, setActiveConversationId] = useState<number | null>(
        visibleConversations[0]?.conversationId ?? null,
    )
    const activeConversation = visibleConversations.find(
        (conversation) => conversation.conversationId === activeConversationId,
    ) ?? visibleConversations[0] ?? null
    const activeSurface = activeConversation
        ? surfaceForConversation(activeConversation, channelRecords, groupRecords)
        : null
    const discoverableSurfaces = useMemo(
        () => filterDiscoverableClocktowerSurfaces(
            channelRecords,
            groupRecords,
            conversationRecords,
            viewerMode,
            gameId,
        ),
        [channelRecords, conversationRecords, gameId, groupRecords, viewerMode],
    )
    const [messageState, setMessageState] = useState<ClocktowerChatMessageState>({
        conversationId: null,
        records: [],
    })
    const [loading, setLoading] = useState(false)
    const [surfaceLoading, setSurfaceLoading] = useState(false)
    const [sending, setSending] = useState(false)
    const [content, setContent] = useState('')
    const policy = activeConversation ? resolveClocktowerChatPolicy(viewerMode, activeConversation, phase) : null
    const activeConversationKey = activeConversation?.conversationId
    const messages = messageState.conversationId === activeConversationKey ? messageState.records : []
    const confirmedMessageSeq = resolveClocktowerConfirmedMessageSeq(messages, activeConversation)
    const displayMessageSeq = Math.max(confirmedMessageSeq, messages.at(-1)?.messageSeq ?? 0)

    const loadImState = useCallback(async () => {
        if (contextIds.length === 0) {
            return
        }
        setSurfaceLoading(true)
        try {
            const nextContexts = await Promise.all(contextIds.map(async (contextId) => {
                const [nextChannels, nextConversations] = await Promise.all([
                    listImChannels({contextType: CLOCKTOWER_IM_CONTEXT_TYPE, contextId}),
                    listImConversations({contextType: CLOCKTOWER_IM_CONTEXT_TYPE, contextId}),
                ])
                const nextGroups = await listClocktowerSurfaceGroups(contextId, nextChannels)
                return {
                    contextId,
                    channels: nextChannels,
                    conversations: nextConversations,
                    groups: nextGroups,
                }
            }))
            const nextChannels = mergeRecordsById(nextContexts.flatMap((context) => context.channels))
            const nextGroups = mergeRecordsById(nextContexts.flatMap((context) => context.groups))
            const nextConversations = mergeClocktowerConversationRecords(nextContexts.flatMap((context) => (
                mapImConversationsToClocktower(
                    context.conversations,
                    context.channels,
                    context.groups,
                    roomId ?? context.contextId,
                    context.contextId === gameId && context.contextId !== roomId ? gameId : null,
                )
            )))
            setChannelRecords(nextChannels)
            setGroupRecords(nextGroups)
            setConversationRecords((current) => mergeClocktowerConversationRecords([...current, ...nextConversations]))
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSurfaceLoading(false)
        }
    }, [contextIds, gameId, roomId])

    const loadHistory = useCallback(async (conversation: ClocktowerConversationResponse, afterSeq?: number | null) => {
        setLoading(true)
        try {
            const page = await listImMessages(conversation.conversationId, {
                page: afterSeq ? undefined : clocktowerChatMessagePage(conversation),
                size: MESSAGE_PAGE_SIZE,
                afterSeq: afterSeq ?? undefined,
            })
            const nextRecords = page.records.map(mapImMessageToClocktower)
            setMessageState((current) => {
                if (afterSeq && current.conversationId === conversation.conversationId) {
                    return {
                        conversationId: conversation.conversationId,
                        records: nextRecords.reduce(mergeClocktowerPushedMessage, current.records),
                    }
                }
                return {
                    conversationId: conversation.conversationId,
                    records: nextRecords,
                }
            })
            const lastMessage = nextRecords.at(-1)
            if (lastMessage && shouldMarkClocktowerChatRead(viewerMode, conversation)) {
                void markImRead(conversation.conversationId, {messageSeq: lastMessage.messageSeq})
                    .then(updateConversationUnread)
                    .catch(reportGlobalError)
            }
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [viewerMode])

    const handleSendAck = useCallback((message: MessageView) => {
        const nextMessage = mapImMessageToClocktower(message)
        setMessageState((current) => ({
            conversationId: current.conversationId,
            records: current.conversationId === nextMessage.conversationId
                ? mergeClocktowerAckMessage(current.records, nextMessage)
                : current.records,
        }))
        setConversationRecords((current) => mergeConversationMessage(current, nextMessage))
    }, [])

    const handleMessagePush = useCallback((payload: ImMessagePushPayload) => {
        const pushAction = resolveClocktowerMessagePushAction(payload, activeConversationKey ?? null)
        if (pushAction === 'LOAD_ACTIVE_HISTORY') {
            const conversation = visibleConversations.find((item) => item.conversationId === payload.conversationId)
                ?? activeConversation
            if (conversation) {
                void loadHistory(conversation, confirmedMessageSeq > 0 ? confirmedMessageSeq : null).catch(reportGlobalError)
            }
            void loadImState().catch(reportGlobalError)
            return
        }
        if (pushAction === 'REFRESH_CONVERSATIONS') {
            void loadImState().catch(reportGlobalError)
            return
        }
        if (!payload.message) {
            return
        }
        const nextMessage = mapImMessageToClocktower(payload.message)
        setMessageState((current) => ({
            conversationId: current.conversationId,
            records: current.conversationId === nextMessage.conversationId
                ? mergeClocktowerPushedMessage(current.records, nextMessage)
                : current.records,
        }))
        setConversationRecords((current) => mergeConversationMessage(current, nextMessage))
    }, [activeConversation, activeConversationKey, confirmedMessageSeq, loadHistory, loadImState, visibleConversations])

    const handleReadUpdate = useCallback((unread: UnreadView) => {
        updateConversationUnread(unread)
    }, [])

    const handleResync = useCallback((payload: {conversationId?: number | null; messageSeq?: number | null}) => {
        const conversation = visibleConversations.find((item) => item.conversationId === payload.conversationId)
            ?? activeConversation
        if (conversation) {
            void loadHistory(conversation, payload.messageSeq ?? confirmedMessageSeq).catch(reportGlobalError)
        }
        void loadImState().catch(reportGlobalError)
    }, [activeConversation, confirmedMessageSeq, loadHistory, loadImState, visibleConversations])

    const imSocket = useImSocket({
        activeConversationId: activeConversationKey,
        enabled: Boolean(activeConversationKey),
        lastSeq: confirmedMessageSeq,
        onError: reportGlobalError,
        onMessagePush: handleMessagePush,
        onReadUpdate: handleReadUpdate,
        onResync: handleResync,
        onSendAck: handleSendAck,
    })

    useEffect(() => {
        if (conversations.length > 0) {
            setConversationRecords(conversations)
        }
    }, [conversations])

    useEffect(() => {
        if (channels.length > 0) {
            setChannelRecords(channels)
        }
    }, [channels])

    useEffect(() => {
        if (groups.length > 0) {
            setGroupRecords(groups)
        }
    }, [groups])

    useEffect(() => {
        void loadImState()
    }, [loadImState])

    useEffect(() => {
        if (activeTabKey === SURFACE_TAB_KEY) {
            void loadImState()
        }
    }, [activeTabKey, loadImState])

    useEffect(() => {
        if (!activeConversation || activeConversation.conversationId === activeConversationId) {
            return
        }
        setActiveConversationId(activeConversation.conversationId)
    }, [activeConversation, activeConversationId])

    useEffect(() => {
        const conversationId = activeConversationKey ?? null
        setMessageState((current) => current.conversationId === conversationId
            ? current
            : {conversationId, records: []})
    }, [activeConversationKey])

    useEffect(() => {
        if (!activeConversation) {
            setMessageState({conversationId: null, records: []})
            return
        }
        if (activeTabKey !== MESSAGE_TAB_KEY) {
            setLoading(false)
            return
        }
        void loadHistory(activeConversation)
    }, [activeConversation, activeTabKey, loadHistory])

    async function sendMessage() {
        if (!activeConversation || policy?.readOnly) {
            return
        }
        const trimmed = content.trim()
        if (!trimmed) {
            return
        }
        const clientMsgId = createClocktowerClientMsgId()
        const sendingConversationId = activeConversation.conversationId
        const pending = createPendingClocktowerMessage(
            sendingConversationId,
            trimmed,
            clientMsgId,
            Math.max(activeConversation.messageSeq, displayMessageSeq) + 1,
        )
        setSending(true)
        setMessageState((current) => ({
            conversationId: current.conversationId,
            records: appendClocktowerSentMessage(
                current.records,
                pending,
                current.conversationId,
                sendingConversationId,
            ),
        }))
        setContent((currentContent) => currentContent === trimmed ? '' : currentContent)
        const sendRequest = {
            conversationId: sendingConversationId,
            clientMsgId,
            messageType: 'TEXT',
            content: trimmed,
        }
        const queued = imSocket.sendMessage(sendRequest)
        if (queued) {
            setSending(false)
            return
        }
        try {
            const sentMessage = mapImMessageToClocktower(await sendImMessage(sendRequest))
            setMessageState((current) => ({
                conversationId: current.conversationId,
                records: current.conversationId === sentMessage.conversationId
                    ? mergeClocktowerAckMessage(current.records, sentMessage)
                    : current.records,
            }))
            setConversationRecords((current) => mergeConversationMessage(current, sentMessage))
        } catch (caught) {
            setMessageState((current) => ({
                conversationId: current.conversationId,
                records: markClocktowerPendingMessageFailed(current.records, clientMsgId),
            }))
            reportGlobalError(caught)
        } finally {
            setSending(false)
        }
    }

    async function applyToSurface(surface: SurfaceIdentity) {
        const result = await createImJoinRequest(surface)
        await loadImState()
        return result
    }

    async function cancelJoinRequest(requestId: number) {
        const result = await cancelImJoinRequest(requestId)
        await loadImState()
        return result
    }

    async function approveJoinRequest(requestId: number) {
        const result = await approveImJoinRequest(requestId)
        await loadImState()
        return result
    }

    async function rejectJoinRequest(requestId: number) {
        const result = await rejectImJoinRequest(requestId)
        await loadImState()
        return result
    }

    async function leaveSurface(surface: SurfaceIdentity) {
        await leaveImSurface(surface.surfaceType, surface.surfaceId)
        await loadImState()
    }

    return (
        <Card
            extra={(
                <Button
                    aria-label="刷新聊天"
                    icon={<ReloadOutlined/>}
                    loading={surfaceLoading}
                    onClick={voidify(loadImState)}
                    size="small"
                />
            )}
            size="small"
            title={title}
        >
            <Tabs
                activeKey={activeTabKey}
                items={[
                    {
                        key: 'conversations',
                        label: '会话',
                        children: visibleConversations.length > 0 ? (
                            <ClocktowerConversationList
                                activeConversationId={activeConversation?.conversationId}
                                conversations={visibleConversations}
                                getPolicy={(conversation) => resolveClocktowerChatPolicy(viewerMode, conversation, phase)}
                                onSelect={(conversation) => {
                                    setActiveConversationId(conversation.conversationId)
                                    setActiveTabKey(MESSAGE_TAB_KEY)
                                }}
                            />
                        ) : <Empty description="暂无可见会话"/>,
                    },
                    {
                        key: MESSAGE_TAB_KEY,
                        label: activeConversation ? conversationLabel(activeConversation) : '消息',
                        forceRender: true,
                        children: (
                            <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                                {activeSurface?.announcement && (
                                    <Alert
                                        banner
                                        title={activeSurface.announcement}
                                        type="info"
                                    />
                                )}
                                {policy?.readOnly && (
                                    <Alert
                                        showIcon
                                        title={policy.reason}
                                        type="info"
                                    />
                                )}
                                <ClocktowerMessageList loading={loading} messages={messages}/>
                                <Input.TextArea
                                    aria-label="聊天内容"
                                    autoSize={{minRows: 2, maxRows: 4}}
                                    disabled={!activeConversation || policy?.readOnly || sending}
                                    onChange={(event) => setContent(event.target.value)}
                                    placeholder={policy?.readOnly ? '当前会话只读' : '输入消息'}
                                    value={content}
                                />
                                <Button
                                    disabled={!activeConversation || policy?.readOnly || content.trim().length === 0}
                                    icon={<SendOutlined/>}
                                    loading={sending}
                                    onClick={voidify(sendMessage)}
                                    type="primary"
                                >
                                    发送
                                </Button>
                            </Space>
                        ),
                    },
                    {
                        key: SURFACE_TAB_KEY,
                        label: '频道',
                        forceRender: true,
                        children: (
                            <DiscoverableSurfaceList
                                channels={discoverableSurfaces.channels}
                                groups={discoverableSurfaces.groups}
                                loading={surfaceLoading}
                                onApply={applyToSurface}
                                onApprove={approveJoinRequest}
                                onCancel={cancelJoinRequest}
                                onLeave={leaveSurface}
                                onReject={rejectJoinRequest}
                            />
                        ),
                    },
                ]}
                onChange={setActiveTabKey}
            />
        </Card>
    )

    function updateConversationUnread(unread: UnreadView) {
        setConversationRecords((current) => current.map((conversation) => conversation.conversationId === unread.conversationId
            ? {...conversation, unreadCount: unread.unreadCount, messageSeq: Math.max(conversation.messageSeq, unread.lastReadSeq)}
            : conversation))
    }
}

function DiscoverableSurfaceList({
    channels,
    groups,
    loading,
    onApply,
    onApprove,
    onCancel,
    onLeave,
    onReject,
}: {
    channels: ChannelView[]
    groups: GroupView[]
    loading: boolean
    onApply: (surface: SurfaceIdentity) => Promise<JoinResultView>
    onApprove: (requestId: number) => Promise<JoinResultView>
    onCancel: (requestId: number) => Promise<JoinResultView>
    onLeave: (surface: SurfaceIdentity) => Promise<void>
    onReject: (requestId: number) => Promise<JoinResultView>
}) {
    const surfaces = [
        ...channels.map((channel) => ({surfaceType: 'CHANNEL' as const, surfaceId: channel.id, surface: channel})),
        ...groups.map((group) => ({surfaceType: 'GROUP' as const, surfaceId: group.id, surface: group})),
    ]
    return (
        <List
            dataSource={surfaces}
            loading={loading}
            locale={{emptyText: <Empty description="暂无可加入频道"/>}}
            renderItem={(item) => (
                <List.Item>
                    <Space orientation="vertical" size={6} style={{width: '100%'}}>
                        <Space wrap>
                            <Typography.Text strong>{item.surface.name}</Typography.Text>
                            <Tag>{item.surfaceType === 'CHANNEL' ? '频道' : '分组'}</Tag>
                            {item.surface.announcement && <Typography.Text type="secondary">{item.surface.announcement}</Typography.Text>}
                        </Space>
                        <ImJoinApplyControls
                            currentMemberRole={item.surface.memberRole}
                            currentMembershipStatus={item.surface.membershipStatus ?? 'NONE'}
                            joinPolicy={item.surface.joinPolicy}
                            onApply={onApply}
                            onApprove={onApprove}
                            onCancel={onCancel}
                            onLeave={onLeave}
                            onReject={onReject}
                            pendingRequestId={pendingRequestId(item.surface)}
                            pendingReviewRequests={pendingReviewRequests(item.surface)}
                            surface={{surfaceType: item.surfaceType, surfaceId: item.surfaceId}}
                        />
                    </Space>
                </List.Item>
            )}
            rowKey={(item) => `${item.surfaceType}:${item.surfaceId}`}
            size="small"
        />
    )
}

export function filterClocktowerConversations(
    conversations: ClocktowerConversationResponse[],
    viewerMode: ClocktowerViewerMode,
    gameId?: number | null,
) {
    const scopedConversations = gameId == null
        ? conversations
        : conversations.filter((conversation) => conversation.gameId == null || conversation.gameId === gameId)
    return scopedConversations.filter((conversation) => {
        const groupKey = normalizeConversationGroup(conversation)
        if (viewerMode === 'STORYTELLER') {
            return groupKey !== 'SPECTATOR'
        }
        if (viewerMode === 'SPECTATOR') {
            return groupKey === 'PUBLIC' || groupKey === 'SPECTATOR' || groupKey === 'SYSTEM'
        }
        if (viewerMode === 'PLAYER') {
            return groupKey === 'PUBLIC' || groupKey === 'PRIVATE' || groupKey === 'SYSTEM'
        }
        return groupKey === 'PUBLIC' || groupKey === 'SYSTEM'
    })
}

export function filterDiscoverableClocktowerSurfaces(
    channels: ChannelView[],
    groups: GroupView[],
    conversations: ClocktowerConversationResponse[],
    viewerMode: ClocktowerViewerMode,
    gameId?: number | null,
): DiscoverableSurfaces {
    const conversationIds = new Set(filterClocktowerConversations(conversations, viewerMode, gameId)
        .map((conversation) => conversation.conversationId))
    return {
        channels: channels.filter((channel) => !channel.mainConversationId || !conversationIds.has(channel.mainConversationId)),
        groups: groups.filter((group) => !group.conversationId || !conversationIds.has(group.conversationId)),
    }
}

export function resolveClocktowerChatPolicy(
    viewerMode: ClocktowerViewerMode,
    conversation: ClocktowerConversationResponse,
    phase: string,
): ClocktowerChatPolicy {
    const groupKey = normalizeConversationGroup(conversation)
    const canPost = conversationSurfaceCanPost(conversation)
    if (canPost === false) {
        return {readOnly: true, reason: '当前成员不可发言'}
    }
    if (viewerMode === 'PLAYER') {
        if (groupKey === 'PUBLIC' || groupKey === 'PRIVATE') {
            if (isNightPhase(phase)) {
                return {readOnly: true, reason: '夜晚阶段不可发言'}
            }
            return {readOnly: false, reason: '可发言'}
        }
        return {readOnly: true, reason: '系统会话只读'}
    }
    if (viewerMode === 'SPECTATOR') {
        if (groupKey === 'SPECTATOR') {
            return {readOnly: false, reason: '可发言'}
        }
        return {readOnly: true, reason: '旁观者只能查看玩家公聊'}
    }
    if (viewerMode === 'STORYTELLER') {
        if (groupKey === 'PUBLIC' || groupKey === 'SYSTEM') {
            return {readOnly: false, reason: '可发言'}
        }
        return {readOnly: true, reason: '玩家私聊监控'}
    }
    return {readOnly: true, reason: '当前视角只读'}
}

export function shouldMarkClocktowerChatRead(
    viewerMode: ClocktowerViewerMode,
    conversation: ClocktowerConversationResponse,
) {
    const groupKey = normalizeConversationGroup(conversation)
    if (viewerMode === 'PLAYER') {
        return groupKey === 'PUBLIC' || groupKey === 'PRIVATE'
    }
    if (viewerMode === 'SPECTATOR') {
        return groupKey === 'SPECTATOR'
    }
    return false
}

export function clocktowerChatMessagePage(conversation: Pick<ClocktowerConversationResponse, 'messageSeq'>) {
    return Math.max(1, Math.ceil(conversation.messageSeq / MESSAGE_PAGE_SIZE))
}

export function buildClocktowerContextIds(roomId?: number | null, gameId?: number | null) {
    return [roomId, gameId].filter((contextId, index, contextIds): contextId is number => (
        contextId != null && contextIds.indexOf(contextId) === index
    ))
}

export function appendClocktowerSentMessage(
    current: ClocktowerMessageResponse[],
    message: ClocktowerMessageResponse,
    activeConversationId: number | null,
    sendingConversationId: number,
) {
    if (activeConversationId !== sendingConversationId) {
        return current
    }
    return [...current, message]
}

export function mergeClocktowerAckMessage(
    current: ClocktowerMessageResponse[],
    message: ClocktowerMessageResponse,
) {
    const pendingIndex = current.findIndex((item) => item.clientMsgId && item.clientMsgId === message.clientMsgId)
    if (pendingIndex >= 0) {
        return current.map((item, index) => index === pendingIndex ? message : item)
    }
    return mergeClocktowerPushedMessage(current, message)
}

export function mergeClocktowerPushedMessage(
    current: ClocktowerMessageResponse[],
    message: ClocktowerMessageResponse,
) {
    const existingIndex = current.findIndex((item) => (
        item.messageId === message.messageId
        || (item.clientMsgId && item.clientMsgId === message.clientMsgId)
        || (item.conversationId === message.conversationId && item.messageSeq === message.messageSeq)
    ))
    if (existingIndex >= 0) {
        const existing = current[existingIndex]
        if (existing.messageId === message.messageId && existing.status !== 'PENDING') {
            return current
        }
        return current.map((item, index) => index === existingIndex ? message : item)
    }
    return [...current, message].sort((left, right) => left.messageSeq - right.messageSeq)
}

export function markClocktowerPendingMessageFailed(
    current: ClocktowerMessageResponse[],
    clientMsgId: string,
) {
    return current.map((message) => message.clientMsgId === clientMsgId && message.status === 'PENDING'
        ? {
            ...message,
            status: 'FAILED',
            imMessage: message.imMessage
                ? {...message.imMessage, status: 'FAILED'}
                : message.imMessage,
        }
        : message)
}

export function resolveClocktowerConfirmedMessageSeq(
    messages: ClocktowerMessageResponse[],
    conversation?: Pick<ClocktowerConversationResponse, 'messageSeq'> | null,
) {
    return messages.reduce((maxSeq, message) => (
        message.status === 'PENDING' || message.status === 'FAILED'
            ? maxSeq
            : Math.max(maxSeq, message.messageSeq)
    ), conversation?.messageSeq ?? 0)
}

export function mergeClocktowerConversationRecords(conversations: ClocktowerConversationResponse[]) {
    const merged = new Map<number, ClocktowerConversationResponse>()
    for (const conversation of conversations) {
        const existing = merged.get(conversation.conversationId)
        if (!existing) {
            merged.set(conversation.conversationId, conversation)
            continue
        }
        merged.set(conversation.conversationId, {
            ...existing,
            ...conversation,
            participantKey: conversation.participantKey ?? existing.participantKey,
            channelKey: conversation.channelKey || existing.channelKey,
            groupKey: conversation.groupKey || existing.groupKey,
            lastMessage: conversation.lastMessage ?? existing.lastMessage,
            unreadCount: conversation.unreadCount ?? existing.unreadCount,
            messageSeq: Math.max(existing.messageSeq, conversation.messageSeq),
        })
    }
    return Array.from(merged.values())
}

export function resolveClocktowerMessagePushAction(
    payload: ImMessagePushPayload,
    activeConversationId?: number | null,
): ClocktowerMessagePushAction {
    if (payload.message) {
        return 'MERGE_MESSAGE'
    }
    if (payload.eventType === 'READ_UPDATED') {
        return 'REFRESH_CONVERSATIONS'
    }
    if (payload.conversationId != null && payload.conversationId === activeConversationId) {
        return 'LOAD_ACTIVE_HISTORY'
    }
    return 'REFRESH_CONVERSATIONS'
}

function mapImConversationsToClocktower(
    conversations: ConversationView[],
    channels: ChannelView[],
    groups: GroupView[],
    roomId: number,
    gameId?: number | null,
) {
    return conversations.map((conversation) => enrichConversationFromSurface(
        mapImConversationToClocktower(conversation, roomId),
        channels,
        groups,
        gameId,
    ))
}

function enrichConversationFromSurface(
    conversation: ClocktowerConversationResponse,
    channels: ChannelView[],
    groups: GroupView[],
    gameId?: number | null,
) {
    const surface = surfaceForConversation(conversation, channels, groups)
    if (!surface) {
        return {
            ...conversation,
            gameId: gameId === undefined ? conversation.gameId : gameId,
        }
    }
    return {
        ...conversation,
        gameId: gameId === undefined ? conversation.gameId : gameId,
        channelKey: 'channelKey' in surface ? surface.channelKey : conversation.channelKey,
        groupKey: 'groupKey' in surface ? surface.groupKey : conversation.groupKey,
    }
}

function surfaceForConversation(
    conversation: ClocktowerConversationResponse,
    channels: ChannelView[],
    groups: GroupView[],
) {
    const group = groups.find((item) => (
        item.conversationId === conversation.conversationId
        || (conversation.ownerSurfaceType === 'GROUP' && conversation.ownerSurfaceId === item.id)
    ))
    if (group) {
        return group
    }
    return channels.find((item) => (
        item.mainConversationId === conversation.conversationId
        || (conversation.ownerSurfaceType === 'CHANNEL' && conversation.ownerSurfaceId === item.id)
    )) ?? null
}

function mergeConversationMessage(
    conversations: ClocktowerConversationResponse[],
    message: ClocktowerMessageResponse,
) {
    return conversations.map((conversation) => conversation.conversationId === message.conversationId
        ? {
            ...conversation,
            lastMessage: message.imMessage,
            lastMessageAt: message.sentAt,
            lastActiveAt: message.sentAt,
            messageSeq: Math.max(conversation.messageSeq, message.messageSeq),
        }
        : conversation)
}

function mergeRecordsById<RecordType extends {id: number}>(records: RecordType[]) {
    return Array.from(new Map(records.map((record) => [record.id, record])).values())
}

async function listClocktowerSurfaceGroups(contextId: number, channels: ChannelView[]) {
    const groupLists = await Promise.all([
        listImGroups({contextType: CLOCKTOWER_IM_CONTEXT_TYPE, contextId}),
        ...channels.map((channel) => listImGroups({channelId: channel.id})),
    ])
    return groupLists.flat()
}

function normalizeConversationGroup(conversation: ClocktowerConversationResponse) {
    const groupKey = (conversation.groupKey || conversation.channelKey || conversation.conversationType).toUpperCase()
    return groupKey.startsWith('PRIVATE') ? 'PRIVATE' : groupKey
}

function conversationSurfaceCanPost(conversation: ClocktowerConversationResponse) {
    if (conversation.imConversation?.status === 'MUTED') {
        return false
    }
    return undefined
}

function isNightPhase(phase: string) {
    return phase === 'NIGHT' || phase === 'FIRST_NIGHT'
}

function pendingRequestId(surface: ChannelView | GroupView) {
    return (surface as {pendingRequestId?: number | null}).pendingRequestId
}

function pendingReviewRequests(surface: ChannelView | GroupView): PendingReviewRequest[] {
    return (surface as {pendingReviewRequests?: PendingReviewRequest[]}).pendingReviewRequests ?? []
}
