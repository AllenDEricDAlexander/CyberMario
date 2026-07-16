import {Alert, App, Button, Card, Empty, Popconfirm, Space, Table, Tabs, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {
    listInvestmentDataQualityIssues,
    listInvestmentPlatformJobs,
    listInvestmentPlatformSubscriptions,
    resolveInvestmentDataQualityIssue,
    retryInvestmentPlatformJob,
} from '../services/investmentPlatformService'
import type {
    InvestmentDataQualityIssueResponse,
    InvestmentPlatformJobResponse,
    InvestmentPlatformPage,
    InvestmentPlatformSubscriptionResponse,
} from '../types/investmentPlatformTypes'

const PAGE_SIZE = 20
const STALE_RUNNING_MILLIS = 5 * 60 * 1000

export default function InvestmentPlatformPage() {
    const auth = useAuth()
    const {message} = App.useApp()
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
    const [retryingJobId, setRetryingJobId] = useState<number>()
    const [resolvingIssueId, setResolvingIssueId] = useState<number>()
    const retryingJobRef = useRef<number | null>(null)
    const resolvingIssueRef = useRef<number | null>(null)
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
        {title: '任务 ID', dataIndex: 'id'},
        {title: '类型', dataIndex: 'jobType'},
        {
            title: '状态',
            key: 'status',
            render: (_, job) => (
                <Space>
                    <Tag>{job.status}</Tag>
                    {isStaleRunningJob(job) && <Tag color="warning">LEASE_STALE</Tag>}
                </Space>
            ),
        },
        {title: '尝试', key: 'attempts', render: (_, job) => `${job.attempts}/${job.maxAttempts}`},
        {
            title: '最近错误',
            key: 'error',
            render: (_, job) => job.lastErrorCode
                ? `${job.lastErrorCode}${job.lastErrorMessage ? `：${job.lastErrorMessage}` : ''}`
                : '-',
        },
        {
            title: '操作',
            key: 'actions',
            render: (_, job) => (
                <Popconfirm
                    description="仅失败的平台任务可以重试。"
                    disabled={!canRetry || job.status !== 'FAILED'}
                    onConfirm={() => retryJob(job.id)}
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
                    onConfirm={() => resolveIssue(issue.id)}
                    title="确认将此问题标记为已解决？"
                >
                    <Button
                        disabled={!canResolve || issue.resolutionStatus !== 'OPEN'
                            || resolvingIssueId !== undefined}
                        loading={resolvingIssueId === issue.id}
                        size="small"
                    >
                        标记解决
                    </Button>
                </Popconfirm>
            ),
        },
    ]

    return (
        <Card title="Investment 平台数据监控">
            <Typography.Paragraph type="secondary">
                订阅范围由服务端 Java 代码声明；页面不提供新增、修改或删除订阅的入口。
            </Typography.Paragraph>
            <Tabs items={[
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
            ]}/>
        </Card>
    )
}

export function isStaleRunningJob(job: InvestmentPlatformJobResponse, now = Date.now()) {
    return job.status === 'RUNNING'
        && Number.isFinite(Date.parse(job.updatedAt))
        && now - Date.parse(job.updatedAt) > STALE_RUNNING_MILLIS
}

function tags(values: string[]) {
    return <Space size={[0, 4]} wrap>{values.map((value) => <Tag key={value}>{value}</Tag>)}</Space>
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
