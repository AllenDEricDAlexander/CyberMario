import {DeleteOutlined, EditOutlined, ReloadOutlined, SaveOutlined, SendOutlined, StopOutlined} from '@ant-design/icons'
import {
    App,
    Avatar,
    Button,
    Card,
    Checkbox,
    Col,
    Form,
    Input,
    InputNumber,
    Popconfirm,
    Row,
    Select,
    Space,
    Switch,
    Tag,
    Typography,
} from 'antd'
import {type FormEvent, useEffect, useMemo, useRef, useState} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {useAuth} from '../auth/authStore'
import {appendChatChunk} from '../chat/chatMessageStream'
import type {ChatMessage} from '../chat/chatTypes'
import {canEditAgentPreset} from './agentPresetPermissions'
import {
    createAgentPreset,
    deleteAgentPreset,
    getAgentPresets,
    streamAgentDebugChat,
    updateAgentPreset,
    updateAgentPresetStatus,
} from './agentService'
import {defaultAgentToolNames} from './agentPresetDefaults'
import type {AgentPresetConfig, AgentPresetRequest, AgentPresetResponse} from './agentTypes'

type DebugFormValues = {
    presetId?: number
    name: string
    description?: string
    enabled: boolean
    systemPrompt?: string
    temperature?: number
    maxTokens?: number
    topP?: number
    topK?: number
    enableThinking?: boolean
    thinkingBudget?: number
    enableSearch?: boolean
    multiModel?: boolean
    enabledToolNames?: string[]
    parallelToolExecution?: boolean
    maxParallelTools?: number
    toolExecutionTimeoutSeconds?: number
}

const markdownPlugins = [remarkGfm]
const initialMessages: ChatMessage[] = [
    {id: 'welcome', role: 'assistant', content: '这里是 Agent 调试工作台。选择预设或调整参数后发起一轮测试对话。'},
]

function AgentDebugPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [form] = Form.useForm<DebugFormValues>()
    const [presets, setPresets] = useState<AgentPresetResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [messages, setMessages] = useState<ChatMessage[]>(initialMessages)
    const [input, setInput] = useState('')
    const [threadId, setThreadId] = useState('')
    const [isSending, setIsSending] = useState(false)
    const [error, setError] = useState('')
    const abortControllerRef = useRef<AbortController | null>(null)

    const selectedPresetId = Form.useWatch('presetId', form)
    const selectedPreset = useMemo(() => presets.find((item) => item.id === selectedPresetId), [presets, selectedPresetId])
    const canEditSelectedPreset = canEditAgentPreset(selectedPreset, auth.user?.id)
    const canSend = input.trim().length > 0 && !isSending

    useEffect(() => {
        void loadPresets()
    }, [])

    async function loadPresets() {
        setLoading(true)
        try {
            const page = await getAgentPresets({page: 1, size: 200})
            setPresets(page.records)
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setLoading(false)
        }
    }

    function applyPreset(id?: number) {
        const preset = presets.find((item) => item.id === id)
        if (!preset) {
            form.setFieldsValue(defaultFormValues())
            return
        }
        form.setFieldsValue(toFormValues(preset))
    }

    async function savePreset() {
        const values = await form.validateFields()
        if (!canEditSelectedPreset) {
            message.warning('只能编辑自己创建的预设')
            return
        }
        setSaving(true)
        try {
            const request = toPresetRequest(values)
            const saved = values.presetId
                ? await updateAgentPreset(values.presetId, request)
                : await createAgentPreset(request)
            message.success('预设已保存')
            await loadPresets()
            form.setFieldsValue(toFormValues(saved))
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setSaving(false)
        }
    }

    async function togglePreset(enabled: boolean) {
        if (!selectedPresetId) {
            form.setFieldValue('enabled', enabled)
            return
        }
        if (!canEditSelectedPreset) {
            message.warning('只能编辑自己创建的预设')
            form.setFieldValue('enabled', selectedPreset?.enabled ?? true)
            return
        }
        setSaving(true)
        try {
            const saved = await updateAgentPresetStatus(selectedPresetId, enabled)
            message.success(enabled ? '预设已启用' : '预设已停用')
            await loadPresets()
            form.setFieldsValue(toFormValues(saved))
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setSaving(false)
        }
    }

    async function removePreset() {
        if (!selectedPresetId) {
            return
        }
        if (!canEditSelectedPreset) {
            message.warning('只能删除自己创建的预设')
            return
        }
        setSaving(true)
        try {
            await deleteAgentPreset(selectedPresetId)
            message.success('预设已删除')
            form.setFieldsValue(defaultFormValues())
            await loadPresets()
        } catch (requestError) {
            message.error(resolveErrorMessage(requestError))
        } finally {
            setSaving(false)
        }
    }

    async function submitMessage(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        const text = input.trim()
        if (!text || isSending) {
            return
        }
        const values = form.getFieldsValue()
        const assistantMessageId = crypto.randomUUID()
        setMessages((current) => [
            ...current,
            {id: crypto.randomUUID(), role: 'user', content: text},
            {id: assistantMessageId, role: 'assistant', content: ''},
        ])
        setInput('')
        setError('')
        setIsSending(true)
        const abortController = new AbortController()
        abortControllerRef.current = abortController
        try {
            await streamAgentDebugChat({
                message: text,
                threadId,
                presetId: values.presetId,
                overrides: toConfig(values),
            }, abortController.signal, (chunk) => {
                if (chunk.threadId) {
                    setThreadId(chunk.threadId)
                }
                setMessages((current) => current.map((item) =>
                    item.id === assistantMessageId ? appendChatChunk(item, chunk) : item,
                ))
            })
        } catch (requestError) {
            if (abortController.signal.aborted) {
                setMessages((current) => current.map((item) =>
                    item.id === assistantMessageId && !item.content ? {...item, content: 'Stopped.'} : item,
                ))
            } else {
                setError(resolveErrorMessage(requestError))
                setMessages((current) => current.map((item) =>
                    item.id === assistantMessageId && !item.content ? {
                        ...item,
                        content: '调试请求失败，请检查参数后重试。'
                    } : item,
                ))
            }
        } finally {
            abortControllerRef.current = null
            setIsSending(false)
        }
    }

    function newConversation() {
        abortControllerRef.current?.abort()
        setMessages(initialMessages)
        setInput('')
        setThreadId('')
        setError('')
        setIsSending(false)
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading}
                                 onClick={() => void loadPresets()}>刷新预设</Button>}
                description="通过预设和临时覆盖参数验证 Agent 行为，模型选择保留为后续扩展。"
                title="Agent 调试"
            />
            <Row gutter={[16, 16]}>
                <Col lg={10} xs={24}>
                    <Card className="dashboard-filter-card" title="预设与参数">
                        <Form form={form} initialValues={defaultFormValues()} layout="vertical">
                            <Form.Item label="预设" name="presetId">
                                <Select
                                    allowClear
                                    loading={loading}
                                    onChange={(id) => applyPreset(id)}
                                    options={presets.map((item) => ({
                                        label: `${item.name}${item.enabled ? '' : '（停用）'}`,
                                        value: item.id,
                                    }))}
                                    placeholder="选择已有预设"
                                />
                            </Form.Item>
                            <Row gutter={12}>
                                <Col md={16} xs={24}>
                                    <Form.Item label="名称" name="name"
                                               rules={[{required: true, message: '请输入名称'}]}>
                                        <Input maxLength={128}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={24}>
                                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                                        <Switch disabled={saving || !canEditSelectedPreset}
                                                onChange={(checked) => void togglePreset(checked)}/>
                                    </Form.Item>
                                </Col>
                            </Row>
                            <Form.Item label="描述" name="description">
                                <Input maxLength={512}/>
                            </Form.Item>
                            <Form.Item label="系统提示词" name="systemPrompt">
                                <Input.TextArea rows={5}/>
                            </Form.Item>
                            <Row gutter={12}>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Temperature" name="temperature">
                                        <InputNumber max={2} min={0} step={0.1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Top P" name="topP">
                                        <InputNumber max={1} min={0} step={0.05} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Max Tokens" name="maxTokens">
                                        <InputNumber min={1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Top K" name="topK">
                                        <InputNumber min={1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Thinking Budget" name="thinkingBudget">
                                        <InputNumber min={1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={8} xs={12}>
                                    <Form.Item label="Tool Timeout" name="toolExecutionTimeoutSeconds">
                                        <InputNumber min={1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                            </Row>
                            <Space wrap>
                                <Form.Item name="enableThinking" valuePropName="checked">
                                    <Checkbox>思考</Checkbox>
                                </Form.Item>
                                <Form.Item name="enableSearch" valuePropName="checked">
                                    <Checkbox>搜索</Checkbox>
                                </Form.Item>
                                <Form.Item name="multiModel" valuePropName="checked">
                                    <Checkbox>Multi Model</Checkbox>
                                </Form.Item>
                                <Form.Item name="parallelToolExecution" valuePropName="checked">
                                    <Checkbox>并行工具</Checkbox>
                                </Form.Item>
                            </Space>
                            <Row gutter={12}>
                                <Col md={12} xs={24}>
                                    <Form.Item label="Max Parallel Tools" name="maxParallelTools">
                                        <InputNumber min={1} style={{width: '100%'}}/>
                                    </Form.Item>
                                </Col>
                                <Col md={12} xs={24}>
                                    <Form.Item label="启用工具" name="enabledToolNames">
                                        <Select mode="tags" options={defaultAgentToolNames.map((tool) => ({
                                            label: tool,
                                            value: tool
                                        }))}/>
                                    </Form.Item>
                                </Col>
                            </Row>
                            <Space wrap>
                                <Button icon={selectedPreset ? <EditOutlined/> : <SaveOutlined/>} loading={saving}
                                        disabled={!canEditSelectedPreset} onClick={voidify(savePreset)} type="primary">
                                    {selectedPreset ? '更新预设' : '创建预设'}
                                </Button>
                                <Popconfirm disabled={!selectedPresetId} title="删除这个预设？"
                                            onConfirm={voidify(removePreset)}>
                                    <Button danger disabled={!selectedPresetId || !canEditSelectedPreset}
                                            icon={<DeleteOutlined/>} loading={saving}>
                                        删除
                                    </Button>
                                </Popconfirm>
                            </Space>
                        </Form>
                    </Card>
                </Col>
                <Col lg={14} xs={24}>
                    <Card
                        className="chat-card"
                        extra={(
                            <Space>
                                <Tag color={isSending ? 'processing' : 'success'}>{isSending ? '响应中' : '就绪'}</Tag>
                                <Tag>{threadId || 'New Session'}</Tag>
                            </Space>
                        )}
                        title="调试对话"
                    >
                        <div className="antd-message-list" aria-live="polite">
                            {messages.map((item, index) => {
                                const isLast = index === messages.length - 1
                                const isStreaming = isLast && item.role === 'assistant' && isSending
                                const hasContent = item.content.length > 0
                                return (
                                    <article className={`antd-message-row ${item.role}`} key={item.id}>
                                        <Avatar
                                            className="antd-message-avatar">{item.role === 'assistant' ? 'A' : '你'}</Avatar>
                                        <div className="antd-message-bubble">
                                            {item.thinkContent && (
                                                <details className="think-block" open={isStreaming && !hasContent}>
                                                    <summary className="think-summary">
                                                        {isStreaming && !hasContent ? 'Thinking...' : '思考过程'}
                                                    </summary>
                                                    <div className="think-content">
                                                        <ReactMarkdown
                                                            remarkPlugins={markdownPlugins}>{item.thinkContent}</ReactMarkdown>
                                                    </div>
                                                </details>
                                            )}
                                            {hasContent ? (
                                                <div className="message-content">
                                                    <ReactMarkdown
                                                        remarkPlugins={markdownPlugins}>{item.content}</ReactMarkdown>
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
                                onChange={(event) => setInput(event.target.value)}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                                        event.preventDefault()
                                        event.currentTarget.form?.requestSubmit()
                                    }
                                }}
                                placeholder="输入调试消息..."
                                rows={3}
                                value={input}
                            />
                            <div className="composer-actions">
                                <Space>
                                    <Button icon={<ReloadOutlined/>} onClick={newConversation}>新会话</Button>
                                    {isSending ? (
                                        <Button icon={<StopOutlined/>}
                                                onClick={() => abortControllerRef.current?.abort()}
                                                type="primary">停止</Button>
                                    ) : (
                                        <Button disabled={!canSend} htmlType="submit" icon={<SendOutlined/>}
                                                type="primary">发送</Button>
                                    )}
                                </Space>
                            </div>
                        </form>
                    </Card>
                </Col>
            </Row>
        </>
    )
}

function defaultFormValues(): DebugFormValues {
    return {
        name: '',
        enabled: true,
        temperature: 0.7,
        topP: 0.9,
        enableThinking: true,
        enableSearch: false,
        multiModel: true,
        parallelToolExecution: false,
        maxParallelTools: 5,
        toolExecutionTimeoutSeconds: 300,
        enabledToolNames: defaultAgentToolNames,
    }
}

function toFormValues(preset: AgentPresetResponse): DebugFormValues {
    const config = preset.config ?? {}
    return {
        ...defaultFormValues(),
        presetId: preset.id,
        name: preset.name,
        description: preset.description,
        enabled: preset.enabled,
        systemPrompt: config.systemPrompt,
        temperature: config.modelOptions?.temperature,
        maxTokens: config.modelOptions?.maxTokens,
        topP: config.modelOptions?.topP,
        topK: config.modelOptions?.topK,
        enableThinking: config.modelOptions?.enableThinking,
        thinkingBudget: config.modelOptions?.thinkingBudget,
        enableSearch: config.modelOptions?.enableSearch,
        multiModel: config.modelOptions?.multiModel,
        enabledToolNames: config.toolConfig?.enabledToolNames ?? [],
        parallelToolExecution: config.agentOptions?.parallelToolExecution,
        maxParallelTools: config.agentOptions?.maxParallelTools,
        toolExecutionTimeoutSeconds: config.agentOptions?.toolExecutionTimeoutSeconds,
    }
}

function toPresetRequest(values: DebugFormValues): AgentPresetRequest {
    return {
        name: values.name,
        description: values.description,
        enabled: values.enabled,
        config: toConfig(values),
    }
}

function toConfig(values: DebugFormValues): AgentPresetConfig {
    return {
        modelOptions: {
            temperature: values.temperature,
            maxTokens: values.maxTokens,
            topP: values.topP,
            topK: values.topK,
            enableThinking: values.enableThinking,
            thinkingBudget: values.thinkingBudget,
            enableSearch: values.enableSearch,
            multiModel: values.multiModel,
        },
        systemPrompt: values.systemPrompt,
        toolConfig: {enabledToolNames: values.enabledToolNames ?? []},
        agentOptions: {
            parallelToolExecution: values.parallelToolExecution,
            maxParallelTools: values.maxParallelTools,
            toolExecutionTimeoutSeconds: values.toolExecutionTimeoutSeconds,
        },
    }
}

export const Component = AgentDebugPage
