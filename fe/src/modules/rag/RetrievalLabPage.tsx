import {SearchOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Input, InputNumber, List, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {getRagKnowledgeBases, searchRagRetrieval} from './ragService'
import type {KnowledgeBaseResponse, RetrievalSearchResponse} from './ragTypes'

function RetrievalLabPage() {
    const {message} = App.useApp()
    const [form] = Form.useForm()
    const [loading, setLoading] = useState(false)
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [result, setResult] = useState<RetrievalSearchResponse | null>(null)

    useEffect(() => {
        getRagKnowledgeBases({page: 1, size: 200}).then((page) => setKnowledgeBases(page.records))
    }, [])

    async function search() {
        const values = await form.validateFields()
        setLoading(true)
        try {
            const response = await searchRagRetrieval(values)
            setResult(response)
        } catch (error) {
            message.error((error as Error).message)
        } finally {
            setLoading(false)
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
                    initialValues={{topK: 6, similarityThreshold: 0.55, searchMode: 'VECTOR'}}
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
                    </Space>
                    <Button icon={<SearchOutlined/>} loading={loading} onClick={search} type="primary">开始检索</Button>
                </Form>
            </Card>
            <Card style={{marginTop: 16}}
                  title={result ? `召回结果：${result.results.length} 条，耗时 ${result.costMs}ms` : '召回结果'}>
                <List
                    dataSource={result?.results ?? []}
                    locale={{emptyText: '暂无召回结果'}}
                    renderItem={(item, index) => (
                        <List.Item>
                            <Card size="small" style={{width: '100%'}}>
                                <Space wrap>
                                    <Tag color="blue">#{index + 1}</Tag>
                                    <Tag color="green">score={item.score.toFixed(4)}</Tag>
                                    <Typography.Text
                                        strong>{item.documentName || `文档 ${item.documentId}`}</Typography.Text>
                                    <Typography.Text type="secondary">chunk={item.chunkIndex}</Typography.Text>
                                </Space>
                                <Typography.Paragraph copyable style={{marginTop: 12}}>
                                    {item.content}
                                </Typography.Paragraph>
                            </Card>
                        </List.Item>
                    )}
                />
            </Card>
        </>
    )
}

export const Component = RetrievalLabPage
