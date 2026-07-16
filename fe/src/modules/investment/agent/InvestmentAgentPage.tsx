import {Alert, App, Button, Card, Flex, Select, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {resolveErrorMessage} from '../../../services/request'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {listInvestmentInstruments} from '../services/investmentMarketService'
import {
    listInvestmentAgentRuns,
    submitInvestmentAgentRun,
} from '../services/investmentAgentService'
import {listInvestmentPaperAccounts} from '../services/investmentPortfolioService'
import type {
    InvestmentAgentRunPage,
    InvestmentAgentRunResponse,
    InvestmentAgentRunType,
} from '../types/investmentAgentTypes'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {InvestmentInstrumentSummaryResponse} from '../types/investmentMarketTypes'
import type {InvestmentPaperAccountResponse} from '../types/investmentPortfolioTypes'
import {InvestmentAgentRunDrawer} from './InvestmentAgentRunDrawer'

const PAGE_SIZE = 20
const FIXED_PRESET = 'INVESTMENT_ANALYST_V1'

export default function InvestmentAgentPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const {currentWorkspace, currentPaperAccount} = useInvestmentWorkspace()
    const workspaceId = currentWorkspace?.id
    const canRun = canUseRbacButton(auth, investmentButtonCodes.agentRun)
    const [runType, setRunType] = useState<InvestmentAgentRunType>('INSTRUMENT_ANALYSIS')
    const [accountId, setAccountId] = useState<number>()
    const [instrumentIds, setInstrumentIds] = useState<number[]>([])
    const [accounts, setAccounts] = useState<InvestmentPaperAccountResponse[]>([])
    const [instruments, setInstruments] = useState<InvestmentInstrumentSummaryResponse[]>([])
    const [scopeState, setScopeState] = useState<InvestmentLoadState>('loading')
    const [scopeError, setScopeError] = useState<string>()
    const [runs, setRuns] = useState<InvestmentAgentRunPage>()
    const [runState, setRunState] = useState<InvestmentLoadState>('loading')
    const [runError, setRunError] = useState<string>()
    const [page, setPage] = useState(1)
    const [submitting, setSubmitting] = useState(false)
    const [selectedRunId, setSelectedRunId] = useState<number>()
    const scopeGenerationRef = useRef(0)
    const runGenerationRef = useRef(0)
    const submitGenerationRef = useRef(0)

    const loadScope = useCallback(async () => {
        const generation = ++scopeGenerationRef.current
        if (workspaceId === undefined) {
            setAccounts([])
            setInstruments([])
            setScopeState('empty')
            return
        }
        setScopeState('loading')
        setScopeError(undefined)
        try {
            const [accountPage, instrumentPage] = await Promise.all([
                listInvestmentPaperAccounts(workspaceId, 1, 100),
                listInvestmentInstruments({page: 1, size: 100, status: 'ACTIVE', sort: 'SYMBOL_ASC'}),
            ])
            if (generation !== scopeGenerationRef.current) return
            setAccounts(accountPage.records)
            setInstruments(instrumentPage.records)
            setScopeState('ready')
            setAccountId((current) => {
                if (current && accountPage.records.some((account) => account.id === current)) return current
                const contextAccount = currentPaperAccount?.workspaceId === workspaceId
                    ? accountPage.records.find((account) => account.id === currentPaperAccount.id)
                    : undefined
                return contextAccount?.id
            })
            setInstrumentIds((current) => current.filter((id) => (
                instrumentPage.records.some((instrument) => instrument.instrumentId === id)
            )))
        } catch (reason) {
            if (generation !== scopeGenerationRef.current) return
            setAccounts([])
            setInstruments([])
            setScopeState('error')
            setScopeError(resolveErrorMessage(reason))
        }
    }, [currentPaperAccount, workspaceId])

    const loadRuns = useCallback(async (nextPage: number) => {
        const generation = ++runGenerationRef.current
        if (workspaceId === undefined) {
            setRuns(undefined)
            setRunState('empty')
            return
        }
        setRunState('loading')
        setRunError(undefined)
        try {
            const response = await listInvestmentAgentRuns(workspaceId, nextPage, PAGE_SIZE)
            if (generation !== runGenerationRef.current) return
            setRuns(response)
            setRunState(response.records.length === 0 ? 'empty' : 'ready')
        } catch (reason) {
            if (generation !== runGenerationRef.current) return
            setRuns(undefined)
            setRunState('error')
            setRunError(resolveErrorMessage(reason))
        }
    }, [workspaceId])

    useEffect(() => {
        setRunType('INSTRUMENT_ANALYSIS')
        setAccountId(undefined)
        setInstrumentIds([])
        setSelectedRunId(undefined)
        setPage(1)
        void loadScope()
        return () => {
            scopeGenerationRef.current += 1
            submitGenerationRef.current += 1
        }
    }, [loadScope, workspaceId])

    useEffect(() => {
        void loadRuns(page)
        return () => {
            runGenerationRef.current += 1
        }
    }, [loadRuns, page])

    async function submit() {
        if (workspaceId === undefined || !validRunRequest(runType, accountId, instrumentIds)) return
        const generation = ++submitGenerationRef.current
        const submittedWorkspaceId = workspaceId
        setSubmitting(true)
        try {
            const response = await submitInvestmentAgentRun(workspaceId, {
                runType,
                accountId: accountId ?? null,
                instrumentIds,
            })
            if (generation !== submitGenerationRef.current || workspaceId !== submittedWorkspaceId) return
            setSelectedRunId(response.run.id)
            setPage(1)
            await message.success(response.duplicate
                ? `已打开相同数据截止的 Agent 运行 #${response.run.id}`
                : `Agent 运行 #${response.run.id} 已进入队列`)
            await loadRuns(1)
        } catch (reason) {
            if (generation === submitGenerationRef.current) {
                await message.error(resolveErrorMessage(reason))
            }
        } finally {
            if (generation === submitGenerationRef.current) setSubmitting(false)
        }
    }

    const selectedAccount = accounts.find((account) => account.id === accountId)
    const valid = validRunRequest(runType, accountId, instrumentIds)

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Card>
                <Flex align="center" gap={12} justify="space-between" wrap>
                    <div>
                        <Typography.Title level={4}>Investment Agent 模拟交易</Typography.Title>
                        <Typography.Text type="secondary">
                            固定预设 {FIXED_PRESET} 使用服务端绑定的数据、工作区和模拟账户进行分析。
                        </Typography.Text>
                    </div>
                    <Tag color="blue">固定代码预设</Tag>
                </Flex>
            </Card>
            <Alert
                description="没有逐单确认弹窗；AUTO_TRADE 的结构化决策通过服务端校验和账户风控后进入统一模拟盘链路。"
                showIcon
                title="仅模拟盘，风控通过后自动执行"
                type="warning"
            />
            <Card title="发起 Agent 运行">
                <InvestmentAsyncState
                    error={scopeError}
                    onRetry={() => void loadScope()}
                    state={scopeState}
                >
                    <Space orientation="vertical" size={12} style={{width: '100%'}}>
                        {instruments.length === 0 && (
                            <Alert
                                description="组合复盘仍可运行；其他运行类型需要等待服务端在代码中接入合约数据。"
                                showIcon
                                title="暂无代码接入的可分析合约"
                                type="info"
                            />
                        )}
                        <Select
                            aria-label="Agent 运行类型"
                            onChange={setRunType}
                            options={runTypeOptions}
                            style={{width: '100%'}}
                            value={runType}
                        />
                        <Select
                            allowClear
                            aria-label="Agent 模拟账户"
                            onChange={setAccountId}
                            options={accounts.map((account) => ({
                                label: `${account.name} #${account.id}${account.agentAutoTradeEnabled ? ' · Agent 已启用' : ''}`,
                                value: account.id,
                            }))}
                            placeholder={runType === 'AUTO_TRADE' ? 'AUTO_TRADE 必须选择模拟账户' : '可选：绑定模拟账户上下文'}
                            style={{width: '100%'}}
                            value={accountId}
                        />
                        <Select
                            aria-label="Agent 分析合约"
                            mode="multiple"
                            onChange={setInstrumentIds}
                            options={instruments.map((instrument) => ({
                                label: `${instrument.symbol} · ${instrument.venueCode} · #${instrument.instrumentId}`,
                                value: instrument.instrumentId,
                            }))}
                            placeholder={runType === 'PORTFOLIO_REVIEW' ? '组合复盘可不选合约' : '选择服务端代码已接入的合约'}
                            style={{width: '100%'}}
                            value={instrumentIds}
                        />
                        {runType === 'AUTO_TRADE' && selectedAccount && !selectedAccount.agentAutoTradeEnabled && (
                            <Alert
                                description="仍可运行并查看分析；任何自动交易意图都会由账户风控明确拒绝，不会产生委托。"
                                showIcon
                                title="该模拟账户尚未开启 Agent 自动交易"
                                type="info"
                            />
                        )}
                        {canRun && (
                            <Button disabled={!valid} loading={submitting} onClick={() => void submit()} type="primary">
                                发起固定预设运行
                            </Button>
                        )}
                    </Space>
                </InvestmentAsyncState>
            </Card>
            <Card title="Agent 运行记录">
                <InvestmentAsyncState
                    emptyDescription="当前工作区暂无 Agent 运行"
                    error={runError}
                    onRetry={() => void loadRuns(page)}
                    state={runState}
                >
                    <Table
                        columns={runColumns(setSelectedRunId)}
                        dataSource={runs?.records ?? []}
                        pagination={{
                            current: runs?.page ?? page,
                            pageSize: runs?.size ?? PAGE_SIZE,
                            total: runs?.total ?? 0,
                            showSizeChanger: false,
                            onChange: setPage,
                        }}
                        rowKey="id"
                        scroll={{x: 1_050}}
                    />
                </InvestmentAsyncState>
            </Card>
            <InvestmentAgentRunDrawer
                onClose={() => setSelectedRunId(undefined)}
                open={selectedRunId !== undefined}
                runId={selectedRunId}
            />
        </Space>
    )
}

