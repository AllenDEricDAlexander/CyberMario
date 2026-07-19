import {
    Alert,
    App,
    Button,
    Card,
    DatePicker,
    Empty,
    Form,
    Modal,
    Popconfirm,
    Select,
    Space,
    Table,
    Tabs,
    Tag,
    Typography,
} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {
    createInvestmentMarketDataPull,
    listInvestmentDataQualityIssues,
    listInvestmentPlatformJobs,
    listInvestmentPlatformSubscriptions,
    resolveInvestmentDataQualityIssue,
    retryInvestmentPlatformJob,
} from '../services/investmentPlatformService'
import type {
    InvestmentDataQualityIssueResponse,
    InvestmentMarketDataPullCapability,
    InvestmentMarketDataPullInterval,
    InvestmentMarketDataPullRequest,
    InvestmentMarketDataPullSymbol,
    InvestmentPlatformJobResponse,
    InvestmentPlatformPage,
    InvestmentPlatformSubscriptionResponse,
} from '../types/investmentPlatformTypes'

const PAGE_SIZE = 20
const STALE_RUNNING_MILLIS = 5 * 60 * 1000
const PULL_SYMBOLS: InvestmentMarketDataPullSymbol[] = ['BTCUSDT', 'SOLUSDT']
const PULL_CAPABILITIES: InvestmentMarketDataPullCapability[] = ['MARKET_CANDLE', 'FUNDING_RATE']
const PULL_INTERVALS: InvestmentMarketDataPullInterval[] = ['M1', 'D1']
const {RangePicker} = DatePicker
type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]
type MarketDataPullFormValues = {
    symbol?: InvestmentMarketDataPullSymbol
    capability?: InvestmentMarketDataPullCapability
    interval?: InvestmentMarketDataPullInterval | null
    range?: RangeValue
}

