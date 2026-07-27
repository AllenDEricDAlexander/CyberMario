import {CommentOutlined, EyeOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Form, Input, InputNumber, Select, Space, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {DateTimeText} from '../../components/DateTimeText'
import {ErrorState} from '../../components/ErrorState'
import {FilterBar} from '../../components/FilterBar'
import {FormDrawer} from '../../components/FormDrawer'
import {PageSection, PageStack} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
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
        {title: 'ID', dataIndex: 'id', fixed: 'left', width: 80},
        {
            title: '用户',
            dataIndex: 'username',
            width: 170,
            render: (_, record) => (
                <StackedCell primary={record.username ?? '-'} secondary={`用户 ID ${record.userId ?? '-'}`}/>
            ),
        },
        {
            title: '线程',
            dataIndex: 'threadId',
            width: 240,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '预设', dataIndex: 'presetId', width: 90, render: valueOrDash},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentConversationStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`,
        },
        {
            title: '请求 / Trace',
            dataIndex: 'requestId',
            width: 220,
            render: (_, record) => (
                <StackedCell plain primary={record.requestId ?? '-'} secondary={record.traceId ?? '无 trace'}/>
            ),
        },
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: valueOrDash},
        {
            title: '时间',
            dataIndex: 'startedAt',
            width: 210,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={<DateTimeText value={record.startedAt}/>}
                    secondary={<>完成 <DateTimeText value={record.finishedAt}/></>}
                />
            ),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 90,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'detail',
                            label: '详情',
                            icon: <EyeOutlined/>,
                            onClick: () => void openDetail(record),
                        },
                    ]}
                />
            ),
        },
    ]

    function applyFilters(values: AuditQueryForm) {
        setFilters(values)
        void load(1, size)
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
                icon={<CommentOutlined/>}
                title="对话审计"
            />
            <FilterBar<AuditQueryForm>
                form={form}
                loading={loading}
                onReset={() => applyFilters({})}
                onSearch={applyFilters}
            >
                <Form.Item label="用户 ID" name="userId">
                    <InputNumber min={1} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="用户名" name="username">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="线程 ID" name="threadId">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="预设 ID" name="presetId">
                    <InputNumber min={1} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select
                        allowClear
                        options={[
                            {label: 'RUNNING', value: 'RUNNING'},
                            {label: 'SUCCESS', value: 'SUCCESS'},
                            {label: 'FAILED', value: 'FAILED'},
                            {label: 'CANCELLED', value: 'CANCELLED'},
                        ]}
                        placeholder="全部"
                    />
                </Form.Item>
            </FilterBar>
            <DataTable<AgentConversationAuditResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="放宽用户、线程或状态条件后重新查询。"
                emptyTitle="没有匹配的对话记录"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1400}}
                title="对话记录"
            />
            <FormDrawer
                description={selected ? `线程 ${selected.threadId}` : undefined}
                footer={false}
                onClose={() => setDrawerOpen(false)}
                open={drawerOpen}
                size="xl"
                title="对话审计详情"
            >
                {selected && (
                    <PageStack>
                        <Space wrap>
                            <Tag>ID={selected.id}</Tag>
                            <Tag color={statusColor(selected.status)}>{selected.status}</Tag>
                            {selected.traceId && <Tag>trace={selected.traceId}</Tag>}
                        </Space>
                        <PageSection title="运行配置">
                            <Typography.Paragraph className="payload-text" copyable>
                                {formatJson(selected.effectiveConfigJson)}
                            </Typography.Paragraph>
                        </PageSection>
                        {detailError && <ErrorState inline message={detailError} title="消息加载失败"/>}
                        <DataTable<AgentConversationMessageAuditResponse>
                            columns={[
                                {title: '#', dataIndex: 'seqNo', width: 70},
                                {title: '角色', dataIndex: 'role', width: 110},
                                {title: '类型', dataIndex: 'messageType', width: 110},
                                {title: '字数', dataIndex: 'contentChars', width: 90, render: valueOrDash},
                                {
                                    title: '内容',
                                    dataIndex: 'content',
                                    render: (value?: string) => (
                                        <Typography.Paragraph className="payload-text" copyable>
                                            {value || '-'}
                                        </Typography.Paragraph>
                                    ),
                                },
                            ]}
                            count={messages.length}
                            dataSource={messages}
                            emptyDescription="这次对话没有留下可展示的消息记录。"
                            emptyTitle="暂无消息"
                            loading={detailLoading}
                            pagination={false}
                            rowKey="id"
                            title="消息"
                        />
                    </PageStack>
                )}
            </FormDrawer>
        </>
    )
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
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
