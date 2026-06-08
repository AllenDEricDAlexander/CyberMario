import type {FormEventHandler} from 'react'
import {useMemo, useRef, useState} from 'react'
import {Button} from '../../components/Button'
import {resolveErrorMessage} from '../../services/request'
import {streamChatResponse} from '../../services/userService'
import type {ChatMessage} from '../../types/chat'

const initialMessages: ChatMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: '你好，我是 CyberMario。当前会话已连接 Spring AI Alibaba agent。',
    },
]

export function Home() {
    const [messages, setMessages] = useState<ChatMessage[]>(initialMessages)
    const [input, setInput] = useState('')
    const [threadId, setThreadId] = useState('')
    const [isSending, setIsSending] = useState(false)
    const [error, setError] = useState('')
    const abortControllerRef = useRef<AbortController | null>(null)

    const canSend = input.trim().length > 0 && !isSending
    const threadLabel = useMemo(() => threadId || '新会话', [threadId])

    const handleSubmit: FormEventHandler<HTMLFormElement> = async (event) => {
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
                    threadId,
                    signal: abortController.signal,
                },
                (chunk) => {
                    if (chunk.threadId) {
                        setThreadId(chunk.threadId)
                    }
                    setMessages((currentMessages) =>
                        currentMessages.map((chatMessage) =>
                            chatMessage.id === assistantMessageId
                                ? {
                                    ...chatMessage,
                                    content: `${chatMessage.content}${chunk.message ?? ''}`,
                                }
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
                                content: '已停止。',
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

    function handleNewConversation() {
        abortControllerRef.current?.abort()
        setMessages(initialMessages)
        setInput('')
        setThreadId('')
        setError('')
        setIsSending(false)
    }

    return (
        <>
            <aside className="conversation-panel" aria-label="Conversation status">
                <div>
                    <p className="product-name">CyberMario</p>
                    <h1>Agent Chat</h1>
                    <p className="product-copy">
                        面向当前 Java agent 的对话工作台。
                    </p>
                </div>

                <dl className="status-list">
                    <div>
                        <dt>Backend</dt>
                        <dd>/demo/chat/stream</dd>
                    </div>
                    <div>
                        <dt>Mode</dt>
                        <dd>text/event-stream</dd>
                    </div>
                    <div>
                        <dt>Thread</dt>
                        <dd title={threadLabel}>{threadLabel}</dd>
                    </div>
                </dl>

                <Button
                    icon={<RestartIcon/>}
                    onClick={handleNewConversation}
                    variant="secondary"
                >
                    新会话
                </Button>
            </aside>

            <section className="chat-panel" aria-label="Agent conversation">
                <div className="chat-header">
                    <div>
                        <p>Spring AI Alibaba</p>
                        <h2>Reactive conversation</h2>
                    </div>
                    <span className={isSending ? 'live-status live' : 'live-status'}>
                        {isSending ? '响应中' : '就绪'}
                    </span>
                </div>

                <div className="message-list" aria-live="polite">
                    {messages.map((message) => (
                        <article
                            className={`message-row ${message.role}`}
                            key={message.id}
                        >
                            <div className="message-avatar" aria-hidden="true">
                                {message.role === 'assistant' ? 'C' : '你'}
                            </div>
                            <div className="message-bubble">
                                <p>{message.content || '正在生成...'}</p>
                            </div>
                        </article>
                    ))}
                </div>

                {error && <p className="error-message">{error}</p>}

                <form className="composer" onSubmit={handleSubmit}>
                    <label className="sr-only" htmlFor="chat-message">
                        输入消息
                    </label>
                    <textarea
                        id="chat-message"
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
                            <Button icon={<StopIcon/>} onClick={handleStop}>
                                停止
                            </Button>
                        ) : (
                            <Button disabled={!canSend} icon={<SendIcon/>} type="submit" variant="primary">
                                发送
                            </Button>
                        )}
                    </div>
                </form>
            </section>
        </>
    )
}

function SendIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 12 20 4l-5 16-3-7-8-1Z"/>
            <path d="m12 13 8-9"/>
        </svg>
    )
}

function StopIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M8 8h8v8H8z"/>
        </svg>
    )
}

function RestartIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 12a8 8 0 1 1-2.34-5.66"/>
            <path d="M20 4v6h-6"/>
        </svg>
    )
}
