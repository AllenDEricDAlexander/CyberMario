import {SendOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Empty, Input, Space, Tabs} from 'antd'
import {useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {voidify} from '../../../utils/async'
import {
    listClocktowerChatMessages,
    markClocktowerChatRead,
    sendClocktowerChatMessage,
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

const MESSAGE_TAB_KEY = 'messages'
const MESSAGE_PAGE_SIZE = 50

type ClocktowerChatPanelProps = {
    conversations: ClocktowerConversationResponse[]
    gameId?: number | null
    phase: string
    viewerMode: ClocktowerViewerMode
    title?: string
}

export function ClocktowerChatPanel({
    conversations,
    gameId,
    phase,
    title = '聊天',
    viewerMode,
}: ClocktowerChatPanelProps) {
    const visibleConversations = useMemo(
        () => filterClocktowerConversations(conversations, viewerMode, gameId),
        [conversations, gameId, viewerMode],
    )
    const [activeTabKey, setActiveTabKey] = useState('conversations')
    const [activeConversationId, setActiveConversationId] = useState<number | null>(
        visibleConversations[0]?.conversationId ?? null,
    )
    const activeConversation = visibleConversations.find(
        (conversation) => conversation.conversationId === activeConversationId,
    ) ?? visibleConversations[0] ?? null
    const [messageState, setMessageState] = useState<ClocktowerChatMessageState>({
        conversationId: null,
        records: [],
    })
    const [loading, setLoading] = useState(false)
    const [sending, setSending] = useState(false)
    const [content, setContent] = useState('')
    const policy = activeConversation ? resolveClocktowerChatPolicy(viewerMode, activeConversation, phase) : null
    const activeConversationKey = activeConversation?.conversationId
    const messages = messageState.conversationId === activeConversationKey ? messageState.records : []

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
        if (!activeConversationKey) {
            setMessageState({conversationId: null, records: []})
            return
        }
        if (activeTabKey !== MESSAGE_TAB_KEY) {
            setLoading(false)
            return
        }
        let cancelled = false
        setLoading(true)
        void listClocktowerChatMessages(activeConversationKey, {
            page: clocktowerChatMessagePage(activeConversation),
            size: MESSAGE_PAGE_SIZE,
        })
            .then((response) => {
                if (cancelled) {
                    return
                }
                setMessageState({
                    conversationId: activeConversationKey,
                    records: response.records,
                })
                const lastMessage = response.records.at(-1)
                if (lastMessage && shouldMarkClocktowerChatRead(viewerMode, activeConversation)) {
                    void markClocktowerChatRead(activeConversationKey, {
                        messageSeq: lastMessage.messageSeq,
                    }).catch(reportGlobalError)
                }
            })
            .catch(reportGlobalError)
            .finally(() => {
                if (!cancelled) {
                    setLoading(false)
                }
            })
        return () => {
            cancelled = true
        }
    }, [activeConversation, activeConversationKey, activeTabKey, viewerMode])

    async function sendMessage() {
        if (!activeConversation || policy?.readOnly) {
            return
        }
        const trimmed = content.trim()
        if (!trimmed) {
            return
        }
        setSending(true)
        const sendingConversationId = activeConversation.conversationId
        try {
            const message = await sendClocktowerChatMessage(sendingConversationId, {content: trimmed})
            setMessageState((current) => ({
                conversationId: current.conversationId,
                records: appendClocktowerSentMessage(
                    current.records,
                    message,
                    current.conversationId,
                    sendingConversationId,
                ),
            }))
            setContent((currentContent) => currentContent === trimmed ? '' : currentContent)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSending(false)
        }
    }

    if (visibleConversations.length === 0) {
        return (
            <Card size="small" title={title}>
                <Empty description="暂无可见会话"/>
            </Card>
        )
    }

    return (
        <Card size="small" title={title}>
            <Tabs
                activeKey={activeTabKey}
                items={[
                    {
                        key: 'conversations',
                        label: '会话',
                        children: (
                            <ClocktowerConversationList
                                activeConversationId={activeConversation?.conversationId}
                                conversations={visibleConversations}
                                getPolicy={(conversation) => resolveClocktowerChatPolicy(viewerMode, conversation, phase)}
                                onSelect={(conversation) => setActiveConversationId(conversation.conversationId)}
                            />
                        ),
                    },
                    {
                        key: 'messages',
                        label: activeConversation ? conversationLabel(activeConversation) : '消息',
                        children: (
                            <Space orientation="vertical" size="middle" style={{width: '100%'}}>
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
                ]}
                onChange={setActiveTabKey}
            />
        </Card>
    )
}

export function filterClocktowerConversations(
    conversations: ClocktowerConversationResponse[],
    viewerMode: ClocktowerViewerMode,
    gameId?: number | null,
) {
    const scopedConversations = gameId == null
        ? conversations
        : conversations.filter((conversation) => conversation.gameId === gameId)
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

export function resolveClocktowerChatPolicy(
    viewerMode: ClocktowerViewerMode,
    conversation: ClocktowerConversationResponse,
    phase: string,
): ClocktowerChatPolicy {
    const groupKey = normalizeConversationGroup(conversation)
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

function normalizeConversationGroup(conversation: ClocktowerConversationResponse) {
    return (conversation.groupKey || conversation.channelKey || conversation.conversationType).toUpperCase()
}

function isNightPhase(phase: string) {
    return phase === 'NIGHT' || phase === 'FIRST_NIGHT'
}
