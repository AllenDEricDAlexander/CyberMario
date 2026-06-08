import {useMemo, useRef, useState} from 'react'
import type {FormEventHandler} from 'react'
import './App.css'

type ChatRole = 'assistant' | 'user'

type ChatMessage = {
    id: string
    role: ChatRole
    content: string
}

type ChatResponse = {
    threadId: string
    message: string
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const CHAT_ENDPOINT = `${API_BASE_URL}/demo/chat/stream`

const initialMessages: ChatMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: '你好，我是 CyberMario。当前会话已连接 Spring AI Alibaba agent。',
    },
]

function App() {
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
        <main className="app-shell">
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

                <button
                    className="secondary-action"
                    type="button"
                    onClick={handleNewConversation}
                >
                    <RestartIcon/>
                    新会话
                </button>
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
                            <button className="icon-action" type="button" onClick={handleStop}>
                                <StopIcon/>
                                停止
                            </button>
                        ) : (
                            <button className="icon-action primary" type="submit" disabled={!canSend}>
                                <SendIcon/>
                                发送
                            </button>
                        )}
                    </div>
                </form>
            </section>
        </main>
    )
}

async function streamChatResponse(
    request: {
        message: string
        threadId: string
        signal: AbortSignal
    },
    onChunk: (chunk: ChatResponse) => void,
) {
    const response = await fetch(CHAT_ENDPOINT, {
        method: 'POST',
        headers: {
            Accept: 'text/event-stream',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            message: request.message,
            threadId: request.threadId,
        }),
        signal: request.signal,
    })

    if (!response.ok) {
        throw new Error(`请求失败：HTTP ${response.status}`)
    }
    if (!response.body) {
        throw new Error('后端没有返回可读取的响应流')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
        const {done, value} = await reader.read()
        if (done) {
            break
        }

        buffer += decoder.decode(value, {stream: true})
        const events = buffer.replaceAll('\r\n', '\n').split('\n\n')
        buffer = events.pop() ?? ''

        for (const event of events) {
            const chunk = parseServerSentEvent(event)
            if (chunk) {
                onChunk(chunk)
            }
        }
    }

    buffer += decoder.decode()
    const remainingChunk = parseServerSentEvent(buffer)
    if (remainingChunk) {
        onChunk(remainingChunk)
    }
}

function parseServerSentEvent(event: string): ChatResponse | null {
    const data = event
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n')

    if (!data || data === '[DONE]') {
        return null
    }

    return JSON.parse(data) as ChatResponse
}

function resolveErrorMessage(error: unknown) {
    if (error instanceof Error) {
        return error.message
    }
    return '请求失败，请稍后重试'
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

export default App
