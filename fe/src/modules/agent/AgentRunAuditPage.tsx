import {EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import type {CollapseProps} from 'antd'
import {
    Button,
    Card,
    Collapse,
    DatePicker,
    Descriptions,
    Drawer,
    Form,
    Input,
    InputNumber,
    Select,
    Space,
    Table,
    Tag,
    Typography
} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {getAgentRunAuditDetail, getAgentRunAuditEvents, getAgentRunAudits} from './agentService'
import type {
    AgentRunAuditResponse,
    AgentRunAuditStatus,
    AgentRunEventAuditResponse,
    AgentRunEventStatus,
    AgentRunEventType,
} from './agentTypes'

type RunAuditQueryForm = {
    timeRange?: RangeValue
    userId?: number
    username?: string
    threadId?: string
    requestId?: string
    traceId?: string
    presetId?: number
    toolName?: string
    mcpServerCode?: string
    status?: AgentRunAuditStatus
}

type RunAuditFilters = Omit<RunAuditQueryForm, 'timeRange'> & {
    startAt?: string
    endAt?: string
}

type CollapseItem = NonNullable<CollapseProps['items']>[number]
type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]

const {RangePicker} = DatePicker

function AgentRunAuditPage() {
    const [form] = Form.useForm<RunAuditQueryForm>()
    const [filters, setFilters] = useState<RunAuditFilters>({})
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [events, setEvents] = useState<AgentRunEventAuditResponse[]>([])
    const [selected, setSelected] = useState<AgentRunAuditResponse | null>(null)
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState('')
    const detailRequestSeq = useRef(0)

    const loadAudits = useCallback(
        (request: { page: number; size: number }) => getAgentRunAudits({...request, ...filters}),
        [filters],
    )
    const {loading, records, page, size, total, load} = usePageData<AgentRunAuditResponse>(loadAudits, {enabled: false})

    useEffect(() => {
        void load(1)
    }, [load])

    const columns: ColumnsType<AgentRunAuditResponse> = [
        {title: 'ID', dataIndex: 'id', width: 80},
        {title: '用户', dataIndex: 'username', width: 130, render: valueOrDash},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {
            title: '线程',
            dataIndex: 'threadId',
            width: 220,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentRunAuditStatus) => <Tag color={runStatusColor(value)}>{value}</Tag>,
        },
        {title: '模型', dataIndex: 'modelCallCount', width: 90, render: countOrZero},
        {title: '工具', dataIndex: 'toolCallCount', width: 90, render: countOrZero},
        {title: 'MCP', dataIndex: 'mcpToolCallCount', width: 90, render: countOrZero},
        {title: '预设', dataIndex: 'presetId', width: 90, render: valueOrDash},
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`,
        },
        {title: '请求', dataIndex: 'requestId', width: 180, render: copyableOrDash},
        {title: 'Trace', dataIndex: 'traceId', width: 180, render: copyableOrDash},
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: errorOrDash},
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
        setFilters(toFilters(await form.validateFields()))
    }

    async function openDetail(record: AgentRunAuditResponse) {
        const requestSeq = detailRequestSeq.current + 1
        detailRequestSeq.current = requestSeq
        setSelected(record)
        setEvents([])
        setDetailError('')
        setDrawerOpen(true)
        setDetailLoading(true)
        try {
            const [nextSelected, nextEvents] = await Promise.all([
                getAgentRunAuditDetail(record.id),
                getAgentRunAuditEvents(record.id),
            ])
            if (detailRequestSeq.current === requestSeq) {
                setSelected(nextSelected)
                setEvents(nextEvents)
            }
        } catch (requestError) {
            if (detailRequestSeq.current === requestSeq) {
                setDetailError(resolveErrorMessage(requestError))
            }
        } finally {
            if (detailRequestSeq.current === requestSeq) {
                setDetailLoading(false)
            }
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="按最近运行查看 Agent ReAct 链路、模型轮次、工具调用和完整明文载荷，仅 SUPER_ADMIN 可访问。"
                title="运行审计"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space wrap>
                        <Form.Item label="用户 ID" name="userId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="时间范围" name="timeRange">
                            <RangePicker showTime style={{width: 360}}/>
                        </Form.Item>
                        <Form.Item label="用户名" name="username">
                            <Input allowClear/>
                        </Form.Item>
                        <Form.Item label="线程 ID" name="threadId">
                            <Input allowClear style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item label="请求 ID" name="requestId">
                            <Input allowClear style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item label="Trace ID" name="traceId">
                            <Input allowClear style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item label="预设 ID" name="presetId">
                            <InputNumber min={1}/>
                        </Form.Item>
                        <Form.Item label="工具名" name="toolName">
                            <Input allowClear style={{width: 180}}/>
                        </Form.Item>
                        <Form.Item label="MCP 服务" name="mcpServerCode">
                            <Input allowClear style={{width: 160}}/>
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
            <Table<AgentRunAuditResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 2100}}
                style={{marginTop: 16}}
            />
            <Drawer onClose={() => setDrawerOpen(false)} open={drawerOpen} title="运行审计详情" width={980}>
                {selected && (
                    <Space direction="vertical" size={16} style={{width: '100%'}}>
                        <Space wrap>
                            <Tag>ID={selected.id}</Tag>
                            <Tag color={runStatusColor(selected.status)}>{selected.status}</Tag>
                            <Tag>thread={selected.threadId}</Tag>
                            {selected.requestId && <Tag>request={selected.requestId}</Tag>}
                            {selected.traceId && <Tag>trace={selected.traceId}</Tag>}
                        </Space>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="用户">{selected.username ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="用户 ID">{selected.userId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="模型轮次">{selected.modelCallCount ?? 0}</Descriptions.Item>
                            <Descriptions.Item label="工具调用">{selected.toolCallCount ?? 0}</Descriptions.Item>
                            <Descriptions.Item label="MCP 调用">{selected.mcpToolCallCount ?? 0}</Descriptions.Item>
                            <Descriptions.Item
                                label="耗时">{selected.durationMs === undefined || selected.durationMs === null ? '-' : `${selected.durationMs}ms`}</Descriptions.Item>
                        </Descriptions>
                        <Card size="small" title="运行配置">
                            <PayloadText value={selected.effectiveConfigJson}/>
                        </Card>
                        <Card size="small" title="用户输入">
                            <PayloadText value={selected.userMessage}/>
                        </Card>
                        {(selected.finalThinking || selected.finalMessage || selected.errorMessage) && (
                            <Card size="small" title="最终结果">
                                {selected.finalThinking &&
                                    <PayloadBlock title="Thinking" value={selected.finalThinking}/>}
                                {selected.finalMessage && <PayloadBlock title="Message" value={selected.finalMessage}/>}
                                {selected.errorMessage && <PayloadBlock danger title={selected.errorCode ?? 'Error'}
                                                                        value={selected.errorMessage}/>}
                            </Card>
                        )}
                        {detailError && <Typography.Text type="danger">{detailError}</Typography.Text>}
                        <Table<AgentRunEventAuditResponse>
                            columns={eventColumns()}
                            dataSource={events}
                            expandable={{
                                expandedRowRender: (record) => <EventPayload event={record}/>,
                                rowExpandable: hasEventPayload,
                            }}
                            loading={detailLoading}
                            pagination={false}
                            rowKey="id"
                            scroll={{x: 1300}}
                        />
                    </Space>
                )}
            </Drawer>
        </>
    )
}

function toFilters(values: RunAuditQueryForm): RunAuditFilters {
    const {timeRange, ...rest} = values
    return {
        ...rest,
        startAt: timeRange?.[0]?.toISOString(),
        endAt: timeRange?.[1]?.toISOString(),
    }
}

function eventColumns(): ColumnsType<AgentRunEventAuditResponse> {
    return [
        {title: '#', dataIndex: 'seqNo', width: 70},
        {title: '事件', dataIndex: 'eventType', width: 170, render: eventTag},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentRunEventStatus) => <Tag color={eventStatusColor(value)}>{value}</Tag>
        },
        {title: '轮次', dataIndex: 'reactRound', width: 80, render: valueOrDash},
        {title: '工具', dataIndex: 'toolName', width: 180, render: copyableOrDash},
        {title: '类型', dataIndex: 'toolType', width: 90, render: valueOrDash},
        {title: 'MCP 服务', dataIndex: 'mcpServerCode', width: 130, render: valueOrDash},
        {title: '模型', dataIndex: 'modelName', width: 160, render: valueOrDash},
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`
        },
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: errorOrDash},
        {title: '开始时间', dataIndex: 'startedAt', width: 190, render: renderDateTime},
    ]
}

