import {AuditOutlined, EyeOutlined, ReloadOutlined} from '@ant-design/icons'
import type {CollapseProps} from 'antd'
import {
    Button,
    Collapse,
    DatePicker,
    Descriptions,
    Form,
    Input,
    InputNumber,
    Select,
    Space,
    Tag,
    Typography,
} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
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
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentRunAuditStatus) => <Tag color={runStatusColor(value)}>{value}</Tag>,
        },
        {
            title: '调用次数',
            dataIndex: 'modelCallCount',
            width: 150,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`模型 ${countOrZero(record.modelCallCount)}`}
                    secondary={`工具 ${countOrZero(record.toolCallCount)} · MCP ${countOrZero(record.mcpToolCallCount)}`}
                />
            ),
        },
        {title: '预设', dataIndex: 'presetId', width: 90, render: valueOrDash},
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`,
        },
        {
            title: '请求 / Trace',
            dataIndex: 'requestId',
            width: 230,
            render: (_, record) => (
                <StackedCell plain primary={record.requestId ?? '-'} secondary={record.traceId ?? '无 trace'}/>
            ),
        },
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: errorOrDash},
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
                icon={<AuditOutlined/>}
                title="运行审计"
            />
            <FilterBar<RunAuditQueryForm>
                form={form}
                loading={loading}
                onReset={() => setFilters({})}
                onSearch={(values) => setFilters(toFilters(values))}
            >
                <Form.Item label="时间范围" name="timeRange">
                    <RangePicker showTime/>
                </Form.Item>
                <Form.Item label="用户 ID" name="userId">
                    <InputNumber min={1} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="用户名" name="username">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="线程 ID" name="threadId">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="请求 ID" name="requestId">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="Trace ID" name="traceId">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="预设 ID" name="presetId">
                    <InputNumber min={1} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="工具名" name="toolName">
                    <Input allowClear placeholder="全部"/>
                </Form.Item>
                <Form.Item label="MCP 服务" name="mcpServerCode">
                    <Input allowClear placeholder="全部"/>
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
            <DataTable<AgentRunAuditResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyDescription="放宽时间范围或清空筛选条件后重新查询。"
                emptyTitle="没有匹配的运行记录"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1700}}
                title="运行记录"
            />
            <FormDrawer
                description={selected ? `线程 ${selected.threadId}` : undefined}
                footer={false}
                onClose={() => setDrawerOpen(false)}
                open={drawerOpen}
                size="xl"
                title="运行审计详情"
            >
                {selected && (
                    <PageStack>
                        <Space wrap>
                            <Tag>ID={selected.id}</Tag>
                            <Tag color={runStatusColor(selected.status)}>{selected.status}</Tag>
                            {selected.requestId && <Tag>request={selected.requestId}</Tag>}
                            {selected.traceId && <Tag>trace={selected.traceId}</Tag>}
                        </Space>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="用户">{selected.username ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="用户 ID">{selected.userId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="模型轮次">{countOrZero(selected.modelCallCount)}</Descriptions.Item>
                            <Descriptions.Item label="工具调用">{countOrZero(selected.toolCallCount)}</Descriptions.Item>
                            <Descriptions.Item label="MCP 调用">{countOrZero(selected.mcpToolCallCount)}</Descriptions.Item>
                            <Descriptions.Item label="耗时">{formatDuration(selected.durationMs)}</Descriptions.Item>
                        </Descriptions>
                        <PageSection title="运行配置">
                            <PayloadText value={selected.effectiveConfigJson}/>
                        </PageSection>
                        <PageSection title="用户输入">
                            <PayloadText value={selected.userMessage}/>
                        </PageSection>
                        {(selected.finalThinking || selected.finalMessage || selected.errorMessage) && (
                            <PageSection title="最终结果">
                                {selected.finalThinking && (
                                    <PayloadBlock title="Thinking" value={selected.finalThinking}/>
                                )}
                                {selected.finalMessage && (
                                    <PayloadBlock title="Message" value={selected.finalMessage}/>
                                )}
                                {selected.errorMessage && (
                                    <PayloadBlock danger title={selected.errorCode ?? 'Error'}
                                                  value={selected.errorMessage}/>
                                )}
                            </PageSection>
                        )}
                        {detailError && <ErrorState inline message={detailError} title="运行明细加载失败"/>}
                        <DataTable<AgentRunEventAuditResponse>
                            columns={eventColumns()}
                            count={events.length}
                            dataSource={events}
                            emptyDescription="这次运行没有记录 ReAct 事件，可能在首轮模型调用前就结束了。"
                            emptyTitle="暂无事件"
                            expandable={{
                                expandedRowRender: (record) => <EventPayload event={record}/>,
                                rowExpandable: hasEventPayload,
                            }}
                            loading={detailLoading}
                            pagination={false}
                            rowKey="id"
                            scroll={{x: 1200}}
                            title="ReAct 事件"
                        />
                    </PageStack>
                )}
            </FormDrawer>
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
            render: (value: AgentRunEventStatus) => <Tag color={eventStatusColor(value)}>{value}</Tag>,
        },
        {title: '轮次', dataIndex: 'reactRound', width: 80, render: valueOrDash},
        {
            title: '工具',
            dataIndex: 'toolName',
            width: 200,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.toolName ?? '-'}
                    secondary={[record.toolType, record.mcpServerCode].filter(Boolean).join(' · ')}
                />
            ),
        },
        {title: '模型', dataIndex: 'modelName', width: 160, render: valueOrDash},
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => formatDuration(value),
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
        <Space className="u-full-width" direction="vertical" size={4}>
            <Typography.Text strong type={danger ? 'danger' : undefined}>{title}</Typography.Text>
            <PayloadText value={value}/>
        </Space>
    )
}

function PayloadText({value}: { value?: string }) {
    return (
        <Typography.Paragraph className="payload-text" copyable>
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

function formatDuration(value?: number | null) {
    return value === undefined || value === null ? '-' : `${value}ms`
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
