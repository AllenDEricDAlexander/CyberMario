import {App, Space, Switch, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    applyAgentChunkToMessage,
    ChatWorkspace,
    type ChatWorkspaceMessage,
    type ChatWorkspaceRequest,
    mapSessionToConversation,
    markMessageSucceeded,
    useXChatWorkspace,
} from '../../../components/chat-workspace'
import {resolveErrorMessage} from '../../../services/request'
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
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

    const conversations = useMemo(
        () => sessions.map(mapSessionToConversation),
        [sessions]
    )
    const threadLabel = useMemo(() => sessionId || 'New Session', [sessionId])

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
        setSessionLoading(true)
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'AGENT_CHAT'})
            setSessions(page.records)
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setSessionLoading(false)
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
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'AGENT_CHAT',
        })
    }

    async function handleNewConversation() {
        abort()
        try {
            const session = await createAgentMemorySession({entryType: 'AGENT_CHAT', memoryEnabled})
            setSessionId(session.sessionId)
            setSessions((current) => [session, ...current.filter((item) => item.sessionId !== session.sessionId)])
        } catch (requestError) {
            reportGlobalError(requestError)
            setSessionId('')
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
    }

    async function archiveCurrentSession(conversationKey = sessionId) {
        if (!conversationKey) {
            return
        }

        try {
            await archiveAgentMemorySession(conversationKey)
            appMessage.success('会话已归档')
            setSessionId('')
            setMessages(initialMessages)
            setError('')
            await loadSessions()
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    function handleConversationChange(conversationKey: string) {
        abort()
        setSessionId(conversationKey)
        const session = sessions.find((item) => item.sessionId === conversationKey)
        if (session) {
            setMemoryEnabled(session.memoryEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
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
