import {DeleteOutlined, EditOutlined, ReloadOutlined, SaveOutlined} from '@ant-design/icons'
import {
    App,
    Button,
    Checkbox,
    Form,
    Input,
    InputNumber,
    Popconfirm,
    Select,
    Space,
    Switch,
    Tag,
    Typography,
} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {
    applyAgentChunkToMessage,
    ChatSettingsModal,
    ChatWorkspace,
    type ChatWorkspaceMessage,
    type ChatWorkspaceRequest,
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
    markMessageSucceeded,
    useXChatWorkspace,
} from '../../components/chat-workspace'
import {resolveErrorMessage} from '../../services/request'
import {useAuth} from '../auth/authStore'
import {canEditAgentPreset} from './agentPresetPermissions'
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
    createAgentPreset,
    deleteAgentPreset,
    getAgentMemoryMessages,
    getAgentMemorySessions,
    getAgentPresets,
    streamAgentDebugChat,
    updateAgentPreset,
    updateAgentPresetStatus,
} from './agentService'
import {defaultAgentToolNames} from './agentPresetDefaults'
import type {AgentMemorySessionResponse, AgentPresetConfig, AgentPresetRequest, AgentPresetResponse} from './agentTypes'

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

type UpdateAssistantMessage = (
    assistantId: string,
    updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage
) => boolean

const initialMessages: ChatWorkspaceMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: '这里是 Agent 调试工作台。选择预设或调整参数后发起一轮测试对话。',
        status: 'success',
    },
]

