import {SearchOutlined} from '@ant-design/icons'
import {Button, Card, Checkbox, Drawer, Form, Input, InputNumber, List, Select, Space, Tabs, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {getRagKnowledgeBases, getRagRetrievalTrace, searchRagRetrieval} from './ragService'
import type {KnowledgeBaseResponse, RagRetrievalTraceResponse, RagSearchMode, RetrievalSearchResponse} from './ragTypes'

type RetrievalLabFormValues = {
    query: string
    knowledgeBaseIds: number[]
    topK: number
    candidateTopK: number
    similarityThreshold: number
    searchMode: RagSearchMode
    rerankEnabled: boolean
    debug: boolean
}

function RetrievalLabPage() {
    const auth = useAuth()
    const [form] = Form.useForm<RetrievalLabFormValues>()
    const [loading, setLoading] = useState(false)
    const [traceLoading, setTraceLoading] = useState(false)
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [result, setResult] = useState<RetrievalSearchResponse | null>(null)
    const [trace, setTrace] = useState<RagRetrievalTraceResponse | null>(null)
    const [traceOpen, setTraceOpen] = useState(false)

    const canDebug = canUseRbacButton(auth, ragButtonCodes.retrieval.debug)
    const canTrace = canUseRbacButton(auth, ragButtonCodes.retrieval.trace)

    useEffect(() => {
        void getRagKnowledgeBases({page: 1, size: 200}).then((page) => setKnowledgeBases(page.records))
    }, [])

    async function search() {
        const values = await form.validateFields()
        setLoading(true)
        try {
            const response = await searchRagRetrieval(values)
            setResult(response)
            setTrace(null)
        } catch (error) {
            reportGlobalError(error)
        } finally {
            setLoading(false)
        }
    }

    async function openTrace() {
        if (!result?.traceId) {
            return
        }
        setTraceLoading(true)
        try {
            setTrace(await getRagRetrievalTrace(result.traceId))
            setTraceOpen(true)
        } catch (error) {
            reportGlobalError(error)
        } finally {
            setTraceLoading(false)
        }
    }

    return (
        <>
            <PageToolbar
                description="输入问题查看召回片段、分数和来源，不调用生成模型。"
                title="检索调试"
            />
            <Card>
                <Form
                    form={form}
                    initialValues={{
                        topK: 6,
                        candidateTopK: 50,
                        similarityThreshold: 0.55,
                        searchMode: 'HYBRID',
                        rerankEnabled: false,
                        debug: true,
                    }}
                    layout="vertical"
                >
                    <Form.Item label="问题" name="query" rules={[{required: true, message: '请输入问题'}]}>
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Space wrap>
                        <Form.Item label="知识库" name="knowledgeBaseIds"
                                   rules={[{required: true, message: '请选择知识库'}]}>
                            <Select
                                mode="multiple"
                                options={knowledgeBases.map((item) => ({label: item.name, value: item.id}))}
                                style={{width: 280}}
                            />
                        </Form.Item>
                        <Form.Item label="TopK" name="topK">
                            <InputNumber min={1} max={20}/>
                        </Form.Item>
                        <Form.Item label="候选 TopK" name="candidateTopK">
                            <InputNumber min={1} max={100}/>
                        </Form.Item>
                        <Form.Item label="阈值" name="similarityThreshold">
                            <InputNumber min={0} max={1} step={0.01}/>
                        </Form.Item>
                        <Form.Item label="模式" name="searchMode">
                            <Select style={{width: 160}} options={[
                                {label: '向量检索', value: 'VECTOR'},
                                {label: '关键词检索', value: 'KEYWORD'},
                                {label: '混合检索', value: 'HYBRID'},
                                {label: '混合重排', value: 'HYBRID_RERANK'},
                            ]}/>
                        </Form.Item>
                        <Form.Item label="Rerank" name="rerankEnabled" valuePropName="checked">
                            <Checkbox>开启</Checkbox>
                        </Form.Item>
                    </Space>
                    <Button disabled={!canDebug} icon={<SearchOutlined/>} loading={loading} onClick={voidify(search)}
                            type="primary">开始检索</Button>
                </Form>
            </Card>
            <Card
                extra={canTrace && result?.traceId ? (
                    <Button loading={traceLoading} onClick={voidify(openTrace)} size="small">Trace 详情</Button>
                ) : null}
                style={{marginTop: 16}}
                title={resultTitle(result)}
            >
                <Tabs
                    items={[
                        {key: 'final', label: '最终结果', children: <StageList records={result?.results ?? []}/>},
                        {key: 'vector', label: `Vector ${result?.stages.vector.length ?? 0}`, children: <StageList records={result?.stages.vector ?? []}/>},
                        {key: 'keyword', label: `Keyword ${result?.stages.keyword.length ?? 0}`, children: <StageList records={result?.stages.keyword ?? []}/>},
                        {key: 'fused', label: `Hybrid ${result?.stages.fused.length ?? 0}`, children: <StageList records={result?.stages.fused ?? []}/>},
                        {key: 'reranked', label: `Rerank ${result?.stages.reranked.length ?? 0}`, children: <StageList records={result?.stages.reranked ?? []}/>},
                    ]}
                />
            </Card>
            <Drawer onClose={() => setTraceOpen(false)} open={traceOpen} title="Trace 详情" width={720}>
                {trace && (
                    <Space direction="vertical" style={{width: '100%'}}>
                        <Space wrap>
                            <Tag>trace={trace.traceId}</Tag>
                            <Tag color="blue">{trace.searchMode}</Tag>
                            <Tag color={trace.rerankEnabled ? 'purple' : 'default'}>
                                Rerank {trace.rerankEnabled ? '开启' : '关闭'}
                            </Tag>
                            <Tag>{trace.costMs}ms</Tag>
                        </Space>
                        <Tabs
                            items={[
                                {key: 'vector', label: `Vector ${trace.vector.length}`, children: <StageList records={trace.vector}/>},
                                {key: 'keyword', label: `Keyword ${trace.keyword.length}`, children: <StageList records={trace.keyword}/>},
                                {key: 'fused', label: `Hybrid ${trace.fused.length}`, children: <StageList records={trace.fused}/>},
                                {key: 'reranked', label: `Rerank ${trace.reranked.length}`, children: <StageList records={trace.reranked}/>},
                            ]}
                        />
                    </Space>
                )}
            </Drawer>
        </>
    )
}

function resultTitle(result: RetrievalSearchResponse | null) {
    if (!result) {
        return '召回结果'
    }
    return `召回结果：${result.results.length} 条，模式 ${result.searchMode}，耗时 ${result.costMs}ms，trace=${result.traceId}`
}

function StageList({records}: { records: RetrievalSearchResponse['results'] }) {
    return (
        <List
            dataSource={records}
            locale={{emptyText: '暂无召回结果'}}
            renderItem={(item, index) => (
                <List.Item>
                    <Card size="small" style={{width: '100%'}}>
                        <Space wrap>
                            <Tag color="blue">#{index + 1}</Tag>
                            <Tag color="green">score={item.score.toFixed(4)}</Tag>
                            {item.vectorScore !== undefined && <Tag>vector={item.vectorScore.toFixed(4)}</Tag>}
                            {item.keywordScore !== undefined && <Tag>keyword={item.keywordScore.toFixed(4)}</Tag>}
                            {item.fusionScore !== undefined && <Tag>fusion={item.fusionScore.toFixed(4)}</Tag>}
                            {item.rerankScore !== undefined && <Tag color="purple">rerank={item.rerankScore.toFixed(4)}</Tag>}
                            {item.matchedBy && <Tag>{item.matchedBy}</Tag>}
                            <Typography.Text strong>{item.documentName || `文档 ${item.documentId}`}</Typography.Text>
                            <Typography.Text type="secondary">chunk={item.chunkIndex}</Typography.Text>
                        </Space>
                        <Typography.Paragraph copyable style={{marginTop: 12}}>
                            {item.content}
                        </Typography.Paragraph>
                    </Card>
                </List.Item>
            )}
        />
    )
}

export const Component = RetrievalLabPage
