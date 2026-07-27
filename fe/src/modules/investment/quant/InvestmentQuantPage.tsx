import {ExperimentOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, DatePicker, Descriptions, Select, Tag} from 'antd'
import type {RangePickerProps} from 'antd/es/date-picker'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {FormDrawer} from '../../../components/FormDrawer'
import {PageGrid, PageSection, PageStack} from '../../../components/PageSection'
import {PageToolbar} from '../../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {InvestmentAsyncState, InvestmentTableFailure, investmentTableLocale} from '../components/InvestmentAsyncState'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {
    listInvestmentBacktests,
    listInvestmentStrategies,
    submitInvestmentBacktest,
} from '../services/investmentQuantService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentBacktestPage,
    InvestmentBacktestRunResponse,
    InvestmentStrategyDescriptor,
    SubmitInvestmentBacktestRequest,
} from '../types/investmentQuantTypes'
import {InvestmentBacktestDrawer} from './InvestmentBacktestDrawer'

const PAGE_SIZE = 20
const {RangePicker} = DatePicker
type RangeValue = Parameters<NonNullable<RangePickerProps['onChange']>>[0]

export default function InvestmentQuantPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const {currentWorkspace} = useInvestmentWorkspace()
    const workspaceId = currentWorkspace?.id
    const canCreate = canUseRbacButton(auth, investmentButtonCodes.backtestCreate)
    const [strategies, setStrategies] = useState<InvestmentStrategyDescriptor[]>([])
    const [strategyState, setStrategyState] = useState<InvestmentLoadState>('loading')
    const [strategyError, setStrategyError] = useState<string>()
    const [runs, setRuns] = useState<InvestmentBacktestPage>()
    const [runState, setRunState] = useState<InvestmentLoadState>('loading')
    const [runError, setRunError] = useState<string>()
    const [page, setPage] = useState(1)
    const [createOpen, setCreateOpen] = useState(false)
    const [strategyCode, setStrategyCode] = useState<string>()
    const [instrumentValues, setInstrumentValues] = useState<string[]>([])
    const [range, setRange] = useState<RangeValue>(null)
    const [submitting, setSubmitting] = useState(false)
    const [submitError, setSubmitError] = useState<string>()
    const [selectedRunId, setSelectedRunId] = useState<number>()
    const strategyGenerationRef = useRef(0)
    const runGenerationRef = useRef(0)
    const submitGenerationRef = useRef(0)

    const loadStrategies = useCallback(async () => {
        const generation = ++strategyGenerationRef.current
        setStrategyState('loading')
        setStrategyError(undefined)
        try {
            const response = await listInvestmentStrategies()
            if (generation === strategyGenerationRef.current) {
                setStrategies(response)
                setStrategyState(response.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === strategyGenerationRef.current) {
                setStrategies([])
                setStrategyState('error')
                setStrategyError(errorMessage(reason, '代码策略加载失败'))
            }
        }
    }, [])

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
            const response = await listInvestmentBacktests(workspaceId, nextPage, PAGE_SIZE)
            if (generation === runGenerationRef.current) {
                setRuns(response)
                setRunState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === runGenerationRef.current) {
                setRuns(undefined)
                setRunState('error')
                setRunError(errorMessage(reason, '回测记录加载失败'))
            }
        }
    }, [workspaceId])

    useEffect(() => {
        void loadStrategies()
        return () => {
            strategyGenerationRef.current += 1
        }
    }, [loadStrategies])

    useEffect(() => {
        void loadRuns(page)
        return () => {
            runGenerationRef.current += 1
        }
    }, [loadRuns, page])

    useEffect(() => {
        setSelectedRunId(undefined)
        setPage(1)
        return () => {
            submitGenerationRef.current += 1
        }
    }, [workspaceId])

    function openCreate() {
        setStrategyCode(strategies[0]?.strategyCode)
        setInstrumentValues([])
        setRange(null)
        setSubmitError(undefined)
        setCreateOpen(true)
    }

    async function submit() {
        if (workspaceId === undefined) {
            return
        }
        const request = createBacktestRequest(strategyCode, instrumentValues, range)
        if (!request) {
            return
        }
        const generation = ++submitGenerationRef.current
        const submittedWorkspaceId = workspaceId
        setSubmitting(true)
        setSubmitError(undefined)
        try {
            const response = await submitInvestmentBacktest(submittedWorkspaceId, request)
            if (generation !== submitGenerationRef.current || workspaceId !== submittedWorkspaceId) {
                return
            }
            setCreateOpen(false)
            setSelectedRunId(response.runId)
            setPage(1)
            void message.success(`回测 #${response.runId} 已进入任务队列`)
            await loadRuns(1)
        } catch (reason) {
            if (generation === submitGenerationRef.current) {
                setSubmitError(errorMessage(reason, '回测提交失败'))
            }
        } finally {
            if (generation === submitGenerationRef.current) {
                setSubmitting(false)
            }
        }
    }

    const selectedStrategy = strategies.find((strategy) => strategy.strategyCode === strategyCode)
    const validRequest = createBacktestRequest(strategyCode, instrumentValues, range) !== null

    return (
        <PageStack>
            <PageToolbar
                actions={canCreate && strategies.length > 0 && (
                    <Button icon={<PlusOutlined/>} onClick={openCreate} type="primary">发起回测</Button>
                )}
                description="策略规则、周期、杠杆、手续费和滑点均由 Java 代码固定；V1 初始权益为服务端 10000 USDT。"
                icon={<ExperimentOutlined/>}
                title="代码策略与合约回测"
            />
            <PageSection
                description="策略由服务端 Java 代码声明，前端只读展示，无法在此改写规则。"
                title="已部署代码策略"
            >
                <InvestmentAsyncState
                    emptyDescription="尚未部署可用的生产代码策略"
                    emptyHint="策略随服务端发布上线，请联系管理员确认部署状态后重试。"
                    error={strategyError}
                    onRetry={() => void loadStrategies()}
                    state={strategyState}
                >
                    <StrategyCards strategies={strategies}/>
                </InvestmentAsyncState>
            </PageSection>
            <DataTable<InvestmentBacktestRunResponse>
                columns={runColumns(setSelectedRunId)}
                count={runs?.total ?? 0}
                dataSource={runs?.records ?? []}
                emptyDescription="选择一个已部署策略并发起回测，运行结果会出现在这里。"
                emptyTitle="当前工作区暂无回测记录"
                loading={runState === 'loading'}
                locale={investmentTableLocale(
                    runState,
                    <InvestmentTableFailure error={runError} onRetry={() => void loadRuns(page)} state={runState}/>,
                )}
                pagination={{
                    current: runs?.page ?? page,
                    pageSize: runs?.size ?? PAGE_SIZE,
                    total: runs?.total ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
                rowKey="runId"
                scroll={{x: 1_100}}
                title="回测记录"
            />
            <FormDrawer
                loading={submitting}
                onClose={() => setCreateOpen(false)}
                onSubmit={() => void submit()}
                open={createOpen}
                submitDisabled={!validRequest}
                submitText="加入回测队列"
                title="发起固定代码策略回测"
            >
                <PageStack>
                    <Select
                        aria-label="回测策略"
                        className="u-full-width"
                        onChange={setStrategyCode}
                        options={strategies.map((strategy) => ({
                            label: `${strategy.displayName} / ${strategy.strategyVersion}`,
                            value: strategy.strategyCode,
                        }))}
                        value={strategyCode}
                    />
                    <Select
                        aria-label="回测合约 ID"
                        className="u-full-width"
                        mode="tags"
                        onChange={setInstrumentValues}
                        open={false}
                        placeholder="输入内部合约 ID 后回车，可选择多个"
                        tokenSeparators={[',', ' ']}
                        value={instrumentValues}
                    />
                    <RangePicker
                        aria-label="回测时间范围"
                        className="u-full-width"
                        onChange={setRange}
                        showTime
                        value={range}
                    />
                    {selectedStrategy && <StrategyDescriptorView strategy={selectedStrategy}/>}
                    {submitError && <Alert description={submitError} showIcon title="回测提交失败" type="error"/>}
                </PageStack>
            </FormDrawer>
            <InvestmentBacktestDrawer
                onClose={() => setSelectedRunId(undefined)}
                open={selectedRunId !== undefined}
                runId={selectedRunId}
            />
        </PageStack>
    )
}

function StrategyCards({strategies}: {strategies: InvestmentStrategyDescriptor[]}) {
    return (
        <PageGrid minWidth={300}>
            {strategies.map((strategy) => (
                <Card key={`${strategy.strategyCode}:${strategy.strategyVersion}`} size="small">
                    <StrategyDescriptorView strategy={strategy}/>
                </Card>
            ))}
        </PageGrid>
    )
}

function StrategyDescriptorView({strategy}: {strategy: InvestmentStrategyDescriptor}) {
    return (
        <Descriptions
            column={1}
            size="small"
            title={`${strategy.displayName} / ${strategy.strategyVersion}`}
            items={[
                {key: 'description', label: '说明', children: strategy.description},
                {key: 'interval', label: '固定周期', children: strategy.evaluationInterval},
                {key: 'price', label: '信号价型', children: strategy.priceType},
                {key: 'leverage', label: '默认杠杆', children: String(strategy.defaultLeverage)},
                {key: 'sizing', label: '仓位策略', children: strategy.positionSizingPolicy},
                {key: 'matching', label: '撮合模型', children: strategy.matchingModelCode},
            ]}
        />
    )
}

function runColumns(onOpen: (runId: number) => void): ColumnsType<InvestmentBacktestRunResponse> {
    return [
        {title: 'Run', dataIndex: 'runId', render: (value: number) => `#${value}`},
        {title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>},
        {title: '初始权益', dataIndex: 'initialEquity', render: decimalCell},
        {title: '总收益率', dataIndex: 'totalReturn', render: decimalCell},
        {title: '最大回撤', dataIndex: 'maxDrawdown', render: decimalCell},
        {title: 'Sharpe', dataIndex: 'sharpeRatio', render: decimalCell},
        {title: '成交', dataIndex: 'tradeCount', render: (value: number | null) => value ?? '-'},
        {title: '创建时间', dataIndex: 'createdAt'},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button aria-label={`查看回测 #${record.runId}`} onClick={() => onOpen(record.runId)} size="small">
                    查看
                </Button>
            ),
        },
    ]
}

function decimalCell(value: string | null) {
    return <InvestmentDecimalText value={value}/>
}

export function createBacktestRequest(
    strategyCode: string | undefined,
    instrumentValues: string[],
    range: RangeValue,
): SubmitInvestmentBacktestRequest | null {
    const instrumentIds = [...new Set(instrumentValues.map(Number))]
    if (!strategyCode || !range?.[0] || !range[1] || instrumentIds.length === 0
        || instrumentIds.some((value) => !Number.isSafeInteger(value) || value <= 0)
        || !range[1].isAfter(range[0])) {
        return null
    }
    return {
        strategyCode,
        instrumentIds,
        startTime: range[0].toISOString(),
        endTime: range[1].toISOString(),
    }
}

function statusColor(status: string) {
    if (status === 'SUCCEEDED') return 'success'
    if (status === 'FAILED' || status === 'CANCELLED') return 'error'
    if (status === 'RUNNING') return 'processing'
    return 'default'
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}

export const Component = InvestmentQuantPage