export default function InvestmentPlatformPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [pullForm] = Form.useForm<MarketDataPullFormValues>()
    const canPull = canUseRbacButton(auth, investmentButtonCodes.platformPullMarketData)
    const canRetry = canUseRbacButton(auth, investmentButtonCodes.platformRetryJob)
    const canResolve = canUseRbacButton(auth, investmentButtonCodes.platformResolveQuality)
    const [subscriptions, setSubscriptions] = useState<InvestmentPlatformSubscriptionResponse[]>([])
    const [subscriptionsError, setSubscriptionsError] = useState<string>()
    const [subscriptionsLoading, setSubscriptionsLoading] = useState(true)
    const [jobs, setJobs] = useState<InvestmentPlatformPage<InvestmentPlatformJobResponse>>()
    const [jobsError, setJobsError] = useState<string>()
    const [jobsLoading, setJobsLoading] = useState(true)
    const [jobPage, setJobPage] = useState(1)
    const [issues, setIssues] = useState<InvestmentPlatformPage<InvestmentDataQualityIssueResponse>>()
    const [issuesError, setIssuesError] = useState<string>()
    const [issuesLoading, setIssuesLoading] = useState(true)
    const [issuePage, setIssuePage] = useState(1)
    const [activeTab, setActiveTab] = useState('subscriptions')
    const [pullOpen, setPullOpen] = useState(false)
    const [pulling, setPulling] = useState(false)
    const [pullError, setPullError] = useState<string>()
    const [retryingJobId, setRetryingJobId] = useState<number>()
    const [resolvingIssueId, setResolvingIssueId] = useState<number>()
    const retryingJobRef = useRef<number | null>(null)
    const resolvingIssueRef = useRef<number | null>(null)
    const pullingRef = useRef(false)
    const pullGenerationRef = useRef(0)
    const subscriptionsGenerationRef = useRef(0)
    const jobsGenerationRef = useRef(0)
    const issuesGenerationRef = useRef(0)

    const loadSubscriptions = useCallback(async () => {
        const generation = ++subscriptionsGenerationRef.current
        setSubscriptionsLoading(true)
        setSubscriptionsError(undefined)
        try {
            const response = await listInvestmentPlatformSubscriptions()
            if (generation === subscriptionsGenerationRef.current) {
                setSubscriptions(response)
            }
        } catch (reason) {
            if (generation === subscriptionsGenerationRef.current) {
                setSubscriptionsError(errorMessage(reason, '代码订阅加载失败'))
            }
        } finally {
            if (generation === subscriptionsGenerationRef.current) {
                setSubscriptionsLoading(false)
            }
        }
    }, [])

    const loadJobs = useCallback(async () => {
        const generation = ++jobsGenerationRef.current
        setJobsLoading(true)
        setJobsError(undefined)
        try {
            const response = await listInvestmentPlatformJobs({page: jobPage, size: PAGE_SIZE})
            if (generation === jobsGenerationRef.current) {
                setJobs(response)
            }
        } catch (reason) {
            if (generation === jobsGenerationRef.current) {
                setJobsError(errorMessage(reason, '平台任务加载失败'))
            }
        } finally {
            if (generation === jobsGenerationRef.current) {
                setJobsLoading(false)
            }
        }
    }, [jobPage])

    const loadIssues = useCallback(async () => {
        const generation = ++issuesGenerationRef.current
        setIssuesLoading(true)
        setIssuesError(undefined)
        try {
            const response = await listInvestmentDataQualityIssues({page: issuePage, size: PAGE_SIZE})
            if (generation === issuesGenerationRef.current) {
                setIssues(response)
            }
        } catch (reason) {
            if (generation === issuesGenerationRef.current) {
                setIssuesError(errorMessage(reason, '质量问题加载失败'))
            }
        } finally {
            if (generation === issuesGenerationRef.current) {
                setIssuesLoading(false)
            }
        }
    }, [issuePage])

    useEffect(() => {
        void loadSubscriptions()
        return () => {
            subscriptionsGenerationRef.current += 1
        }
    }, [loadSubscriptions])

    useEffect(() => {
        void loadJobs()
        return () => {
            jobsGenerationRef.current += 1
        }
    }, [loadJobs])

    useEffect(() => {
        void loadIssues()
        return () => {
            issuesGenerationRef.current += 1
        }
    }, [loadIssues])

    useEffect(() => () => {
        pullGenerationRef.current += 1
    }, [])

    const supportedSubscriptions = subscriptions.filter((subscription) => (
        isPullSymbol(subscription.symbol)
        && subscription.capabilities.some(isPullCapability)
    ))
    const pullSymbolOptions = PULL_SYMBOLS
        .filter((symbol) => supportedSubscriptions.some((subscription) => subscription.symbol === symbol))
        .map((symbol) => ({label: symbol, value: symbol}))
    const selectedPullSymbol = Form.useWatch('symbol', pullForm)
    const selectedSubscription = supportedSubscriptions.find(
        (subscription) => subscription.symbol === selectedPullSymbol,
    )
    const capabilityOptions = PULL_CAPABILITIES
        .filter((capability) => selectedSubscription?.capabilities.includes(capability))
        .map((capability) => ({label: capabilityLabel(capability), value: capability}))
    const selectedCapability = Form.useWatch('capability', pullForm)
    const intervalOptions = PULL_INTERVALS
        .filter((interval) => selectedSubscription?.intervals.includes(interval))
        .map((interval) => ({label: interval, value: interval}))

    function openPullModal() {
        const subscription = supportedSubscriptions[0]
        if (!subscription) {
            return
        }
        const capability = PULL_CAPABILITIES.find((item) => subscription.capabilities.includes(item))
        const interval = capability === 'MARKET_CANDLE'
            ? PULL_INTERVALS.find((item) => subscription.intervals.includes(item))
            : null
        pullForm.setFieldsValue({
            symbol: subscription.symbol as InvestmentMarketDataPullSymbol,
            capability,
            interval,
            range: null,
        })
        setPullError(undefined)
        setPullOpen(true)
    }

    function closePullModal() {
        if (pullingRef.current) {
            return
        }
        setPullOpen(false)
        setPullError(undefined)
        pullForm.resetFields()
    }

    function changePullSymbol(symbol: InvestmentMarketDataPullSymbol) {
        const subscription = supportedSubscriptions.find((item) => item.symbol === symbol)
        const capability = PULL_CAPABILITIES.find((item) => subscription?.capabilities.includes(item))
        const interval = capability === 'MARKET_CANDLE'
            ? PULL_INTERVALS.find((item) => subscription?.intervals.includes(item))
            : null
        pullForm.setFieldsValue({symbol, capability, interval})
        setPullError(undefined)
    }

    function changePullCapability(capability: InvestmentMarketDataPullCapability) {
        pullForm.setFieldsValue({
            capability,
            interval: capability === 'MARKET_CANDLE' ? intervalOptions[0]?.value : null,
        })
        setPullError(undefined)
    }

    async function submitMarketDataPull() {
        if (pullingRef.current) {
            return
        }
        let values: MarketDataPullFormValues
        try {
            values = await pullForm.validateFields()
        } catch {
            return
        }
        const result = createMarketDataPullRequest(values)
        if (!result.request) {
            setPullError(result.error)
            return
        }
        const generation = ++pullGenerationRef.current
        pullingRef.current = true
        setPulling(true)
        setPullError(undefined)
        try {
            const response = await createInvestmentMarketDataPull(result.request)
            if (generation !== pullGenerationRef.current) {
                return
            }
            setPullOpen(false)
            pullForm.resetFields()
            setActiveTab('jobs')
            void message.success(`拉取任务 #${response.jobId} 已进入待执行队列`)
            if (jobPage === 1) {
                await loadJobs()
            } else {
                setJobPage(1)
            }
        } catch (reason) {
            if (generation === pullGenerationRef.current) {
                setPullError(errorMessage(reason, '手动拉取任务创建失败'))
            }
        } finally {
            if (generation === pullGenerationRef.current) {
                pullingRef.current = false
                setPulling(false)
            }
        }
    }

    async function retryJob(jobId: number) {
        if (retryingJobRef.current !== null) {
            return
        }
        retryingJobRef.current = jobId
        setRetryingJobId(jobId)
        try {
            await retryInvestmentPlatformJob(jobId)
            void message.success('任务已重新进入待执行队列')
            await loadJobs()
        } catch (reason) {
            void message.error(errorMessage(reason, '任务重试失败'))
        } finally {
            retryingJobRef.current = null
            setRetryingJobId(undefined)
        }
    }

    async function resolveIssue(issueId: number) {
        if (resolvingIssueRef.current !== null) {
            return
        }
        resolvingIssueRef.current = issueId
        setResolvingIssueId(issueId)
        try {
            await resolveInvestmentDataQualityIssue(issueId)
            void message.success('质量问题已解决')
            await loadIssues()
        } catch (reason) {
            void message.error(errorMessage(reason, '质量问题处理失败'))
        } finally {
            resolvingIssueRef.current = null
            setResolvingIssueId(undefined)
        }
    }

    const subscriptionColumns: ColumnsType<InvestmentPlatformSubscriptionResponse> = [
        {title: '数据源', dataIndex: 'sourceCode'},
        {title: '产品', dataIndex: 'productType'},
        {title: '合约', dataIndex: 'symbol'},
        {title: '状态', dataIndex: 'status', render: (value) => <Tag>{value}</Tag>},
        {title: '能力', dataIndex: 'capabilities', render: tags},
        {title: '价型', dataIndex: 'priceTypes', render: tags},
        {title: '周期', dataIndex: 'intervals', render: tags},
    ]
    const jobColumns: ColumnsType<InvestmentPlatformJobResponse> = [
        {title: '任务 ID', dataIndex: 'id', width: 90, fixed: 'left'},
        {
            title: '触发方式',
            dataIndex: 'triggerSource',
            width: 100,
            render: (value) => <Tag color={value === 'MANUAL' ? 'blue' : 'default'}>{value}</Tag>,
        },
        {title: '数据源', dataIndex: 'sourceCode', width: 100, render: nullableText},
        {title: '合约', dataIndex: 'symbol', width: 110, render: nullableText},
        {
            title: '数据类型',
            key: 'dataType',
            width: 150,
            render: (_, job) => (
                <Space orientation="vertical" size={0}>
                    <span>{job.capability ?? '-'}</span>
                    <Typography.Text type="secondary">
                        {[job.priceType, job.interval].filter(Boolean).join(' / ') || '-'}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: '拉取区间',
            key: 'pullRange',
            width: 210,
            render: (_, job) => timeRange(job.startInclusive, job.endExclusive),
        },
        {title: '类型', dataIndex: 'jobType', width: 190, ellipsis: true},
        {
            title: '状态',
            key: 'status',
            width: 150,
            render: (_, job) => (
                <Space>
                    <Tag>{job.status}</Tag>
                    {isStaleRunningJob(job) && <Tag color="warning">LEASE_STALE</Tag>}
                </Space>
            ),
        },
        {
            title: '拉取 / 写入',
            key: 'counts',
            width: 110,
            render: (_, job) => `${job.fetchedCount ?? '-'} / ${job.writtenCount ?? '-'}`,
        },
        {title: '尝试', key: 'attempts', width: 80, render: (_, job) => `${job.attempts}/${job.maxAttempts}`},
        {
            title: '执行起止',
            key: 'executionRange',
            width: 210,
            render: (_, job) => timeRange(job.startedAt, job.finishedAt),
        },
        {
            title: '最近错误',
            key: 'error',
            width: 260,
            ellipsis: true,
            render: (_, job) => job.lastErrorCode
                ? `${job.lastErrorCode}${job.lastErrorMessage ? `：${job.lastErrorMessage}` : ''}`
                : '-',
        },
        {
            title: '操作',
            key: 'actions',
            width: 90,
            fixed: 'right',
            render: (_, job) => (
                <Popconfirm
                    description="仅失败的平台任务可以重试。"
                    disabled={!canRetry || job.status !== 'FAILED'}
                    onConfirm={() => {
                        void retryJob(job.id)
                    }}
                    title="确认重试此任务？"
                >
                    <Button
                        disabled={!canRetry || job.status !== 'FAILED' || retryingJobId !== undefined}
                        loading={retryingJobId === job.id}
                        size="small"
                    >
                        重试
                    </Button>
                </Popconfirm>
            ),
        },
    ]
    const issueColumns: ColumnsType<InvestmentDataQualityIssueResponse> = [
        {title: '问题 ID', dataIndex: 'id'},
        {title: '标的 ID', dataIndex: 'instrumentId'},
        {title: '数据类型', dataIndex: 'dataType'},
        {title: '问题编码', dataIndex: 'issueCode'},
        {title: '级别', dataIndex: 'severity', render: (value) => <Tag>{value}</Tag>},
        {title: '状态', dataIndex: 'resolutionStatus'},
        {
            title: '操作',
            key: 'actions',
            render: (_, issue) => (
                <Popconfirm
                    description="解决操作会记录当前管理员。"
                    disabled={!canResolve || issue.resolutionStatus !== 'OPEN'}
                    onConfirm={() => {
                        void resolveIssue(issue.id)
                    }}
                    title="确认将此问题标记为已解决？"
                >
                    <Button
                        disabled={!canResolve || issue.resolutionStatus !== 'OPEN'
                            || resolvingIssueId !== undefined}
                        loading={resolvingIssueId === issue.id}
                        size="small"
                    >标记解决</Button>
                </Popconfirm>
            ),
        },
    ]

    return (
        <Card
            extra={canPull && (
                <Button
                    disabled={supportedSubscriptions.length === 0}
                    onClick={openPullModal}
                    type="primary"
                >
                    手动拉取
                </Button>
            )}
            title="Investment 平台数据监控"
        >
            <Typography.Paragraph type="secondary">
                订阅范围由服务端 Java 代码声明；页面不提供新增、修改或删除订阅的入口。
            </Typography.Paragraph>
            <Tabs activeKey={activeTab} items={[
                {
                    key: 'subscriptions',
                    label: '代码订阅',
                    children: subscriptionsError
                        ? <Alert description={subscriptionsError} showIcon type="error"/>
                        : subscriptions.length === 0 && !subscriptionsLoading
                            ? <Empty description="尚未在代码中接入任何行情订阅"/>
                            : <Table
                                columns={subscriptionColumns}
                                dataSource={subscriptions}
                                loading={subscriptionsLoading}
                                pagination={false}
                                rowKey={(item) => `${item.sourceCode}:${item.productType}:${item.symbol}`}
                            />,
                },
                {
                    key: 'jobs',
                    label: '同步任务',
                    children: jobsError
                        ? <Alert description={jobsError} showIcon type="error"/>
                        : <Table
                            columns={jobColumns}
                            dataSource={jobs?.records ?? []}
                            loading={jobsLoading}
                            pagination={{
                                current: jobs?.page ?? jobPage,
                                pageSize: jobs?.size ?? PAGE_SIZE,
                                total: jobs?.total ?? 0,
                                showSizeChanger: false,
                                onChange: setJobPage,
                            }}
                            rowKey="id"
                            scroll={{x: 1_950}}
                        />,
                },
                {
                    key: 'quality',
                    label: '数据质量',
                    children: issuesError
                        ? <Alert description={issuesError} showIcon type="error"/>
                        : <Table
                            columns={issueColumns}
                            dataSource={issues?.records ?? []}
                            loading={issuesLoading}
                            pagination={{
                                current: issues?.page ?? issuePage,
                                pageSize: issues?.size ?? PAGE_SIZE,
                                total: issues?.total ?? 0,
                                showSizeChanger: false,
                                onChange: setIssuePage,
                            }}
                            rowKey="id"
                        />,
                },
            ]} onChange={setActiveTab}/>
            <Modal
                cancelButtonProps={{disabled: pulling}}
                confirmLoading={pulling}
                destroyOnHidden
                okText="创建拉取任务"
                onCancel={closePullModal}
                onOk={() => void submitMarketDataPull()}
                open={pullOpen}
                title="手动拉取 Bitget 行情数据"
            >
                <Form form={pullForm} layout="vertical">
                    <Form.Item
                        label="合约"
                        name="symbol"
                        rules={[{required: true, message: '请选择合约'}]}
                    >
                        <Select
                            onChange={changePullSymbol}
                            options={pullSymbolOptions}
                            placeholder="请选择合约"
                        />
                    </Form.Item>
                    <Form.Item
                        label="数据类型"
                        name="capability"
                        rules={[{required: true, message: '请选择数据类型'}]}
                    >
                        <Select
                            onChange={changePullCapability}
                            options={capabilityOptions}
                            placeholder="请选择数据类型"
                        />
                    </Form.Item>
                    {selectedCapability === 'MARKET_CANDLE' && (
                        <Form.Item
                            label="K 线周期"
                            name="interval"
                            rules={[{required: true, message: '请选择 K 线周期'}]}
                        >
                            <Select options={intervalOptions} placeholder="请选择 K 线周期"/>
                        </Form.Item>
                    )}
                    <Form.Item
                        label="拉取时间范围"
                        name="range"
                        rules={[{required: true, message: '请选择拉取时间范围'}]}
                    >
                        <RangePicker
                            aria-label="拉取时间范围"
                            disabledDate={(current) => current.valueOf() > Date.now()}
                            showTime
                            style={{width: '100%'}}
                        />
                    </Form.Item>
                </Form>
                {pullError && (
                    <Alert description={pullError} showIcon title="任务创建失败" type="error"/>
                )}
            </Modal>
        </Card>
    )
}

