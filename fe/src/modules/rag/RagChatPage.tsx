import {ReloadOutlined, SendOutlined, StopOutlined} from '@ant-design/icons'
import {App, Avatar, Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useRef, useState} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {getRagKnowledgeBases, streamRagChat} from './ragService'
import type {KnowledgeBaseResponse, RagStreamEvent, SourceReferenceResponse} from './ragTypes'

type ChatMessage = {
    id: string
    role: 'user' | 'assistant'
    content: string
    sources?: SourceReferenceResponse[]
    traceId?: string
}

type RagChatFormValues = {
    knowledgeBaseIds: number[]
    topK: number
    similarityThreshold: number
}

const markdownPlugins = [remarkGfm]

function RagChatPage() {
    const {message} = App.useApp()
    const [form] = Form.useForm<RagChatFormValues>()
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [messages, setMessages] = useState<ChatMessage[]>([
        {id: 'welcome', role: 'assistant', content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。'},
    ])
    const [input, setInput] = useState('')
    const [loading, setLoading] = useState(false)
    const [sources, setSources] = useState<SourceReferenceResponse[]>([])
    const [sourceOpen, setSourceOpen] = useState(false)
    const abortRef = useRef<AbortController | null>(null)

    useEffect(() => {
        void getRagKnowledgeBases({page: 1, size: 200}).then((page) => setKnowledgeBases(page.records))
    }, [])

    async function send() {
        const values = await form.validateFields()
        const question = input.trim()
        if (!question) {
            message.warning('请输入问题')
            return
        }

        const userMessage: ChatMessage = {id: crypto.randomUUID(), role: 'user', content: question}
        const assistantId = crypto.randomUUID()
        const assistantMessage: ChatMessage = {id: assistantId, role: 'assistant', content: '', sources: []}
        setMessages((current) => [...current, userMessage, assistantMessage])
        setInput('')
        setLoading(true)
        const abortController = new AbortController()
        abortRef.current = abortController

        try {
            await streamRagChat(
                {
                    question,
                    knowledgeBaseIds: values.knowledgeBaseIds,
                    retrievalOptions: {
                        topK: values.topK,
                        similarityThreshold: values.similarityThreshold,
                        searchMode: 'VECTOR',
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
            setMessages((current) => current.map((item) => item.id === assistantId ? {
                ...item,
                traceId: event.data.traceId
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

    function reset() {
        abortRef.current?.abort()
        setMessages([{id: 'welcome', role: 'assistant', content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。'}])
        setInput('')
        setLoading(false)
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={reset}>新会话</Button>}
                description="基于已入库知识库回答问题，回答来源由右侧抽屉查看。"
                title="RAG 问答"
            />
            <Card size="small">
                <Form form={form} initialValues={{topK: 6, similarityThreshold: 0.55}} layout="inline">
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
                    <Form.Item name="similarityThreshold">
                        <InputNumber min={0} max={1} step={0.01} addonBefore="阈值"/>
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
