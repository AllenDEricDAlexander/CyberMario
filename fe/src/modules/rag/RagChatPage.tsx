import {DislikeOutlined, LikeOutlined, SendOutlined, StopOutlined, WarningOutlined} from '@ant-design/icons'
import {App, Avatar, Button, Card, Checkbox, Drawer, Form, Input, InputNumber, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useRef, useState} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
    getAgentMemorySessions,
} from '../agent/agentService'
import type {AgentMemorySessionResponse} from '../agent/agentTypes'
import {MemorySessionControls} from '../agent/memorySessionControls'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {createRagFeedback, getRagKnowledgeBases, streamRagChat} from './ragService'
import type {KnowledgeBaseResponse, RagSearchMode, RagStreamEvent, SourceReferenceResponse} from './ragTypes'

type ChatMessage = {
    id: string
    role: 'user' | 'assistant'
    content: string
    sources?: SourceReferenceResponse[]
    traceId?: string
    messageId?: string
    question?: string
}

type RagChatFormValues = {
    knowledgeBaseIds: number[]
    topK: number
    candidateTopK: number
    similarityThreshold: number
    searchMode: RagSearchMode
    rerankEnabled: boolean
}

const markdownPlugins = [remarkGfm]

function RagChatPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [form] = Form.useForm<RagChatFormValues>()
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [sessionId, setSessionId] = useState('')
    const [sessionLoading, setSessionLoading] = useState(false)
    const [memoryEnabled, setMemoryEnabled] = useState(true)
    const [longTermExtractionEnabled, setLongTermExtractionEnabled] = useState(true)
    const [messages, setMessages] = useState<ChatMessage[]>([
        {id: 'welcome', role: 'assistant', content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。'},
    ])
    const [input, setInput] = useState('')
    const [loading, setLoading] = useState(false)
    const [sources, setSources] = useState<SourceReferenceResponse[]>([])
    const [sourceOpen, setSourceOpen] = useState(false)
    const abortRef = useRef<AbortController | null>(null)
    const canCreateFeedback = canUseRbacButton(auth, ragButtonCodes.feedback.create)

    useEffect(() => {
        void getRagKnowledgeBases({page: 1, size: 200}).then((page) => setKnowledgeBases(page.records))
        void loadSessions()
    }, [])

    async function loadSessions() {
        setSessionLoading(true)
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'RAG_CHAT'})
            setSessions(page.records)
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setSessionLoading(false)
        }
    }

    async function send() {
        const values = await form.validateFields()
        const question = input.trim()
        if (!question) {
            message.warning('请输入问题')
            return
        }

        const userMessage: ChatMessage = {id: crypto.randomUUID(), role: 'user', content: question}
        const assistantId = crypto.randomUUID()
        const assistantMessage: ChatMessage = {id: assistantId, role: 'assistant', content: '', sources: [], question}
        setMessages((current) => [...current, userMessage, assistantMessage])
        setInput('')
        setLoading(true)
        const abortController = new AbortController()
        abortRef.current = abortController

        try {
            await streamRagChat(
                {
                    sessionId,
                    memoryEnabled,
                    longTermExtractionEnabled,
                    question,
                    knowledgeBaseIds: values.knowledgeBaseIds,
                    retrievalOptions: {
                        topK: values.topK,
                        candidateTopK: values.candidateTopK,
                        similarityThreshold: values.similarityThreshold,
                        searchMode: values.searchMode,
                        rerankEnabled: values.rerankEnabled,
                    },
                    withSources: true,
                },
                abortController.signal,
                (event) => handleEvent(assistantId, event),
            )
        } catch (error) {
            if (!abortController.signal.aborted) {
                const errorMessage = resolveErrorMessage(error)
                setMessages((current) => current.map((item) => item.id === assistantId ? {
                    ...item,
                    content: errorMessage
                } : item))
            }
        } finally {
            setLoading(false)
            abortRef.current = null
        }
    }

    function handleEvent(assistantId: string, event: RagStreamEvent) {
        if (event.type === 'metadata') {
            if (event.data.sessionId) {
                setSessionId(event.data.sessionId)
            }
            setMessages((current) => current.map((item) => item.id === assistantId ? {
                ...item,
                traceId: event.data.traceId
                , messageId: event.data.messageId
            } : item))
        }
        if (event.type === 'retrieval') {
            setMessages((current) => current.map((item) => item.id === assistantId ? {
                ...item,
                sources: event.data.sources
            } : item))
        }
        if (event.type === 'delta') {
            setMessages((current) => current.map((item) => item.id === assistantId ? {
                ...item,
                content: `${item.content}${event.data.content}`
            } : item))
        }
        if (event.type === 'error') {
            setMessages((current) => current.map((item) => item.id === assistantId ? {
                ...item,
                content: event.data.message,
                traceId: event.data.traceId
            } : item))
        }
    }

    function stop() {
        abortRef.current?.abort()
        setLoading(false)
    }

    async function reset() {
        abortRef.current?.abort()
        try {
            const session = await createAgentMemorySession({
                entryType: 'RAG_CHAT',
                memoryEnabled,
                longTermExtractionEnabled,
            })
            setSessionId(session.sessionId)
            setSessions((current) => [session, ...current.filter((item) => item.sessionId !== session.sessionId)])
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
            setSessionId('')
        }
        setMessages([{id: 'welcome', role: 'assistant', content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。'}])
        setInput('')
        setLoading(false)
    }

    async function archiveCurrentSession() {
        if (!sessionId) {
            return
        }
        try {
            await archiveAgentMemorySession(sessionId)
            message.success('会话已归档')
            setSessionId('')
            setMessages([{id: 'welcome', role: 'assistant', content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。'}])
            await loadSessions()
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        }
    }

    async function submitFeedback(item: ChatMessage, feedbackType: 'HELPFUL' | 'NOT_HELPFUL' | 'BAD_SOURCE' | 'NO_ANSWER') {
        await createRagFeedback({
            traceId: item.traceId,
            messageId: item.messageId,
            feedbackType,
            question: item.question,
            answer: item.content,
            sourceChunkIds: item.sources?.map((source) => source.chunkId),
        })
        message.success('反馈已提交')
    }

    return (
        <>
            <PageToolbar
                actions={(
                    <MemorySessionControls
                        entryType="RAG_CHAT"
                        loading={sessionLoading}
                        longTermExtractionEnabled={longTermExtractionEnabled}
                        memoryEnabled={memoryEnabled}
                        sessionId={sessionId || undefined}
                        sessions={sessions}
                        showExtractionSwitch
                        onArchive={() => void archiveCurrentSession()}
                        onCreate={() => void reset()}
                        onExtractionChange={setLongTermExtractionEnabled}
                        onMemoryChange={setMemoryEnabled}
                        onReload={() => void loadSessions()}
                        onSelect={setSessionId}
                        onSelectSession={(session) => {
                            setMemoryEnabled(session.memoryEnabled)
                            setLongTermExtractionEnabled(session.longTermExtractionEnabled)
                        }}
                    />
                )}
                description="基于已入库知识库回答问题，回答来源由右侧抽屉查看。"
                title="RAG 问答"
            />
            <Card size="small">
                <Form form={form} initialValues={{
                    topK: 6,
                    candidateTopK: 50,
                    similarityThreshold: 0.55,
                    searchMode: 'HYBRID',
                    rerankEnabled: false,
                }} layout="inline">
                    <Form.Item name="knowledgeBaseIds" rules={[{required: true, message: '请选择知识库'}]}>
                        <Select
                            mode="multiple"
                            options={knowledgeBases.map((item) => ({label: item.name, value: item.id}))}
                            placeholder="选择知识库"
                            style={{minWidth: 300}}
                        />
                    </Form.Item>
                    <Form.Item name="topK">
                        <InputNumber min={1} max={20} addonBefore="TopK"/>
                    </Form.Item>
                    <Form.Item name="candidateTopK">
                        <InputNumber min={1} max={100} addonBefore="候选"/>
                    </Form.Item>
                    <Form.Item name="similarityThreshold">
                        <InputNumber min={0} max={1} step={0.01} addonBefore="阈值"/>
                    </Form.Item>
                    <Form.Item name="searchMode">
                        <Select style={{width: 140}} options={[
                            {label: '向量', value: 'VECTOR'},
                            {label: '关键词', value: 'KEYWORD'},
                            {label: '混合', value: 'HYBRID'},
                            {label: '混合重排', value: 'HYBRID_RERANK'},
                        ]}/>
                    </Form.Item>
                    <Form.Item name="rerankEnabled" valuePropName="checked">
                        <Checkbox>Rerank</Checkbox>
                    </Form.Item>
                </Form>
            </Card>
            <Card className="chat-card" style={{marginTop: 16}}>
                <div className="antd-message-list" aria-live="polite">
                    {messages.map((item) => (
                        <article className={`antd-message-row ${item.role}`} key={item.id}>
                            <Avatar className="antd-message-avatar">{item.role === 'assistant' ? 'R' : '你'}</Avatar>
                            <div className="antd-message-bubble">
                                {item.content ? (
                                    <div className="message-content">
                                        <ReactMarkdown remarkPlugins={markdownPlugins}>{item.content}</ReactMarkdown>
                                    </div>
                                ) : (
                                    <Typography.Text type="secondary">正在生成...</Typography.Text>
                                )}
                                {item.sources && item.sources.length > 0 && (
                                    <Button
                                        size="small"
                                        type="link"
                                        onClick={() => {
                                            setSources(item.sources ?? [])
                                            setSourceOpen(true)
                                        }}
                                    >
                                        查看引用来源（{item.sources.length}）
                                    </Button>
                                )}
                                {canCreateFeedback && item.role === 'assistant' && item.id !== 'welcome' && item.content && (
                                    <Space wrap>
                                        <Button icon={<LikeOutlined/>} size="small" type="text"
                                                onClick={() => void submitFeedback(item, 'HELPFUL')}>有帮助</Button>
                                        <Button icon={<DislikeOutlined/>} size="small" type="text"
                                                onClick={() => void submitFeedback(item, 'NOT_HELPFUL')}>没帮助</Button>
                                        <Button icon={<WarningOutlined/>} size="small" type="text"
                                                onClick={() => void submitFeedback(item, 'BAD_SOURCE')}>引用不准</Button>
                                        <Button size="small" type="text"
                                                onClick={() => void submitFeedback(item, 'NO_ANSWER')}>没找到答案</Button>
                                    </Space>
                                )}
                                {item.traceId && <Tag>traceId={item.traceId}</Tag>}
                            </div>
                        </article>
                    ))}
                </div>
                <Space.Compact block style={{marginTop: 16}}>
                    <Input.TextArea
                        autoSize={{minRows: 2, maxRows: 6}}
                        disabled={loading}
                        onChange={(event) => setInput(event.target.value)}
                        onPressEnter={(event) => {
                            if (!event.shiftKey && !event.nativeEvent.isComposing) {
                                event.preventDefault()
                                void send()
                            }
                        }}
                        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
                        value={input}
                    />
                    {loading ? (
                        <Button icon={<StopOutlined/>} onClick={stop} type="primary">停止</Button>
                    ) : (
                        <Button icon={<SendOutlined/>} onClick={() => void send()} type="primary">发送</Button>
                    )}
                </Space.Compact>
            </Card>
            <Drawer onClose={() => setSourceOpen(false)} open={sourceOpen} title="引用来源" width={560}>
                <Space direction="vertical" style={{width: '100%'}}>
                    {sources.map((source, index) => (
                        <Card key={source.sourceId} size="small" title={`来源 ${index + 1}`}>
                            <Space wrap>
                                <Tag color="blue">score={source.score.toFixed(4)}</Tag>
                                {source.rerankScore !== undefined && <Tag color="purple">rerank={source.rerankScore.toFixed(4)}</Tag>}
                                {source.matchedBy && <Tag>{source.matchedBy}</Tag>}
                                <Tag>chunk={source.chunkIndex}</Tag>
                                <Typography.Text
                                    strong>{source.documentName || `文档 ${source.documentId}`}</Typography.Text>
                            </Space>
                            <Typography.Paragraph copyable style={{marginTop: 8}}>
                                {source.content}
                            </Typography.Paragraph>
                        </Card>
                    ))}
                </Space>
            </Drawer>
        </>
    )
}

export const Component = RagChatPage
