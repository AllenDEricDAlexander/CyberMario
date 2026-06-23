import {App, Checkbox, Drawer, Form, InputNumber, Select, Space, Switch, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {
    applyRagEventToMessage,
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
import {
    archiveAgentMemorySession,
    createAgentMemorySession,
    getAgentMemoryMessages,
    getAgentMemorySessions,
} from '../agent/agentService'
import type {AgentMemorySessionResponse} from '../agent/agentTypes'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {createRagFeedback, getRagKnowledgeBases, streamRagChat} from './ragService'
import type {KnowledgeBaseResponse, RagFeedbackType, RagSearchMode, SourceReferenceResponse} from './ragTypes'

type RagConfig = {
    knowledgeBaseIds: number[]
    topK: number
    candidateTopK: number
    similarityThreshold: number
    searchMode: RagSearchMode
    rerankEnabled: boolean
}

type UpdateAssistantMessage = (
    assistantId: string,
    updater: (message: ChatWorkspaceMessage) => ChatWorkspaceMessage
) => boolean

const defaultRagConfig: RagConfig = {
    knowledgeBaseIds: [],
    topK: 6,
    candidateTopK: 50,
    similarityThreshold: 0.55,
    searchMode: 'HYBRID',
    rerankEnabled: false,
}

const initialMessages: ChatWorkspaceMessage[] = [
    {
        id: 'welcome',
        role: 'assistant',
        content: '我是 CyberMario RAG 问答助手，请先选择知识库再提问。',
        status: 'success',
    },
]

function RagChatPage() {
    const auth = useAuth()
    const {message: appMessage} = App.useApp()
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
    const [sessionId, setSessionId] = useState('')
    const [sessionLoading, setSessionLoading] = useState(false)
    const [memoryContextEnabled, setMemoryContextEnabled] = useState(true)
    const [longTermExtractionEnabled, setLongTermExtractionEnabled] = useState(true)
    const [ragConfig, setRagConfig] = useState<RagConfig>(defaultRagConfig)
    const [input, setInput] = useState('')
    const [settingsOpen, setSettingsOpen] = useState(false)
    const [error, setError] = useState('')
    const [sources, setSources] = useState<SourceReferenceResponse[]>([])
    const [sourceOpen, setSourceOpen] = useState(false)
    const abortControllerRef = useRef<AbortController | null>(null)
    const updateAssistantMessageRef = useRef<UpdateAssistantMessage | null>(null)
    const historyRequestSeqRef = useRef(0)
    const sessionListRequestSeqRef = useRef(0)
    const knowledgeBaseListRequestSeqRef = useRef(0)
    const isMountedRef = useRef(true)
    const canCreateFeedback = canUseRbacButton(auth, ragButtonCodes.feedback.create)

    const conversations = useMemo(
        () => sessions.map(mapSessionToConversation),
        [sessions]
    )
    const sessionLabel = useMemo(() => sessionId || 'New RAG session', [sessionId])

    function nextHistoryRequestToken() {
        historyRequestSeqRef.current += 1
        return historyRequestSeqRef.current
    }

    useEffect(() => {
        isMountedRef.current = true
        return () => {
            isMountedRef.current = false
            abortControllerRef.current?.abort()
            abortControllerRef.current = null
            nextHistoryRequestToken()
        }
    }, [])

    const loadKnowledgeBases = useCallback(async () => {
        knowledgeBaseListRequestSeqRef.current += 1
        const requestToken = knowledgeBaseListRequestSeqRef.current
        const isLatestRequest = () => isMountedRef.current && knowledgeBaseListRequestSeqRef.current === requestToken
        try {
            const page = await getRagKnowledgeBases({page: 1, size: 200})
            if (!isLatestRequest()) {
                return
            }
            setKnowledgeBases(page.records)
        } catch (requestError) {
            if (!isLatestRequest()) {
                return
            }
            reportGlobalError(requestError)
        }
    }, [])

    const loadSessions = useCallback(async () => {
        sessionListRequestSeqRef.current += 1
        const requestToken = sessionListRequestSeqRef.current
        const isLatestRequest = () => isMountedRef.current && sessionListRequestSeqRef.current === requestToken
        if (isMountedRef.current) {
            setSessionLoading(true)
        }
        try {
            const page = await getAgentMemorySessions({page: 1, size: 100, entryType: 'RAG_CHAT'})
            if (!isLatestRequest()) {
                return
            }
            setSessions(page.records)
        } catch (requestError) {
            if (!isLatestRequest()) {
                return
            }
            reportGlobalError(requestError)
        } finally {
            if (isLatestRequest()) {
                setSessionLoading(false)
            }
        }
    }, [])

    useEffect(() => {
        void loadKnowledgeBases()
        void loadSessions()
    }, [loadKnowledgeBases, loadSessions])

    const handleWorkspaceRequest = useCallback(async (
        requestParams: ChatWorkspaceRequest,
        assistantId: string
    ) => {
        const abortController = new AbortController()
        abortControllerRef.current = abortController

        try {
            await streamRagChat(
                {
                    sessionId: requestParams.conversationKey,
                    memoryContextEnabled,
                    longTermExtractionEnabled,
                    question: requestParams.message,
                    knowledgeBaseIds: ragConfig.knowledgeBaseIds,
                    retrievalOptions: {
                        topK: ragConfig.topK,
                        candidateTopK: ragConfig.candidateTopK,
                        similarityThreshold: ragConfig.similarityThreshold,
                        searchMode: ragConfig.searchMode,
                        rerankEnabled: ragConfig.rerankEnabled,
                    },
                    withSources: true,
                },
                abortController.signal,
                (event) => {
                    if (!isMountedRef.current) {
                        return
                    }
                    if (event.type === 'metadata' && event.data.sessionId) {
                        setSessionId(event.data.sessionId)
                    }
                    updateAssistantMessageRef.current?.(
                        assistantId,
                        current => applyRagEventToMessage(current, event)
                    )
                },
            )
            if (!isMountedRef.current) {
                return
            }
            updateAssistantMessageRef.current?.(assistantId, markMessageSucceeded)
        } catch (requestError) {
            if (!isMountedRef.current || abortController.signal.aborted) {
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
    }, [longTermExtractionEnabled, memoryContextEnabled, ragConfig])

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
        if (!nextMessage) {
            appMessage.warning('请输入问题')
            return
        }
        if (ragConfig.knowledgeBaseIds.length === 0) {
            appMessage.warning('请选择知识库')
            return
        }
        if (isRequesting) {
            return
        }

        setInput('')
        setError('')
        nextHistoryRequestToken()
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'RAG_CHAT',
        })
    }

    async function handleNewConversation() {
        abort()
        const requestToken = nextHistoryRequestToken()
        try {
            const session = await createAgentMemorySession({
                entryType: 'RAG_CHAT',
                memoryContextEnabled,
                longTermExtractionEnabled,
            })
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
        closeSourceDrawer()
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
            if (!isMountedRef.current) {
                return
            }
            appMessage.success('会话已归档')
            await loadSessions()
            if (isMountedRef.current && isActiveConversation && historyRequestSeqRef.current === requestToken) {
                setSessionId('')
                setMessages(initialMessages)
                setInput('')
                setError('')
                closeSourceDrawer()
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
            setMemoryContextEnabled(session.memoryContextEnabled ?? session.memoryEnabled ?? true)
            setLongTermExtractionEnabled(session.longTermExtractionEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
        closeSourceDrawer()

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

    function handleSourceSelect(source: SourceReferenceResponse) {
        setSources([source])
        setSourceOpen(true)
    }

    function closeSourceDrawer() {
        setSourceOpen(false)
        setSources([])
    }

    function canFeedbackMessage(message: ChatWorkspaceMessage) {
        return message.role === 'assistant' &&
            message.id !== 'welcome' &&
            message.content.trim().length > 0 &&
            Boolean(message.traceId || message.messageId || (message.sources && message.sources.length > 0))
    }

    async function submitFeedback(item: ChatWorkspaceMessage, feedbackType: RagFeedbackType) {
        try {
            await createRagFeedback({
                traceId: item.traceId,
                messageId: item.messageId,
                feedbackType,
                question: typeof item.question === 'string' ? item.question : undefined,
                answer: item.content,
                sourceChunkIds: item.sources?.map((source) => source.chunkId),
            })
            appMessage.success('反馈已提交')
        } catch (requestError) {
            reportGlobalError(requestError)
        }
    }

    function updateRagConfig<Field extends keyof RagConfig>(field: Field, value: RagConfig[Field]) {
        setRagConfig((current) => ({
            ...current,
            [field]: value,
        }))
    }

    const subtitle = error ? (
        <Space direction="vertical" size={0}>
            <span>基于已入库知识库回答问题，回答来源可在消息内查看。</span>
            <Typography.Text type="danger">{error}</Typography.Text>
        </Space>
    ) : '基于已入库知识库回答问题，回答来源可在消息内查看。'

    return (
        <>
            <ChatWorkspace
                activeConversationKey={sessionId || undefined}
                brandDescription="RAG memory sessions"
                brandTitle="CyberMario RAG"
                canFeedback={canCreateFeedback}
                canFeedbackMessage={canFeedbackMessage}
                conversations={conversations}
                headerActions={(
                    <Space wrap>
                        <Switch
                            checked={memoryContextEnabled}
                            checkedChildren="长期记忆"
                            unCheckedChildren="长期记忆"
                            onChange={setMemoryContextEnabled}
                        />
                        <Switch
                            checked={longTermExtractionEnabled}
                            checkedChildren="长期提取"
                            unCheckedChildren="长期提取"
                            onChange={setLongTermExtractionEnabled}
                        />
                        <Tag>{sessionLabel}</Tag>
                    </Space>
                )}
                input={input}
                messages={messages}
                sending={isRequesting}
                settings={(
                    <ChatSettingsModal
                        open={settingsOpen}
                        title="RAG 检索设置"
                        onClose={() => setSettingsOpen(false)}
                        onReset={() => setRagConfig(defaultRagConfig)}
                        onSave={() => setSettingsOpen(false)}
                    >
                        <Form layout="vertical">
                            <Form.Item label="知识库" required>
                                <Select
                                    mode="multiple"
                                    options={knowledgeBases.map((item) => ({label: item.name, value: item.id}))}
                                    placeholder="选择知识库"
                                    value={ragConfig.knowledgeBaseIds}
                                    onChange={(value) => updateRagConfig('knowledgeBaseIds', value)}
                                />
                            </Form.Item>
                            <Space wrap align="start">
                                <Form.Item label="TopK">
                                    <InputNumber
                                        min={1}
                                        max={20}
                                        value={ragConfig.topK}
                                        onChange={(value) => updateRagConfig('topK', value ?? defaultRagConfig.topK)}
                                    />
                                </Form.Item>
                                <Form.Item label="候选数量">
                                    <InputNumber
                                        min={1}
                                        max={100}
                                        value={ragConfig.candidateTopK}
                                        onChange={(value) => updateRagConfig('candidateTopK', value ?? defaultRagConfig.candidateTopK)}
                                    />
                                </Form.Item>
                                <Form.Item label="相似度阈值">
                                    <InputNumber
                                        min={0}
                                        max={1}
                                        step={0.01}
                                        value={ragConfig.similarityThreshold}
                                        onChange={(value) => updateRagConfig('similarityThreshold', value ?? defaultRagConfig.similarityThreshold)}
                                    />
                                </Form.Item>
                            </Space>
                            <Form.Item label="搜索模式">
                                <Select
                                    options={[
                                        {label: '向量', value: 'VECTOR'},
                                        {label: '关键词', value: 'KEYWORD'},
                                        {label: '混合', value: 'HYBRID'},
                                        {label: '混合重排', value: 'HYBRID_RERANK'},
                                    ]}
                                    value={ragConfig.searchMode}
                                    onChange={(value) => updateRagConfig('searchMode', value)}
                                />
                            </Form.Item>
                            <Form.Item>
                                <Checkbox
                                    checked={ragConfig.rerankEnabled}
                                    onChange={(event) => updateRagConfig('rerankEnabled', event.target.checked)}
                                >
                                    启用 Rerank
                                </Checkbox>
                            </Form.Item>
                        </Form>
                    </ChatSettingsModal>
                )}
                sidebarLoading={sessionLoading}
                subtitle={subtitle}
                title="RAG 问答"
                onArchiveConversation={(conversationKey) => void archiveCurrentSession(conversationKey)}
                onConversationChange={handleConversationChange}
                onFeedback={(chatMessage, feedbackType) => void submitFeedback(chatMessage, feedbackType)}
                onInputChange={setInput}
                onNewConversation={() => void handleNewConversation()}
                onOpenSettings={() => setSettingsOpen(true)}
                onReloadConversations={() => void loadSessions()}
                onSend={handleSend}
                onSourceSelect={handleSourceSelect}
                onStop={abort}
            />

            <Drawer onClose={closeSourceDrawer} open={sourceOpen} title="引用来源" width={560}>
                <Space direction="vertical" style={{width: '100%'}}>
                    {sources.map((source, index) => (
                        <section
                            aria-label={`来源 ${index + 1}`}
                            key={source.sourceId}
                            style={{border: '1px solid #f0f0f0', borderRadius: 8, padding: 12}}
                        >
                            <Space wrap>
                                <Tag color="blue">score={source.score.toFixed(4)}</Tag>
                                {source.rerankScore !== undefined && <Tag color="purple">rerank={source.rerankScore.toFixed(4)}</Tag>}
                                {source.matchedBy && <Tag>{source.matchedBy}</Tag>}
                                <Tag>chunk={source.chunkIndex}</Tag>
                                <Typography.Text strong>
                                    {source.documentName || `文档 ${source.documentId}`}
                                </Typography.Text>
                            </Space>
                            <Typography.Paragraph copyable style={{marginTop: 8}}>
                                {source.content}
                            </Typography.Paragraph>
                        </section>
                    ))}
                </Space>
            </Drawer>
        </>
    )
}

export const Component = RagChatPage
