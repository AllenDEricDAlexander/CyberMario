import {DashboardOutlined, ReloadOutlined, ThunderboltOutlined, UserOutlined,} from '@ant-design/icons'
import {Column, Line, Pie} from '@ant-design/charts'
import {Button, Card, Col, DatePicker, Empty, Row, Segmented, Select, Space, Statistic, Table, Tag} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {hasAdminPermissionBypass, useAuth} from '../auth/authStore'
import {getModelAuditDashboardSummary, getModelAuditRecentCalls, getModelAuditUserOptions} from './dashboardService'
import type {
    ModelAuditDashboardQuery,
    ModelAuditDashboardScope,
    ModelAuditDashboardSummaryResponse,
    ModelAuditRecentCall,
    ModelAuditUserOption,
} from './dashboardTypes'

const {RangePicker} = DatePicker

type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]

function DashboardPage() {
    const auth = useAuth()
    const canViewGlobal = hasAdminPermissionBypass(auth)
        || auth.hasPermission('api:agent:model-audit:dashboard:global')
    const [scope, setScope] = useState<ModelAuditDashboardScope>(canViewGlobal ? 'GLOBAL' : 'SELF')
    const [range, setRange] = useState<RangeValue>(null)
    const [provider, setProvider] = useState<string>()
    const [model, setModel] = useState<string>()
    const [scenario, setScenario] = useState<string>()
    const [status, setStatus] = useState<string>()
    const [userId, setUserId] = useState<number>()
    const [userOptions, setUserOptions] = useState<ModelAuditUserOption[]>([])
    const [summaryLoading, setSummaryLoading] = useState(false)
    const [recentLoading, setRecentLoading] = useState(false)
    const [userLoading, setUserLoading] = useState(false)
    const [summary, setSummary] = useState<ModelAuditDashboardSummaryResponse>()
    const [recentCalls, setRecentCalls] = useState<ModelAuditRecentCall[]>([])
    const [recentPage, setRecentPage] = useState(1)
    const [recentSize, setRecentSize] = useState(20)
    const [recentTotal, setRecentTotal] = useState(0)

    const effectiveScope = canViewGlobal ? scope : 'SELF'

    function buildQuery(): ModelAuditDashboardQuery {
        return {
            scope: effectiveScope,
            startAt: range?.[0]?.toISOString(),
            endAt: range?.[1]?.toISOString(),
            userId: effectiveScope === 'GLOBAL' ? userId : undefined,
            provider: provider as ModelAuditDashboardQuery['provider'],
            model,
            scenario: scenario as ModelAuditDashboardQuery['scenario'],
            status: status as ModelAuditDashboardQuery['status'],
        }
    }

    async function loadSummary() {
        setSummaryLoading(true)
        try {
            setSummary(await getModelAuditDashboardSummary(buildQuery()))
        } catch (error) {
            reportGlobalError(error)
        } finally {
            setSummaryLoading(false)
        }
    }

    async function loadRecent(nextPage = recentPage, nextSize = recentSize) {
        setRecentLoading(true)
        try {
            const page = await getModelAuditRecentCalls(buildQuery(), nextPage, nextSize)
            setRecentCalls(page.records)
            setRecentPage(page.page)
            setRecentSize(page.size)
            setRecentTotal(page.total)
        } catch (error) {
            reportGlobalError(error)
        } finally {
            setRecentLoading(false)
        }
    }

    async function loadDashboard() {
        await Promise.all([
            loadSummary(),
            loadRecent(1, recentSize),
        ])
    }

    async function searchUsers(keyword: string) {
        if (!canViewGlobal) return
        setUserLoading(true)
        try {
            setUserOptions(await getModelAuditUserOptions(keyword, 20))
        } finally {
            setUserLoading(false)
        }
    }

    useEffect(() => {
        if (effectiveScope !== 'GLOBAL') {
            setUserId(undefined)
        }
        void loadDashboard()
    }, [effectiveScope])

    const recentColumns: ColumnsType<ModelAuditRecentCall> = [
        {title: '时间', dataIndex: 'createdAt', width: 180, render: renderDateTime},
        {title: '用户', width: 160, render: (_, record) => userLabel(record)},
        {title: '模型', dataIndex: 'model', width: 190},
        {title: '场景', dataIndex: 'scenario', width: 130, render: (value) => <Tag>{value}</Tag>},
        {title: '状态', dataIndex: 'status', width: 110, render: (value: ModelAuditRecentCall['status']) => statusTag(value)},
        {title: '输入', dataIndex: 'promptTokens', width: 90, render: numberText},
        {title: '输出', dataIndex: 'completionTokens', width: 90, render: numberText},
        {title: '总 Token', dataIndex: 'totalTokens', width: 110, render: numberText},
        {title: '耗时', dataIndex: 'durationMs', width: 100, render: (value: ModelAuditRecentCall['durationMs']) => `${numberText(value)}ms`},
        {title: 'Trace', dataIndex: 'traceId', width: 180, render: (value: ModelAuditRecentCall['traceId']) => value || '-'},
    ]

    const chartTheme = useMemo(() => ({
        color: ['#0f766e', '#2574d8', '#e76f61', '#d9822b'],
    }), [])

    return (
        <>
            <PageToolbar
                actions={
                    <Button icon={<ReloadOutlined/>} loading={summaryLoading || recentLoading}
                            onClick={() => void loadDashboard()} type="primary">
                        刷新
                    </Button>
                }
                description="查看模型调用、Token 消耗、成功率、耗时和用户维度排行。"
                title="首页控制台"
            />
            <Card className="dashboard-filter-card">
                <Space wrap>
                    {canViewGlobal && (
                        <Segmented
                            onChange={(value) => setScope(value as ModelAuditDashboardScope)}
                            options={[
                                {label: '全局用量', value: 'GLOBAL'},
                                {label: '我的用量', value: 'SELF'},
                            ]}
                            value={effectiveScope}
                        />
                    )}
                    <RangePicker
                        onChange={(value) => setRange(value)}
                        showTime
                        value={range}
                    />
                    {canViewGlobal && effectiveScope === 'GLOBAL' && (
                        <Select
                            allowClear
                            loading={userLoading}
                            onChange={setUserId}
                            options={userOptions.map((user) => ({label: userOptionLabel(user), value: user.id}))}
                            placeholder="选择用户"
                            showSearch={{filterOption: false, onSearch: (value) => void searchUsers(value)}}
                            style={{width: 240}}
                            value={userId}
                        />
                    )}
                    <Select
                        allowClear
                        onChange={setProvider}
                        options={[{label: 'DASHSCOPE', value: 'DASHSCOPE'}]}
                        placeholder="Provider"
                        style={{width: 150}}
                        value={provider}
                    />
                    <Select
                        allowClear
                        onChange={setModel}
                        options={modelOptions(summary)}
                        placeholder="模型"
                        showSearch
                        style={{width: 220}}
                        value={model}
                    />
                    <Select
                        allowClear
                        onChange={setScenario}
                        options={['UNKNOWN', 'AGENT_CHAT', 'RAG_CHAT', 'RAG_SUMMARY', 'BACKGROUND_TASK']
                            .map((value) => ({label: value, value}))}
                        placeholder="场景"
                        style={{width: 170}}
                        value={scenario}
                    />
                    <Select
                        allowClear
                        onChange={setStatus}
                        options={['SUCCESS', 'FAILED', 'CANCELLED'].map((value) => ({label: value, value}))}
                        placeholder="状态"
                        style={{width: 140}}
                        value={status}
                    />
                    <Button onClick={() => void loadDashboard()}>查询</Button>
                </Space>
            </Card>

            <Row gutter={[16, 16]} style={{marginTop: 16}}>
                <MetricCard icon={<DashboardOutlined/>} loading={summaryLoading} title="调用次数"
                            value={summary?.overview.callCount ?? 0}/>
                <MetricCard icon={<ThunderboltOutlined/>} loading={summaryLoading} title="总 Token"
                            value={summary?.overview.totalTokens ?? 0}/>
                <MetricCard loading={summaryLoading} title="输入 Token" value={summary?.overview.promptTokens ?? 0}/>
                <MetricCard loading={summaryLoading} title="输出 Token"
                            value={summary?.overview.completionTokens ?? 0}/>
                <MetricCard loading={summaryLoading} suffix="%" title="成功率"
                            value={((summary?.overview.successRate ?? 0) * 100).toFixed(1)}/>
                <MetricCard loading={summaryLoading} suffix="ms" title="平均耗时"
                            value={Math.round(summary?.overview.avgDurationMs ?? 0)}/>
            </Row>

            <Row gutter={[16, 16]} style={{marginTop: 16}}>
                <Col lg={14} xs={24}>
                    <ChartCard empty={!summary?.tokenTrend.length} title="Token 趋势">
                        <Line
                            data={summary?.tokenTrend ?? []}
                            height={300}
                            theme={chartTheme}
                            xField="date"
                            yField="value"
                            colorField="metric"
                            smooth
                        />
                    </ChartCard>
                </Col>
                <Col lg={10} xs={24}>
                    <ChartCard empty={!summary?.callTrend.length} title="调用量趋势">
                        <Column
                            data={summary?.callTrend ?? []}
                            height={300}
                            theme={chartTheme}
                            xField="date"
                            yField="value"
                        />
                    </ChartCard>
                </Col>
                <Col lg={12} xs={24}>
                    <ChartCard empty={!summary?.modelStats.length} title="模型 Token 排行">
                        <Column
                            data={summary?.modelStats ?? []}
                            height={320}
                            theme={chartTheme}
                            xField="name"
                            yField="totalTokens"
                        />
                    </ChartCard>
                </Col>
                <Col lg={6} xs={24}>
                    <ChartCard empty={!summary?.scenarioStats.length} title="场景分布">
                        <Pie
                            angleField="callCount"
                            colorField="name"
                            data={summary?.scenarioStats ?? []}
                            height={320}
                            theme={chartTheme}
                        />
                    </ChartCard>
                </Col>
                <Col lg={6} xs={24}>
                    <ChartCard empty={!summary?.statusStats.length} title="状态分布">
                        <Pie
                            angleField="callCount"
                            colorField="name"
                            data={summary?.statusStats ?? []}
                            height={320}
                            theme={chartTheme}
                        />
                    </ChartCard>
                </Col>
                {canViewGlobal && effectiveScope === 'GLOBAL' && (
                    <Col xs={24}>
                        <ChartCard empty={!summary?.userStats.length} title="用户 Token 排行">
                            <Column
                                data={(summary?.userStats ?? []).map((item) => ({...item, name: userStatLabel(item)}))}
                                height={320}
                                theme={chartTheme}
                                xField="name"
                                yField="totalTokens"
                            />
                        </ChartCard>
                    </Col>
                )}
            </Row>

            <Card style={{marginTop: 16}} title="最近调用">
                <Table<ModelAuditRecentCall>
                    columns={recentColumns}
                    dataSource={recentCalls}
                    loading={recentLoading}
                    pagination={{
                        current: recentPage,
                        pageSize: recentSize,
                        total: recentTotal,
                        showSizeChanger: true,
                        onChange: (page, size) => void loadRecent(page, size),
                    }}
                    rowKey="id"
                    scroll={{x: 1350}}
                />
            </Card>
        </>
    )
}

