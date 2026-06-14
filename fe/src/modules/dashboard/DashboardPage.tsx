import {DashboardOutlined, ReloadOutlined, ThunderboltOutlined, UserOutlined,} from '@ant-design/icons'
import {Bar, Column, Line, Pie} from '@ant-design/charts'
import {App, Button, Card, Col, DatePicker, Empty, Row, Segmented, Select, Space, Statistic, Table, Tag} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {hasAdminPermissionBypass, useAuth} from '../auth/authStore'
import {getModelAuditDashboard, getModelAuditUserOptions} from './dashboardService'
import type {
    ModelAuditDashboardQuery,
    ModelAuditDashboardResponse,
    ModelAuditDashboardScope,
    ModelAuditRecentCall,
    ModelAuditUserOption,
} from './dashboardTypes'

const {RangePicker} = DatePicker

type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]

function DashboardPage() {
    const auth = useAuth()
    const {message} = App.useApp()
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
    const [loading, setLoading] = useState(false)
    const [userLoading, setUserLoading] = useState(false)
    const [data, setData] = useState<ModelAuditDashboardResponse>()

    const effectiveScope = canViewGlobal ? scope : 'SELF'

    async function load() {
        setLoading(true)
        try {
            const request: ModelAuditDashboardQuery = {
                scope: effectiveScope,
                startAt: range?.[0]?.toISOString(),
                endAt: range?.[1]?.toISOString(),
                userId: effectiveScope === 'GLOBAL' ? userId : undefined,
                provider: provider as ModelAuditDashboardQuery['provider'],
                model,
                scenario: scenario as ModelAuditDashboardQuery['scenario'],
                status: status as ModelAuditDashboardQuery['status'],
            }
            setData(await getModelAuditDashboard(request))
        } catch (error) {
            message.error((error as Error).message)
        } finally {
            setLoading(false)
        }
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
        void load()
    }, [effectiveScope])

    const recentColumns: ColumnsType<ModelAuditRecentCall> = [
        {title: '时间', dataIndex: 'createdAt', width: 180, render: (value) => formatDateTime(value)},
        {title: '用户', width: 160, render: (_, record) => userLabel(record)},
        {title: '模型', dataIndex: 'model', width: 190},
        {title: '场景', dataIndex: 'scenario', width: 130, render: (value) => <Tag>{value}</Tag>},
        {title: '状态', dataIndex: 'status', width: 110, render: (value) => statusTag(value)},
        {title: '输入', dataIndex: 'promptTokens', width: 90, render: numberText},
        {title: '输出', dataIndex: 'completionTokens', width: 90, render: numberText},
        {title: '总 Token', dataIndex: 'totalTokens', width: 110, render: numberText},
        {title: '耗时', dataIndex: 'durationMs', width: 100, render: (value) => `${numberText(value)}ms`},
        {title: 'Trace', dataIndex: 'traceId', width: 180, render: (value) => value || '-'},
    ]

    const chartTheme = useMemo(() => ({
        color: ['#0f766e', '#2574d8', '#e76f61', '#d9822b'],
    }), [])

    return (
        <>
            <PageToolbar
                actions={
                    <Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()} type="primary">
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
                            filterOption={false}
                            loading={userLoading}
                            onChange={setUserId}
                            onSearch={(value) => void searchUsers(value)}
                            options={userOptions.map((user) => ({label: userOptionLabel(user), value: user.id}))}
                            placeholder="选择用户"
                            showSearch
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
                        options={modelOptions(data)}
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
                    <Button onClick={() => void load()}>查询</Button>
                </Space>
            </Card>

            <Row gutter={[16, 16]} style={{marginTop: 16}}>
                <MetricCard icon={<DashboardOutlined/>} loading={loading} title="调用次数"
                            value={data?.overview.callCount ?? 0}/>
                <MetricCard icon={<ThunderboltOutlined/>} loading={loading} title="总 Token"
                            value={data?.overview.totalTokens ?? 0}/>
                <MetricCard loading={loading} title="输入 Token" value={data?.overview.promptTokens ?? 0}/>
                <MetricCard loading={loading} title="输出 Token" value={data?.overview.completionTokens ?? 0}/>
                <MetricCard loading={loading} suffix="%" title="成功率"
                            value={((data?.overview.successRate ?? 0) * 100).toFixed(1)}/>
                <MetricCard loading={loading} suffix="ms" title="平均耗时"
                            value={Math.round(data?.overview.avgDurationMs ?? 0)}/>
            </Row>

            <Row gutter={[16, 16]} style={{marginTop: 16}}>
                <Col lg={14} xs={24}>
                    <ChartCard empty={!data?.tokenTrend.length} title="Token 趋势">
                        <Line
                            data={data?.tokenTrend ?? []}
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
                    <ChartCard empty={!data?.callTrend.length} title="调用量趋势">
                        <Column
                            data={data?.callTrend ?? []}
                            height={300}
                            theme={chartTheme}
                            xField="date"
                            yField="value"
                        />
                    </ChartCard>
                </Col>
                <Col lg={12} xs={24}>
                    <ChartCard empty={!data?.modelStats.length} title="模型 Token 排行">
                        <Bar
                            data={data?.modelStats ?? []}
                            height={320}
                            theme={chartTheme}
                            xField="totalTokens"
                            yField="name"
                        />
                    </ChartCard>
                </Col>
                <Col lg={6} xs={24}>
                    <ChartCard empty={!data?.scenarioStats.length} title="场景分布">
                        <Pie
                            angleField="callCount"
                            colorField="name"
                            data={data?.scenarioStats ?? []}
                            height={320}
                            theme={chartTheme}
                        />
                    </ChartCard>
                </Col>
                <Col lg={6} xs={24}>
                    <ChartCard empty={!data?.statusStats.length} title="状态分布">
                        <Pie
                            angleField="callCount"
                            colorField="name"
                            data={data?.statusStats ?? []}
                            height={320}
                            theme={chartTheme}
                        />
                    </ChartCard>
                </Col>
                {canViewGlobal && effectiveScope === 'GLOBAL' && (
                    <Col xs={24}>
                        <ChartCard empty={!data?.userStats.length} title="用户 Token 排行">
                            <Bar
                                data={(data?.userStats ?? []).map((item) => ({...item, name: userStatLabel(item)}))}
                                height={320}
                                theme={chartTheme}
                                xField="totalTokens"
                                yField="name"
                            />
                        </ChartCard>
                    </Col>
                )}
            </Row>

            <Card style={{marginTop: 16}} title="最近调用">
                <Table<ModelAuditRecentCall>
                    columns={recentColumns}
                    dataSource={data?.recentCalls ?? []}
                    loading={loading}
                    pagination={false}
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

function modelOptions(data?: ModelAuditDashboardResponse) {
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

function formatDateTime(value?: string) {
    return value ? new Date(value).toLocaleString() : '-'
}

export const Component = DashboardPage
