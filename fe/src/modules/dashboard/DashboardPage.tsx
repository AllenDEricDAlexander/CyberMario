import {
    ClockCircleOutlined,
    DashboardOutlined,
    ExportOutlined,
    ImportOutlined,
    ReloadOutlined,
    RiseOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons'
import {Column, Line, Pie} from '@ant-design/charts'
import {Button, Card, Col, DatePicker, Form, Row, Segmented, Select, Tag} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {DataTable} from '../../components/DataTable'
import {DateTimeText} from '../../components/DateTimeText'
import {EmptyState} from '../../components/EmptyState'
import {FilterBar} from '../../components/FilterBar'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
import {StatCard, StatGrid} from '../../components/StatCard'
import {palette} from '../../theme/designTokens'
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

/** Everything the filter bar collects; the scope toggle lives in the page header. */
type DashboardFilters = {
    range?: RangeValue
    userId?: number
    provider?: string
    model?: string
    scenario?: string
    status?: string
}

function DashboardPage() {
    const auth = useAuth()
    const canViewGlobal = hasAdminPermissionBypass(auth)
        || auth.hasPermission('api:agent:model-audit:dashboard:global')
    const [filterForm] = Form.useForm<DashboardFilters>()
    const [scope, setScope] = useState<ModelAuditDashboardScope>(canViewGlobal ? 'GLOBAL' : 'SELF')
    const [filters, setFilters] = useState<DashboardFilters>({})
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

    function buildQuery(active: DashboardFilters): ModelAuditDashboardQuery {
        return {
            scope: effectiveScope,
            startAt: active.range?.[0]?.toISOString(),
            endAt: active.range?.[1]?.toISOString(),
            userId: effectiveScope === 'GLOBAL' ? active.userId : undefined,
            provider: active.provider as ModelAuditDashboardQuery['provider'],
            model: active.model,
            scenario: active.scenario as ModelAuditDashboardQuery['scenario'],
            status: active.status as ModelAuditDashboardQuery['status'],
        }
    }

    async function loadSummary(active: DashboardFilters) {
        setSummaryLoading(true)
        try {
            setSummary(await getModelAuditDashboardSummary(buildQuery(active)))
        } catch (error) {
            reportGlobalError(error)
        } finally {
            setSummaryLoading(false)
        }
    }

    async function loadRecent(nextPage = recentPage, nextSize = recentSize, active = filters) {
        setRecentLoading(true)
        try {
            const page = await getModelAuditRecentCalls(buildQuery(active), nextPage, nextSize)
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

    async function loadDashboard(active = filters) {
        await Promise.all([
            loadSummary(active),
            loadRecent(1, recentSize, active),
        ])
    }

    function applyFilters(values: DashboardFilters) {
        setFilters(values)
        void loadDashboard(values)
    }

    function resetFilters() {
        setFilters({})
        void loadDashboard({})
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
            filterForm.setFieldValue('userId', undefined)
            setFilters((current) => ({...current, userId: undefined}))
        }
        void loadDashboard()
    }, [effectiveScope])

    const recentColumns: ColumnsType<ModelAuditRecentCall> = [
        {title: '时间', dataIndex: 'createdAt', width: 180, render: renderDateTime},
        {title: '用户', width: 170, render: (_, record) => userCell(record)},
        {
            title: '模型',
            dataIndex: 'model',
            width: 200,
            render: (_, record) => <StackedCell primary={record.model} secondary={record.provider}/>,
        },
        {title: '场景', dataIndex: 'scenario', width: 130, render: (value) => <Tag>{value}</Tag>},
        {title: '状态', dataIndex: 'status', width: 110, render: (value: ModelAuditRecentCall['status']) => statusTag(value)},
        {title: '输入', dataIndex: 'promptTokens', width: 90, render: numberText},
        {title: '输出', dataIndex: 'completionTokens', width: 90, render: numberText},
        {title: '总 Token', dataIndex: 'totalTokens', width: 110, render: numberText},
        {title: '耗时', dataIndex: 'durationMs', width: 100, render: (value: ModelAuditRecentCall['durationMs']) => `${numberText(value)}ms`},
        {title: 'Trace', dataIndex: 'traceId', width: 180, render: (value: ModelAuditRecentCall['traceId']) => value || '-'},
    ]

    const chartTheme = useMemo(() => ({
        color: [palette.accent, palette.sky, palette.coral, palette.amber],
    }), [])

    return (
        <>
            <PageToolbar
                actions={
                    <>
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
                        <Button icon={<ReloadOutlined/>} loading={summaryLoading || recentLoading}
                                onClick={() => void loadDashboard()} type="primary">
                            刷新
                        </Button>
                    </>
                }
                description="查看模型调用、Token 消耗、成功率、耗时和用户维度排行。"
                icon={<DashboardOutlined/>}
                title="首页控制台"
            />
            <FilterBar<DashboardFilters>
                form={filterForm}
                loading={summaryLoading || recentLoading}
                onReset={resetFilters}
                onSearch={applyFilters}
            >
                <Form.Item label="时间范围" name="range">
                    <RangePicker showTime/>
                </Form.Item>
                {canViewGlobal && effectiveScope === 'GLOBAL' && (
                    <Form.Item label="用户" name="userId">
                        <Select
                            allowClear
                            loading={userLoading}
                            options={userOptions.map((user) => ({label: userOptionLabel(user), value: user.id}))}
                            placeholder="搜索账号或昵称"
                            showSearch={{filterOption: false, onSearch: (value) => void searchUsers(value)}}
                        />
                    </Form.Item>
                )}
                <Form.Item label="Provider" name="provider">
                    <Select allowClear options={[{label: 'DASHSCOPE', value: 'DASHSCOPE'}]} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="模型" name="model">
                    <Select allowClear options={modelOptions(summary)} placeholder="全部" showSearch/>
                </Form.Item>
                <Form.Item label="场景" name="scenario">
                    <Select
                        allowClear
                        options={['UNKNOWN', 'AGENT_CHAT', 'RAG_CHAT', 'RAG_SUMMARY', 'BACKGROUND_TASK']
                            .map((value) => ({label: value, value}))}
                        placeholder="全部"
                    />
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select
                        allowClear
                        options={['SUCCESS', 'FAILED', 'CANCELLED'].map((value) => ({label: value, value}))}
                        placeholder="全部"
                    />
                </Form.Item>
            </FilterBar>

            <StatGrid columns={6}>
                <StatCard
                    icon={<DashboardOutlined/>}
                    label="调用次数"
                    loading={summaryLoading}
                    tooltip="所选条件下的模型调用总次数。"
                    value={numberText(summary?.overview.callCount ?? 0)}
                />
                <StatCard
                    icon={<ThunderboltOutlined/>}
                    label="总 Token"
                    loading={summaryLoading}
                    tone="sky"
                    tooltip="输入与输出 Token 之和。"
                    value={numberText(summary?.overview.totalTokens ?? 0)}
                />
                <StatCard
                    icon={<ImportOutlined/>}
                    label="输入 Token"
                    loading={summaryLoading}
                    tone="violet"
                    value={numberText(summary?.overview.promptTokens ?? 0)}
                />
                <StatCard
                    icon={<ExportOutlined/>}
                    label="输出 Token"
                    loading={summaryLoading}
                    tone="violet"
                    value={numberText(summary?.overview.completionTokens ?? 0)}
                />
                <StatCard
                    hint={`成功 ${numberText(summary?.overview.successCount ?? 0)} · 失败 ${numberText(summary?.overview.failedCount ?? 0)}`}
                    icon={<RiseOutlined/>}
                    label="成功率"
                    loading={summaryLoading}
                    suffix="%"
                    value={((summary?.overview.successRate ?? 0) * 100).toFixed(1)}
                />
                <StatCard
                    icon={<ClockCircleOutlined/>}
                    label="平均耗时"
                    loading={summaryLoading}
                    suffix="ms"
                    tone="amber"
                    tooltip="单次调用从发起到结束的平均耗时。"
                    value={Math.round(summary?.overview.avgDurationMs ?? 0)}
                />
            </StatGrid>

            <Row gutter={[16, 16]}>
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

            <DataTable<ModelAuditRecentCall>
                columns={recentColumns}
                count={recentTotal}
                dataSource={recentCalls}
                emptyDescription="当前筛选条件下还没有调用记录，试试放宽时间范围或清空筛选。"
                emptyTitle="暂无调用记录"
                loading={recentLoading}
                pagination={{
                    current: recentPage,
                    pageSize: recentSize,
                    total: recentTotal,
                    onChange: (page, size) => void loadRecent(page, size),
                }}
                rowKey="id"
                scroll={{x: 1360}}
                title="最近调用"
            />
        </>
    )
}

function ChartCard(props: { title: string; empty: boolean; children: ReactNode }) {
    return (
        <Card title={props.title}>
            {props.empty
                ? <EmptyState description="调整时间范围或筛选条件后再看看。" inline title="暂无数据"/>
                : props.children}
        </Card>
    )
}

function modelOptions(data?: ModelAuditDashboardSummaryResponse) {
    return (data?.modelStats ?? []).map((item) => ({label: item.name, value: item.name}))
}

function userOptionLabel(user: ModelAuditUserOption) {
    return `#${user.id} ${user.nickname || user.username} (${user.username})`
}

function userCell(record: ModelAuditRecentCall) {
    if (!record.userId) {
        return '-'
    }
    return (
        <StackedCell
            primary={record.nickname || record.username || '未知用户'}
            secondary={`#${record.userId}`}
        />
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