export function validRunRequest(
    runType: InvestmentAgentRunType,
    accountId: number | undefined,
    instrumentIds: number[],
) {
    return (runType !== 'AUTO_TRADE' || accountId !== undefined)
        && (runType === 'PORTFOLIO_REVIEW' || instrumentIds.length > 0)
        && instrumentIds.length <= 20
}

const runTypeOptions: {label: string; value: InvestmentAgentRunType}[] = [
    {label: '市场分析', value: 'MARKET_ANALYSIS'},
    {label: '合约分析', value: 'INSTRUMENT_ANALYSIS'},
    {label: '策略复盘', value: 'STRATEGY_REVIEW'},
    {label: '组合复盘', value: 'PORTFOLIO_REVIEW'},
    {label: 'Agent 自动模拟交易', value: 'AUTO_TRADE'},
]

function runColumns(onOpen: (runId: number) => void): ColumnsType<InvestmentAgentRunResponse> {
    return [
        {title: 'Run', dataIndex: 'id', render: (value: number) => `#${value}`},
        {title: '类型', dataIndex: 'runType', render: (value: string) => runTypeOptions.find(({value: code}) => code === value)?.label ?? value},
        {title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>},
        {title: '模拟账户', dataIndex: 'accountId', render: (value: number | null) => value ? `#${value}` : '-'},
        {title: '数据截止', dataIndex: 'dataAsOf'},
        {title: '报告', dataIndex: 'reportId', render: (value: number | null) => value ? `#${value}` : '-'},
        {title: '创建时间', dataIndex: 'createdAt'},
        {title: '操作', key: 'action', render: (_, record) => <Button onClick={() => onOpen(record.id)} size="small">查看</Button>},
    ]
}

function statusColor(status: string) {
    if (status === 'SUCCEEDED') return 'success'
    if (status === 'FAILED') return 'error'
    if (status === 'RUNNING') return 'processing'
    return 'default'
}

export const Component = InvestmentAgentPage