function AgentDebugPage() {
    const {message: appMessage} = App.useApp()
    const auth = useAuth()
    const [form] = Form.useForm<DebugFormValues>()
    const [presets, setPresets] = useState<AgentPresetResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [input, setInput] = useState('')
    const [settingsOpen, setSettingsOpen] = useState(false)
    const [sessionId, setSessionId] = useState('')
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [sessionLoading, setSessionLoading] = useState(false)
    const [memoryEnabled, setMemoryEnabled] = useState(true)
    const [longTermExtractionEnabled, setLongTermExtractionEnabled] = useState(true)
    const [error, setError] = useState('')
    const abortControllerRef = useRef<AbortController | null>(null)
    const updateAssistantMessageRef = useRef<UpdateAssistantMessage | null>(null)
    const historyRequestSeqRef = useRef(0)

    const selectedPresetId = Form.useWatch('presetId', form)
    const selectedPreset = useMemo(() => presets.find((item) => item.id === selectedPresetId), [presets, selectedPresetId])
    const canEditSelectedPreset = canEditAgentPreset(selectedPreset, auth.user?.id)
    const conversations = useMemo(
        () => sessions.map(mapSessionToConversation),
        [sessions]
    )
    const sessionLabel = useMemo(() => sessionId || 'New Debug Session', [sessionId])

    function nextHistoryRequestToken() {
        historyRequestSeqRef.current += 1
        return historyRequestSeqRef.current
    }

    useEffect(() => () => {
        nextHistoryRequestToken()
    }, [])

    const loadPresets = useCallback(async () => {
        setLoading(true)
        try {
            const page = await getAgentPresets({page: 1, size: 200})
            setPresets(page.records)
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setLoading(false)
        }
    }, [])

    const loadSessions = useCallback(async (requestToken?: number) => {
        const isCurrentRequest = () => requestToken === undefined || historyRequestSeqRef.current === requestToken
        if (!isCurrentRequest()) {
            return
        }
        if (requestToken === undefined) {
            setSessionLoading(true)
        }
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'AGENT_DEBUG'})
            if (isCurrentRequest()) {
                setSessions(page.records)
            }
        } catch (requestError) {
            if (isCurrentRequest()) {
                reportGlobalError(requestError)
            }
        } finally {
            if (requestToken === undefined && isCurrentRequest()) {
                setSessionLoading(false)
            }
        }
    }, [])

    useEffect(() => {
        form.setFieldsValue(defaultFormValues())
        void loadPresets()
        void loadSessions()
    }, [form, loadPresets, loadSessions])

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
            appMessage.warning('只能编辑自己创建的预设')
            return
        }
        setSaving(true)
        try {
            const request = toPresetRequest(values)
            const saved = values.presetId
                ? await updateAgentPreset(values.presetId, request)
                : await createAgentPreset(request)
            appMessage.success('预设已保存')
            await loadPresets()
            form.setFieldsValue(toFormValues(saved))
        } catch (requestError) {
            reportGlobalError(requestError)
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
            appMessage.warning('只能编辑自己创建的预设')
            form.setFieldValue('enabled', selectedPreset?.enabled ?? true)
            return
        }
        setSaving(true)
        try {
            const saved = await updateAgentPresetStatus(selectedPresetId, enabled)
            appMessage.success(enabled ? '预设已启用' : '预设已停用')
            await loadPresets()
            form.setFieldsValue(toFormValues(saved))
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setSaving(false)
        }
    }

    async function removePreset() {
        if (!selectedPresetId) {
            return
        }
        if (!canEditSelectedPreset) {
            appMessage.warning('只能删除自己创建的预设')
            return
        }
        setSaving(true)
        try {
            await deleteAgentPreset(selectedPresetId)
            appMessage.success('预设已删除')
            form.setFieldsValue(defaultFormValues())
            await loadPresets()
        } catch (requestError) {
            reportGlobalError(requestError)
        } finally {
            setSaving(false)
        }
    }

    const handleWorkspaceRequest = useCallback(async (
        requestParams: ChatWorkspaceRequest,
        assistantId: string
    ) => {
        const values = form.getFieldsValue(true) as DebugFormValues
        const abortController = new AbortController()
        abortControllerRef.current = abortController

        try {
            await streamAgentDebugChat({
                message: requestParams.message,
                sessionId: requestParams.conversationKey,
                memoryEnabled,
                longTermExtractionEnabled,
                presetId: values.presetId,
                overrides: toConfig(values),
            }, abortController.signal, (chunk) => {
                if (chunk.threadId) {
                    setSessionId(chunk.threadId)
                }
                updateAssistantMessageRef.current?.(
                    assistantId,
                    current => applyAgentChunkToMessage(current, chunk)
                )
            })
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
    }, [form, longTermExtractionEnabled, memoryEnabled])

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
            entryType: 'AGENT_DEBUG',
        })
    }

    async function newConversation() {
        abort()
        const requestToken = nextHistoryRequestToken()
        try {
            const session = await createAgentMemorySession({
                entryType: 'AGENT_DEBUG',
                memoryEnabled,
                longTermExtractionEnabled,
            })
            if (historyRequestSeqRef.current !== requestToken) {
                return
            }
            setSessionId(session.sessionId)
            setSessions((current) => [session, ...current.filter((item) => item.sessionId !== session.sessionId)])
        } catch (requestError) {
            if (historyRequestSeqRef.current !== requestToken) {
                return
            }
            reportGlobalError(requestError)
            setSessionId('')
        }
        if (historyRequestSeqRef.current !== requestToken) {
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

        abort()
        const isActiveConversation = conversationKey === sessionId
        const requestToken = nextHistoryRequestToken()
        try {
            await archiveAgentMemorySession(conversationKey)
            if (historyRequestSeqRef.current !== requestToken) {
                return
            }
            appMessage.success('会话已归档')
            if (isActiveConversation) {
                setSessionId('')
                setMessages(initialMessages)
                setInput('')
                setError('')
            }
            await loadSessions(requestToken)
        } catch (requestError) {
            if (historyRequestSeqRef.current === requestToken) {
                reportGlobalError(requestError)
            }
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
            setLongTermExtractionEnabled(session.longTermExtractionEnabled)
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
            entryType: 'AGENT_DEBUG',
        })
    }

    const subtitle = error ? (
        <Space direction="vertical" size={0}>
            <span>通过预设和临时覆盖参数验证 Agent 行为。</span>
            <Typography.Text type="danger">{error}</Typography.Text>
        </Space>
    ) : '通过预设和临时覆盖参数验证 Agent 行为。'

    return (
        <ChatWorkspace
            activeConversationKey={sessionId || undefined}
            brandDescription="Agent debug memory sessions"
            brandTitle="Agent Debug"
            conversations={conversations}
            headerActions={(
                <Space wrap>
                    <Switch
                        checked={memoryEnabled}
                        checkedChildren="记忆"
                        unCheckedChildren="记忆"
                        onChange={setMemoryEnabled}
                    />
                    <Switch
                        checked={longTermExtractionEnabled}
                        checkedChildren="长期提取"
                        unCheckedChildren="长期提取"
                        onChange={setLongTermExtractionEnabled}
                    />
                    <Tag>{sessionLabel}</Tag>
                    <Button
                        icon={<ReloadOutlined/>}
                        loading={loading}
                        onClick={() => void loadPresets()}
                    >
                        刷新预设
                    </Button>
                </Space>
            )}
            input={input}
            messages={messages}
            sending={isRequesting}
            settings={(
                <ChatSettingsModal
                    footer={(
                        <Space wrap>
                            <Button
                                onClick={() => applyPreset(selectedPresetId)}
                            >
                                重置
                            </Button>
                            <Popconfirm
                                disabled={!selectedPresetId}
                                title="删除这个预设？"
                                onConfirm={() => void removePreset()}
                            >
                                <Button
                                    danger
                                    disabled={!selectedPresetId || !canEditSelectedPreset}
                                    icon={<DeleteOutlined/>}
                                    loading={saving}
                                >
                                    删除
                                </Button>
                            </Popconfirm>
                            <Button
                                disabled={!canEditSelectedPreset}
                                icon={selectedPreset ? <EditOutlined/> : <SaveOutlined/>}
                                loading={saving}
                                type="primary"
                                onClick={() => void savePreset()}
                            >
                                {selectedPreset ? '更新预设' : '创建预设'}
                            </Button>
                        </Space>
                    )}
                    open={settingsOpen}
                    title="Agent 调试设置"
                    onClose={() => setSettingsOpen(false)}
                >
                    <Form form={form} initialValues={defaultFormValues()} layout="vertical">
                        <Form.Item label="预设" name="presetId">
                            <Select<number>
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
                        <Space wrap align="start">
                            <Form.Item
                                label="名称"
                                name="name"
                                rules={[{required: true, message: '请输入名称'}]}
                            >
                                <Input maxLength={128} style={{minWidth: 260}}/>
                            </Form.Item>
                            <Form.Item label="启用" name="enabled" valuePropName="checked">
                                <Switch
                                    disabled={saving || !canEditSelectedPreset}
                                    onChange={(checked) => void togglePreset(checked)}
                                />
                            </Form.Item>
                        </Space>
                        <Form.Item label="描述" name="description">
                            <Input maxLength={512}/>
                        </Form.Item>
                        <Form.Item label="系统提示词" name="systemPrompt">
                            <Input.TextArea rows={5}/>
                        </Form.Item>
                        <Space wrap align="start">
                            <Form.Item label="Temperature" name="temperature">
                                <InputNumber max={2} min={0} step={0.1}/>
                            </Form.Item>
                            <Form.Item label="Top P" name="topP">
                                <InputNumber max={1} min={0} step={0.05}/>
                            </Form.Item>
                            <Form.Item label="Max Tokens" name="maxTokens">
                                <InputNumber min={1}/>
                            </Form.Item>
                            <Form.Item label="Top K" name="topK">
                                <InputNumber min={1}/>
                            </Form.Item>
                            <Form.Item label="Thinking Budget" name="thinkingBudget">
                                <InputNumber min={1}/>
                            </Form.Item>
                            <Form.Item label="Tool Timeout" name="toolExecutionTimeoutSeconds">
                                <InputNumber min={1}/>
                            </Form.Item>
                        </Space>
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
                        <Space wrap align="start">
                            <Form.Item label="Max Parallel Tools" name="maxParallelTools">
                                <InputNumber min={1}/>
                            </Form.Item>
                            <Form.Item label="启用工具" name="enabledToolNames">
                                <Select
                                    mode="tags"
                                    options={defaultAgentToolNames.map((tool) => ({
                                        label: tool,
                                        value: tool,
                                    }))}
                                    style={{minWidth: 260}}
                                />
                            </Form.Item>
                        </Space>
                    </Form>
                </ChatSettingsModal>
            )}
            sidebarLoading={sessionLoading}
            subtitle={subtitle}
            title="Agent 调试"
            onArchiveConversation={(conversationKey) => void archiveCurrentSession(conversationKey)}
            onConversationChange={handleConversationChange}
            onCopyMessage={(message) => void handleCopyMessage(message)}
            onInputChange={setInput}
            onNewConversation={() => void newConversation()}
            onOpenSettings={() => setSettingsOpen(true)}
            onReloadConversations={() => void loadSessions()}
            onReloadMessage={handleReloadMessage}
            onSend={handleSend}
            onStop={abort}
        />
    )
}

function defaultFormValues(): DebugFormValues {
    return {
        presetId: undefined,
        name: '',
        description: undefined,
        enabled: true,
        systemPrompt: undefined,
        temperature: 0.7,
        maxTokens: undefined,
        topP: 0.9,
        topK: undefined,
        enableThinking: true,
        thinkingBudget: undefined,
        enableSearch: false,
        multiModel: false,
        enabledToolNames: defaultAgentToolNames,
        parallelToolExecution: false,
        maxParallelTools: 5,
        toolExecutionTimeoutSeconds: 300,
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