export function createMarketDataPullRequest(
    values: MarketDataPullFormValues,
    now = Date.now(),
): {request: InvestmentMarketDataPullRequest | null, error: string} {
    const start = values.range?.[0]
    const end = values.range?.[1]
    if (!values.symbol || !isPullSymbol(values.symbol)
        || !values.capability || !isPullCapability(values.capability)
        || !start || !end) {
        return {request: null, error: '请完整填写拉取条件'}
    }
    const startTime = start.valueOf()
    const endTime = end.valueOf()
    if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || startTime >= endTime) {
        return {request: null, error: '拉取开始时间必须早于结束时间'}
    }
    if (endTime > now) {
        return {request: null, error: '拉取结束时间不能晚于当前时间'}
    }
    const maxEnd = new Date(startTime)
    maxEnd.setUTCFullYear(maxEnd.getUTCFullYear() + 2)
    if (endTime > maxEnd.getTime()) {
        return {request: null, error: '单次拉取时间跨度不能超过 2 年'}
    }
    if (values.capability === 'MARKET_CANDLE'
        && (!values.interval || !isPullInterval(values.interval))) {
        return {request: null, error: '请选择有效的 K 线周期'}
    }
    return {
        request: {
            symbol: values.symbol,
            capability: values.capability,
            interval: values.capability === 'MARKET_CANDLE' ? values.interval! : null,
            startInclusive: start.toISOString(),
            endExclusive: end.toISOString(),
        },
        error: '',
    }
}