function EventPayload({event}: { event: AgentRunEventAuditResponse }) {
    const items = [
        payloadItem('Prompt', event.promptText),
        payloadItem('Messages', event.requestMessagesJson),
        payloadItem('Options', event.requestOptionsJson),
        payloadItem('Available Tools', event.availableToolsJson),
        payloadItem('Model Response', event.responseText),
        payloadItem('Tool Arguments', event.toolArguments),
        payloadItem('Tool Result', event.toolResult),
        payloadItem('Metadata', event.metadataJson),
        payloadItem(event.errorCode ?? 'Error', event.errorMessage, true),
    ].filter(isCollapseItem)

    return <Collapse bordered={false} items={items}/>
}

function PayloadBlock({title, value, danger = false}: { title: string; value?: string; danger?: boolean }) {
    return (
        <Space direction="vertical" size={4} style={{width: '100%'}}>
            <Typography.Text strong type={danger ? 'danger' : undefined}>{title}</Typography.Text>
            <PayloadText value={value}/>
        </Space>
    )
}

function PayloadText({value}: { value?: string }) {
    return (
        <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
            {formatPayload(value)}
        </Typography.Paragraph>
    )
}

function payloadItem(label: string, value?: string, danger = false): CollapseItem | null {
    if (!value) {
        return null
    }
    return {
        key: label,
        label: <Typography.Text type={danger ? 'danger' : undefined}>{label}</Typography.Text>,
        children: <PayloadText value={value}/>,
    }
}

