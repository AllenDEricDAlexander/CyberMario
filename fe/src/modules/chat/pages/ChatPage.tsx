import {SendOutlined, StopOutlined} from '@ant-design/icons'
import {App, Avatar, Button, Card, Input, Space, Tag, Typography} from 'antd'
import {type FormEvent, useEffect, useMemo, useRef, useState} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {PageToolbar} from '../../../components/PageToolbar'
import {VisualBackdrop} from '../../../components/VisualBackdrop'
import {resolveErrorMessage} from '../../../services/request'
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
    getAgentMemorySessions,
} from '../../agent/agentService'
import type {AgentMemorySessionResponse} from '../../agent/agentTypes'
import {MemorySessionControls} from '../../agent/memorySessionControls'
import {appendChatChunk} from '../chatMessageStream'
import {streamChatResponse} from '../chatService'
import type {ChatMessage} from '../chatTypes'

const markdownPlugins = [remarkGfm]

const initialMessages: ChatMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: 'Hello, I am CyberMario. This session is connecting Cyber Mario Agent.',
    },
]

export function ChatPage() {
    const {message: appMessage} = App.useApp()
    const [messages, setMessages] = useState<ChatMessage[]>(initialMessages)
    const [input, setInput] = useState('')
    const [sessionId, setSessionId] = useState('')
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [sessionLoading, setSessionLoading] = useState(false)
    const [memoryEnabled, setMemoryEnabled] = useState(true)
    const [isSending, setIsSending] = useState(false)
    const [error, setError] = useState('')
    const abortControllerRef = useRef<AbortController | null>(null)

    const canSend = input.trim().length > 0 && !isSending
    const threadLabel = useMemo(() => sessionId || 'New Session', [sessionId])

    useEffect(() => {
        void loadSessions()
    }, [])

    async function loadSessions() {
        setSessionLoading(true)
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'AGENT_CHAT'})
            setSessions(page.records)
        } catch (requestError) {
            appMessage.error(resolveErrorMessage(requestError))
        } finally {
            setSessionLoading(false)
        }
    }

    async function submitMessage(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        const message = input.trim()
        if (!message || isSending) {
            return
        }

        const userMessage: ChatMessage = {
            id: crypto.randomUUID(),
            role: 'user',
            content: message,
        }
        const assistantMessageId = crypto.randomUUID()

        setMessages((currentMessages) => [
            ...currentMessages,
            userMessage,
            {id: assistantMessageId, role: 'assistant', content: ''},
        ])
        setInput('')
        setError('')
        setIsSending(true)

        const abortController = new AbortController()
        abortControllerRef.current = abortController

        try {
            await streamChatResponse(
                {
                    message,
                    sessionId,
                    memoryEnabled,
                    signal: abortController.signal,
                },
                (chunk) => {
                    if (chunk.threadId) {
                        setSessionId(chunk.threadId)
                    }
                    setMessages((currentMessages) =>
                        currentMessages.map((chatMessage) =>
                            chatMessage.id === assistantMessageId
                                ? appendChatChunk(chatMessage, chunk)
                                : chatMessage,
                        ),
                    )
                },
            )
        } catch (requestError) {
            if (abortController.signal.aborted) {
                setMessages((currentMessages) =>
                    currentMessages.map((chatMessage) =>
                        chatMessage.id === assistantMessageId && !chatMessage.content
                            ? {
                                ...chatMessage,
                                content: 'Stopped.',
                            }
                            : chatMessage,
                    ),
                )
            } else {
                setError(resolveErrorMessage(requestError))
                setMessages((currentMessages) =>
                    currentMessages.map((chatMessage) =>
                        chatMessage.id === assistantMessageId && !chatMessage.content
                            ? {
                                ...chatMessage,
                                content: '这次请求没有成功，请检查后端服务和 API Key 后重试。',
                            }
                            : chatMessage,
                    ),
                )
            }
        } finally {
            abortControllerRef.current = null
            setIsSending(false)
        }
    }

    function handleStop() {
        abortControllerRef.current?.abort()
    }

    async function handleNewConversation() {
        abortControllerRef.current?.abort()
        try {
            const session = await createAgentMemorySession({entryType: 'AGENT_CHAT', memoryEnabled})
            setSessionId(session.sessionId)
            setSessions((current) => [session, ...current.filter((item) => item.sessionId !== session.sessionId)])
        } catch (requestError) {
            appMessage.error(resolveErrorMessage(requestError))
            setSessionId('')
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
        setIsSending(false)
    }

    async function archiveCurrentSession() {
        if (!sessionId) {
            return
        }
        try {
            await archiveAgentMemorySession(sessionId)
            appMessage.success('会话已归档')
            setSessionId('')
            setMessages(initialMessages)
            await loadSessions()
        } catch (requestError) {
            appMessage.error(resolveErrorMessage(requestError))
        }
    }

    return (
        <div className="chat-workspace">
            <VisualBackdrop particleCount={12} variant="chat"/>
            <PageToolbar
                actions={(
                    <MemorySessionControls
                        entryType="AGENT_CHAT"
                        loading={sessionLoading}
                        memoryEnabled={memoryEnabled}
                        sessionId={sessionId || undefined}
                        sessions={sessions}
                        onArchive={() => void archiveCurrentSession()}
                        onCreate={() => void handleNewConversation()}
                        onMemoryChange={setMemoryEnabled}
                        onReload={() => void loadSessions()}
                        onSelect={setSessionId}
                        onSelectSession={(session) => setMemoryEnabled(session.memoryEnabled)}
                    />
                )}
                description="面向当前 Java agent 的对话工作台。"
                title="Agent Chat"
            />

            <Card
                className="chat-card"
                extra={(
                    <Space>
                        <Tag color={isSending ? 'processing' : 'success'}>{isSending ? '响应中' : '就绪'}</Tag>
                        <Tag>{threadLabel}</Tag>
                    </Space>
                )}
                title="Reactive conversation"
            >
                <div className="antd-message-list" aria-live="polite">
                    {messages.map((message, index) => {
                        const isLastMessage = index === messages.length - 1
                        const isStreaming = isLastMessage && message.role === 'assistant' && isSending
                        const hasContent = message.content && message.content.length > 0
                        return (
                            <article className={`antd-message-row ${message.role}`} key={message.id}>
                                <Avatar className="antd-message-avatar">
                                    {message.role === 'assistant' ? 'C' : '你'}
                                </Avatar>
                                <div className="antd-message-bubble">
                                    {message.thinkContent && (
                                        <details className="think-block" open={isStreaming && !hasContent}>
                                            <summary className="think-summary">
                                                {isStreaming && !hasContent ? 'Thinking...' : '思考过程'}
                                            </summary>
                                            <div className="think-content">
                                                <ReactMarkdown remarkPlugins={markdownPlugins}>
                                                    {message.thinkContent}
                                                </ReactMarkdown>
                                            </div>
                                        </details>
                                    )}
                                    {hasContent ? (
                                        <div className="message-content">
                                            <ReactMarkdown remarkPlugins={markdownPlugins}>
                                                {message.content}
                                            </ReactMarkdown>
                                        </div>
                                    ) : isStreaming ? (
                                        <Typography.Text type="secondary">正在生成...</Typography.Text>
                                    ) : null}
                                </div>
                            </article>
                        )
                    })}
                </div>

                {error && <Typography.Text type="danger">{error}</Typography.Text>}

                <form className="antd-composer" onSubmit={(event) => void submitMessage(event)}>
                    <Input.TextArea
                        placeholder="问 CyberMario 一个问题..."
                        rows={3}
                        value={input}
                        onChange={(event) => setInput(event.target.value)}
                        onKeyDown={(event) => {
                            if (
                                event.key === 'Enter' &&
                                !event.shiftKey &&
                                !event.nativeEvent.isComposing
                            ) {
                                event.preventDefault()
                                event.currentTarget.form?.requestSubmit()
                            }
                        }}
                    />
                    <div className="composer-actions">
                        {isSending ? (
                            <Button icon={<StopOutlined/>} onClick={handleStop} type="primary">
                                停止
                            </Button>
                        ) : (
                            <Button disabled={!canSend} htmlType="submit" icon={<SendOutlined/>} type="primary">
                                发送
                            </Button>
                        )}
                    </div>
                </form>
            </Card>
        </div>
    )
}