function MetricCard(props: {
    title: string;
    value: number | string;
    suffix?: string;
    icon?: ReactNode;
    loading: boolean
}) {
    return (
        <Col lg={4} md={8} xs={12}>
            <Card loading={props.loading}>
                <Statistic prefix={props.icon} suffix={props.suffix} title={props.title} value={props.value}/>
            </Card>
        </Col>
    )
}

function ChartCard(props: { title: string; empty: boolean; children: ReactNode }) {
    return (
        <Card title={props.title}>
            {props.empty ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}/> : props.children}
        </Card>
    )
}

function modelOptions(data?: ModelAuditDashboardSummaryResponse) {
    return (data?.modelStats ?? []).map((item) => ({label: item.name, value: item.name}))
}

function userOptionLabel(user: ModelAuditUserOption) {
    return `#${user.id} ${user.nickname || user.username} (${user.username})`
}

function userLabel(record: ModelAuditRecentCall) {
    if (!record.userId) {
        return '-'
    }
    return (
        <Space size={4}>
            <UserOutlined/>
            <span>#{record.userId} {record.nickname || record.username || '-'}</span>
        </Space>
    )
}

function userStatLabel(record: { userId?: number | null; username?: string; nickname?: string }) {
    if (!record.userId) {
        return '系统/未知用户'
    }
    return `#${record.userId} ${record.nickname || record.username || ''}`
}

function statusTag(value: string) {
    const color = value === 'SUCCESS' ? 'success' : value === 'FAILED' ? 'error' : 'default'
    return <Tag color={color}>{value}</Tag>
}

function numberText(value?: number | null) {
    return value == null ? '-' : value.toLocaleString()
}

function renderDateTime(value?: string | number | null) {
    return <DateTimeText value={value}/>
}

export const Component = DashboardPage