function isCollapseItem(item: CollapseItem | null): item is CollapseItem {
    return item !== null
}

function hasEventPayload(event: AgentRunEventAuditResponse) {
    return Boolean(event.promptText || event.requestMessagesJson || event.requestOptionsJson || event.availableToolsJson
        || event.responseText || event.toolArguments || event.toolResult || event.metadataJson || event.errorMessage)
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

function countOrZero(value?: number | null) {
    return value ?? 0
}

function copyableOrDash(value?: string | number | null) {
    if (value === undefined || value === null || value === '') {
        return '-'
    }
    const text = String(value)
    return <Typography.Text copyable ellipsis={{tooltip: text}}>{text}</Typography.Text>
}

function errorOrDash(value?: string | null) {
    if (!value) {
        return '-'
    }
    return <Typography.Text ellipsis={{tooltip: value}} type="danger">{value}</Typography.Text>
}

function runStatusColor(status: AgentRunAuditStatus) {
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

function eventStatusColor(status: AgentRunEventStatus) {
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

function eventTag(type: AgentRunEventType) {
    if (type.startsWith('MODEL')) {
        return <Tag color="blue">{type}</Tag>
    }
    if (type.startsWith('TOOL')) {
        return <Tag color="purple">{type}</Tag>
    }
    if (type.startsWith('RUN')) {
        return <Tag color="geekblue">{type}</Tag>
    }
    return <Tag>{type}</Tag>
}

function formatPayload(value?: string) {
    if (!value) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
        return value
    }
}

export const Component = AgentRunAuditPage