export function isStaleRunningJob(job: InvestmentPlatformJobResponse, now = Date.now()) {
    return job.status === 'RUNNING'
        && Number.isFinite(Date.parse(job.updatedAt))
        && now - Date.parse(job.updatedAt) > STALE_RUNNING_MILLIS
}

function tags(values: string[]) {
    return <Space size={[0, 4]} wrap>{values.map((value) => <Tag key={value}>{value}</Tag>)}</Space>
}

function isPullSymbol(value: string): value is InvestmentMarketDataPullSymbol {
    return PULL_SYMBOLS.some((symbol) => symbol === value)
}

function isPullCapability(value: string): value is InvestmentMarketDataPullCapability {
    return PULL_CAPABILITIES.some((capability) => capability === value)
}

function isPullInterval(value: string): value is InvestmentMarketDataPullInterval {
    return PULL_INTERVALS.some((interval) => interval === value)
}

function capabilityLabel(capability: InvestmentMarketDataPullCapability) {
    return capability === 'MARKET_CANDLE' ? 'K 线' : '资金费率'
}

function nullableText(value: string | null) {
    return value ?? '-'
}

function timeRange(start: string | null, end: string | null) {
    return (
        <Space orientation="vertical" size={0}>
            <Typography.Text ellipsis={{tooltip: start ?? '-'}}>{start ?? '-'}</Typography.Text>
            <Typography.Text ellipsis={{tooltip: end ?? '-'}} type="secondary">
                {end ?? '-'}
            </Typography.Text>
        </Space>
    )
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}

export const Component = InvestmentPlatformPage
