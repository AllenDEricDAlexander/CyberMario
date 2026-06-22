import {App, Space, Switch, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    applyAgentChunkToMessage,
    ChatWorkspace,
    type ChatWorkspaceMessage,
    type ChatWorkspaceRequest,
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
    markMessageSucceeded,
    useXChatWorkspace,
} from '../../../components/chat-workspace'
import {resolveErrorMessage} from '../../../services/request'
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
    getAgentMemoryMessages,
    getAgentMemorySessions,
} from '../../agent/agentService'
import type {AgentMemorySessionResponse} from '../../agent/agentTypes'
import {streamChatResponse} from '../chatService'

const initialMessages: ChatWorkspaceMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: 'Hello, I am CyberMario. This session is connecting Cyber Mario Agent.',
        status: 'success',
    },
]

type UpdateAssistantMessage = (
    assistantId: string,
    updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage
) => boolean

export function ChatPage() {
    const {message: appMessage} = App.useApp()
    const [input, setInput] = useState('')
    const [sessionId, setSessionId] = useState('')
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [sessionLoading, setSessionLoading] = useState(false)
    const [memoryEnabled, setMemoryEnabled] = useState(true)
    const [error, setError] = useState('')
    const abortControllerRef = useRef<AbortController | null>(null)
    const updateAssistantMessageRef = useRef<UpdateAssistantMessage | null>(null)
    const historyRequestSeqRef = useRef(0)
    const isMountedRef = useRef(true)

    const conversations = useMemo(
        () => sessions.map(mapSessionToConversation),
        [sessions]
    )
    const threadLabel = useMemo(() => sessionId || 'New Session', [sessionId])

    function nextHistoryRequestToken() {
        historyRequestSeqRef.current += 1
        return historyRequestSeqRef.current
    }

    useEffect(() => {
        isMountedRef.current = true
        return () => {
            isMountedRef.current = false
            nextHistoryRequestToken()
        }
    }, [])

    const handleWorkspaceRequest = useCallback(async (
        requestParams: ChatWorkspaceRequest,
        assistantId: string
    ) => {
        const abortController = new AbortController()
        abortControllerRef.current = abortController

        try {
            await streamChatResponse(
                {
                    message: requestParams.message,
                    sessionId: requestParams.conversationKey,
                    memoryEnabled,
                    signal: abortController.signal,
                },
                (chunk) => {
                    if (chunk.threadId) {
                        setSessionId(chunk.threadId)
                    }
                    updateAssistantMessageRef.current?.(
                        assistantId,
                        current => applyAgentChunkToMessage(current, chunk)
                    )
                },
            )
            updateAssistantMessageRef.current?.(assistantId, markMessageSucceeded)
        } catch (requestError) {
            if (abortController.signal.aborted) {
                return
            }

            const errorMessage = resolveErrorMessage(requestError)
            setError(errorMessage)
            reportGlobalError(requestError)
            throw new Error(errorMessage, {cause: requestError})
        } finally {
            if (abortControllerRef.current === abortController) {
                abortControllerRef.current = null
            }
        }
    }, [memoryEnabled])

    const handleWorkspaceAbort = useCallback(() => {
        abortControllerRef.current?.abort()
        abortControllerRef.current = null
    }, [])

    const {
        messages,
        setMessages,
        updateAssistantMessage,
        request,
        abort,
        isRequesting,
    } = useXChatWorkspace({
        conversationKey: sessionId || undefined,
        defaultMessages: initialMessages,
        onRequest: handleWorkspaceRequest,
        onAbort: handleWorkspaceAbort,
    })

    const loadSessions = useCallback(async () => {
        if (isMountedRef.current) {
            setSessionLoading(true)
        }
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'AGENT_CHAT'})
            if (!isMountedRef.current) {
                return
            }
            setSessions(page.records)
        } catch (requestError) {
            if (!isMountedRef.current) {
                return
            }
            reportGlobalError(requestError)
        } finally {
            if (isMountedRef.current) {
                setSessionLoading(false)
            }
        }
    }, [])

    useEffect(() => {
        void loadSessions()
    }, [loadSessions])

    useEffect(() => {
        updateAssistantMessageRef.current = updateAssistantMessage
    }, [updateAssistantMessage])

    function handleSend(message: string) {
        const nextMessage = message.trim()
        if (!nextMessage || isRequesting) {
            return
        }

        setInput('')
        setError('')
        nextHistoryRequestToken()
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'AGENT_CHAT',
        })
    }

    async function handleNewConversation() {
        abort()
        const requestToken = nextHistoryRequestToken()
        try {
            const session = await createAgentMemorySession({entryType: 'AGENT_CHAT', memoryEnabled})
            if (!isMountedRef.current) {
                return
            }
            setSessions((current) => [session, ...current.filter((item) => item.sessionId !== session.sessionId)])
            if (historyRequestSeqRef.current === requestToken) {
                setSessionId(session.sessionId)
            }
        } catch (requestError) {
            if (!isMountedRef.current) {
                return
            }
            reportGlobalError(requestError)
            if (historyRequestSeqRef.current === requestToken) {
                setSessionId('')
            }
        }
        if (!isMountedRef.current || historyRequestSeqRef.current !== requestToken) {
            return
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
    }

    async function archiveCurrentSession(conversationKey = sessionId) {
        if (!conversationKey) {
            return
        }

        const isActiveConversation = conversationKey === sessionId
        if (isActiveConversation) {
            abort()
        }
        const requestToken = nextHistoryRequestToken()
        try {
            await archiveAgentMemorySession(conversationKey)
            if (!isMountedRef.current) {
                return
            }
            appMessage.success('会话已归档')
            await loadSessions()
            if (isMountedRef.current && isActiveConversation && historyRequestSeqRef.current === requestToken) {
                setSessionId('')
                setMessages(initialMessages)
                setError('')
            }
        } catch (requestError) {
            if (!isMountedRef.current) {
                return
            }
            reportGlobalError(requestError)
        }
    }

    function handleConversationChange(conversationKey: string) {
        void loadConversationHistory(conversationKey)
    }

    async function loadConversationHistory(conversationKey: string) {
        abort()
        setSessionId(conversationKey)
        const requestToken = nextHistoryRequestToken()
        const session = sessions.find((item) => item.sessionId === conversationKey)
        if (session) {
            setMemoryEnabled(session.memoryEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')

        try {
            const historyMessages = mapMemoryMessagesToWorkspaceMessages(
                await getAgentMemoryMessages(conversationKey)
            )
            if (historyRequestSeqRef.current === requestToken) {
                setMessages(historyMessages.length > 0 ? historyMessages : initialMessages)
            }
        } catch (requestError) {
            if (historyRequestSeqRef.current === requestToken) {
                setMessages(initialMessages)
                reportGlobalError(requestError)
            }
        }
    }

    async function handleCopyMessage(message: ChatWorkspaceMessage) {
        const content = message.content.trim()
        if (!content) {
            return
        }

        try {
            if (!navigator.clipboard?.writeText) {
                throw new Error('Clipboard is unavailable.')
            }
            await navigator.clipboard.writeText(content)
            appMessage.success('已复制')
        } catch {
            reportGlobalError('复制失败')
        }
    }

    function handleReloadMessage(message: ChatWorkspaceMessage) {
        const question = typeof message.question === 'string'
            ? message.question.trim()
            : message.role === 'user'
                ? message.content.trim()
                : ''

        if (!question || isRequesting) {
            return
        }

        setError('')
        nextHistoryRequestToken()
        request({
            message: question,
            conversationKey: sessionId || undefined,
            entryType: 'AGENT_CHAT',
        })
    }

    const subtitle = error ? (
        <Space direction="vertical" size={0}>
            <span>面向当前 Java agent 的对话工作台。</span>
            <Typography.Text type="danger">{error}</Typography.Text>
        </Space>
    ) : '面向当前 Java agent 的对话工作台。'

    return (
        <ChatWorkspace
            activeConversationKey={sessionId || undefined}
            brandDescription="Agent memory sessions"
            brandTitle="CyberMario"
            conversations={conversations}
            headerActions={(
                <Space wrap>
                    <Switch
                        checked={memoryEnabled}
                        checkedChildren="Memory"
                        unCheckedChildren="Memory"
                        onChange={setMemoryEnabled}
                    />
                    <Tag>{threadLabel}</Tag>
                </Space>
            )}
            input={input}
            messages={messages}
            sending={isRequesting}
            sidebarLoading={sessionLoading}
            subtitle={subtitle}
            title="Agent Chat"
            onArchiveConversation={(conversationKey) => void archiveCurrentSession(conversationKey)}
            onConversationChange={handleConversationChange}
            onCopyMessage={(message) => void handleCopyMessage(message)}
            onInputChange={setInput}
            onNewConversation={() => void handleNewConversation()}
            onReloadConversations={() => void loadSessions()}
            onReloadMessage={handleReloadMessage}
            onSend={handleSend}
            onStop={abort}
        />
    )
}
