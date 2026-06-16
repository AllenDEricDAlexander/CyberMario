import {EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useState} from 'react'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {getAgentConversationAuditMessages, getAgentConversationAudits} from './agentService'
import type {
    AgentConversationAuditResponse,
    AgentConversationMessageAuditResponse,
    AgentConversationStatus,
} from './agentTypes'

type AuditQueryForm = {
    userId?: number
    username?: string
    threadId?: string
    presetId?: number
    status?: AgentConversationStatus
}

function AgentConversationAuditPage() {
    const [form] = Form.useForm<AuditQueryForm>()
    const [filters, setFilters] = useState<AuditQueryForm>({})
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [messages, setMessages] = useState<AgentConversationMessageAuditResponse[]>([])
    const [selected, setSelected] = useState<AgentConversationAuditResponse | null>(null)
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState('')

    const loadAudits = useCallback(
        (request: { page: number; size: number }) => getAgentConversationAudits({...request, ...filters}),
        [filters],
    )
    const {loading, records, page, size, total, load} = usePageData<AgentConversationAuditResponse>(loadAudits)

    const columns: ColumnsType<AgentConversationAuditResponse> = [
        {title: 'ID', dataIndex: 'id', width: 80},
        {title: '用户', dataIndex: 'username', width: 130, render: valueOrDash},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {
            title: '线程',
            dataIndex: 'threadId',
            width: 220,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '预设', dataIndex: 'presetId', width: 90, render: valueOrDash},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentConversationStatus) => <Tag color={statusColor(value)}>{value}</Tag>
        },
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`
        },
        {title: '请求', dataIndex: 'requestId', width: 180, render: valueOrDash},
        {title: 'Trace', dataIndex: 'traceId', width: 180, render: valueOrDash},
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: valueOrDash},
        {title: '开始时间', dataIndex: 'startedAt', width: 190, render: renderDateTime},
        {title: '完成时间', dataIndex: 'finishedAt', width: 190, render: renderDateTime},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button icon={<EyeOutlined/>} onClick={() => void openDetail(record)} size="small">详情</Button>
            ),
        },
    ]

    async function search() {
        setFilters(await form.validateFields())
        await load(1, size)
    }

    async function openDetail(record: AgentConversationAuditResponse) {
        setSelected(record)
        setMessages([])
        setDetailError('')
        setDrawerOpen(true)
        setDetailLoading(true)
        try {
            setMessages(await getAgentConversationAuditMessages(record.id))
        } catch (requestError) {
            setDetailError(resolveErrorMessage(requestError))
        } finally {
            setDetailLoading(false)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="查询 Agent 对话原文、运行配置和执行状态，仅 SUPER_ADMIN 可访问。"
                title="对话审计"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space wrap>
                        <Form.Item label="用户 ID" name="userId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="用户名" name="username">
                            <Input allowClear/>
                        </Form.Item>
                        <Form.Item label="线程 ID" name="threadId">
                            <Input allowClear style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item label="预设 ID" name="presetId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="状态" name="status">
                            <Select allowClear style={{width: 160}} options={[
                                {label: 'RUNNING', value: 'RUNNING'},
                                {label: 'SUCCESS', value: 'SUCCESS'},
                                {label: 'FAILED', value: 'FAILED'},
                                {label: 'CANCELLED', value: 'CANCELLED'},
                            ]}/>
                        </Form.Item>
                        <Form.Item label=" ">
                            <Button icon={<SearchOutlined/>} onClick={voidify(search)} type="primary">查询</Button>
                        </Form.Item>
                    </Space>
                </Form>
            </Card>
            <Table<AgentConversationAuditResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1800}}
                style={{marginTop: 16}}
            />
            <Drawer onClose={() => setDrawerOpen(false)} open={drawerOpen} title="对话审计详情" width={860}>
                {selected && (
                    <Space direction="vertical" size={16} style={{width: '100%'}}>
                        <Space wrap>
                            <Tag>ID={selected.id}</Tag>
                            <Tag color={statusColor(selected.status)}>{selected.status}</Tag>
                            <Tag>thread={selected.threadId}</Tag>
                            {selected.traceId && <Tag>trace={selected.traceId}</Tag>}
                        </Space>
                        <Card size="small" title="运行配置">
                            <Typography.Paragraph copyable style={{whiteSpace: 'pre-wrap'}}>
                                {formatJson(selected.effectiveConfigJson)}
                            </Typography.Paragraph>
                        </Card>
                        {detailError && <Typography.Text type="danger">{detailError}</Typography.Text>}
                        <Table<AgentConversationMessageAuditResponse>
                            columns={[
                                {title: '#', dataIndex: 'seqNo', width: 70},
                                {title: '角色', dataIndex: 'role', width: 110},
                                {title: '类型', dataIndex: 'messageType', width: 110},
                                {title: '字数', dataIndex: 'contentChars', width: 90, render: valueOrDash},
                                {
                                    title: '内容',
                                    dataIndex: 'content',
                                    render: (value?: string) => (
                                        <Typography.Paragraph copyable
                                                              style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                            {value || '-'}
                                        </Typography.Paragraph>
                                    ),
                                },
                            ]}
                            dataSource={messages}
                            loading={detailLoading}
                            pagination={false}
                            rowKey="id"
                        />
                    </Space>
                )}
            </Drawer>
        </>
    )
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function statusColor(status: AgentConversationStatus) {
    if (status === 'SUCCESS') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    if (status === 'CANCELLED') {
        return 'default'
    }
    return 'processing'
}

function formatJson(value?: string) {
    if (!value) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
        return value
    }
}

export const Component = AgentConversationAuditPage
