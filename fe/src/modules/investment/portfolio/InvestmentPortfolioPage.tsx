import {
    Alert,
    App,
    Button,
    Card,
    Descriptions,
    Empty,
    Flex,
    InputNumber,
    Select,
    Space,
    Switch,
    Typography,
} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import {resolveErrorMessage} from '../../../services/request'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {investmentButtonCodes} from '../investmentPermissionCodes'
import {
    cancelInvestmentPaperOrder,
    createInvestmentPaperAccount,
    getInvestmentPaperAccount,
    listInvestmentEquity,
    listInvestmentLedger,
    listInvestmentPaperAccounts,
    listInvestmentPaperFills,
    listInvestmentPaperOrders,
    listInvestmentPositions,
    submitInvestmentPaperTrade,
    updateInvestmentPaperAccountSwitches,
    updateInvestmentRiskProfile,
} from '../services/investmentPortfolioService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    CreateInvestmentPaperAccountRequest,
    InvestmentEquityPage,
    InvestmentFillMarkerPage,
    InvestmentLedgerPage,
    InvestmentPaperAccountDetail,
    InvestmentPaperAccountResponse,
    InvestmentPaperOrderPage,
    InvestmentPaperTradeResult,
    InvestmentPosition,
    SubmitInvestmentPaperTradeRequest,
    UpdateInvestmentRiskProfileRequest,
} from '../types/investmentPortfolioTypes'
import {PaperAccountCreateDrawer} from './PaperAccountCreateDrawer'
import {PortfolioEquityChart} from './PortfolioEquityChart'
import {PortfolioFillsTable} from './PortfolioFillsTable'
import {PortfolioLedgerTable} from './PortfolioLedgerTable'
import {PortfolioOrdersTable} from './PortfolioOrdersTable'
import {PortfolioPositionsTable} from './PortfolioPositionsTable'
import {RiskProfileDrawer} from './RiskProfileDrawer'
import {TradeIntentDrawer} from './TradeIntentDrawer'

const ORDER_PAGE_SIZE = 20
const LEDGER_PAGE_SIZE = 50

export default function InvestmentPortfolioPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const {
        currentWorkspace,
        currentPaperAccount,
        setCurrentPaperAccount,
    } = useInvestmentWorkspace()
    const workspaceId = currentWorkspace?.id
    const accountId = currentPaperAccount && currentPaperAccount.workspaceId === workspaceId
        ? currentPaperAccount.id
        : undefined
    const canTrade = canUseRbacButton(auth, investmentButtonCodes.paperTrade)
    const [accounts, setAccounts] = useState<InvestmentPaperAccountResponse[]>([])
    const [accountsWorkspaceId, setAccountsWorkspaceId] = useState<number>()
    const [accountListState, setAccountListState] = useState<InvestmentLoadState>('loading')
    const [accountListError, setAccountListError] = useState<string>()
    const [detail, setDetail] = useState<InvestmentPaperAccountDetail>()
    const [detailState, setDetailState] = useState<InvestmentLoadState>('idle')
    const [positions, setPositions] = useState<InvestmentPosition[]>([])
    const [positionsState, setPositionsState] = useState<InvestmentLoadState>('idle')
    const [positionsError, setPositionsError] = useState<string>()
    const [orders, setOrders] = useState<InvestmentPaperOrderPage>()
    const [ordersState, setOrdersState] = useState<InvestmentLoadState>('idle')
    const [ordersError, setOrdersError] = useState<string>()
    const [orderPage, setOrderPage] = useState(1)
    const [ledger, setLedger] = useState<InvestmentLedgerPage>()
    const [ledgerState, setLedgerState] = useState<InvestmentLoadState>('idle')
    const [ledgerError, setLedgerError] = useState<string>()
    const [ledgerPage, setLedgerPage] = useState(1)
    const [equity, setEquity] = useState<InvestmentEquityPage>()
    const [equityState, setEquityState] = useState<InvestmentLoadState>('idle')
    const [equityError, setEquityError] = useState<string>()
    const [fills, setFills] = useState<InvestmentFillMarkerPage>()
    const [fillsState, setFillsState] = useState<InvestmentLoadState>('idle')
    const [fillsError, setFillsError] = useState<string>()
    const [fillInstrumentId, setFillInstrumentId] = useState<number | null>(null)
    const [fillWindow, setFillWindow] = useState<{from: string; to: string}>()
    const [createOpen, setCreateOpen] = useState(false)
    const [riskOpen, setRiskOpen] = useState(false)
    const [tradeOpen, setTradeOpen] = useState(false)
    const [switchSaving, setSwitchSaving] = useState(false)
    const [cancellingId, setCancellingId] = useState<number>()
    const listGeneration = useRef(0)
    const detailGeneration = useRef(0)
    const positionsGeneration = useRef(0)
    const ordersGeneration = useRef(0)
    const ledgerGeneration = useRef(0)
    const equityGeneration = useRef(0)
    const fillsGeneration = useRef(0)

    const loadAccounts = useCallback(async () => {
        const generation = ++listGeneration.current
        if (workspaceId === undefined) {
            setAccounts([])
            setAccountsWorkspaceId(undefined)
            setAccountListState('empty')
            return
        }
        setAccounts([])
        setAccountsWorkspaceId(workspaceId)
        setAccountListState('loading')
        setAccountListError(undefined)
        try {
            const page = await listInvestmentPaperAccounts(workspaceId, 1, 100)
            if (generation === listGeneration.current) {
                setAccounts(page.records)
                setAccountListState(page.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === listGeneration.current) {
                setAccounts([])
                setAccountListState('error')
                setAccountListError(resolveErrorMessage(reason))
            }
        }
    }, [workspaceId])

    const loadDetail = useCallback(async (id: number) => {
        const generation = ++detailGeneration.current
        setDetailState('loading')
        try {
            const response = await getInvestmentPaperAccount(id)
            if (generation === detailGeneration.current && accountId === id) {
                setDetail(response)
                setDetailState('ready')
                setAccounts((current) => replaceAccount(current, response.account))
            }
        } catch {
            if (generation === detailGeneration.current) setDetailState('error')
        }
    }, [accountId])

    const loadPositions = useCallback(async (id: number) => {
        const generation = ++positionsGeneration.current
        setPositionsState('loading')
        setPositionsError(undefined)
        try {
            const response = await listInvestmentPositions(id)
            if (generation === positionsGeneration.current && accountId === id) {
                setPositions(response)
                setPositionsState(response.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === positionsGeneration.current) {
                setPositions([])
                setPositionsState('error')
                setPositionsError(resolveErrorMessage(reason))
            }
        }
    }, [accountId])

    const loadOrders = useCallback(async (id: number, page: number) => {
        const generation = ++ordersGeneration.current
        setOrdersState('loading')
        setOrdersError(undefined)
        try {
            const response = await listInvestmentPaperOrders(id, page, ORDER_PAGE_SIZE)
            if (generation === ordersGeneration.current && accountId === id) {
                setOrders(response)
                setOrdersState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === ordersGeneration.current) {
                setOrders(undefined)
                setOrdersState('error')
                setOrdersError(resolveErrorMessage(reason))
            }
        }
    }, [accountId])

    const loadLedger = useCallback(async (id: number, page: number) => {
        const generation = ++ledgerGeneration.current
        setLedgerState('loading')
        setLedgerError(undefined)
        try {
            const response = await listInvestmentLedger(id, page, LEDGER_PAGE_SIZE)
            if (generation === ledgerGeneration.current && accountId === id) {
                setLedger(response)
                setLedgerState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === ledgerGeneration.current) {
                setLedger(undefined)
                setLedgerState('error')
                setLedgerError(resolveErrorMessage(reason))
            }
        }
    }, [accountId])

    const loadEquity = useCallback(async (id: number) => {
        const generation = ++equityGeneration.current
        setEquityState('loading')
        setEquityError(undefined)
        try {
            const response = await listInvestmentEquity(id, 1, 500)
            if (generation === equityGeneration.current && accountId === id) {
                setEquity(response)
                setEquityState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === equityGeneration.current) {
                setEquity(undefined)
                setEquityState('error')
                setEquityError(resolveErrorMessage(reason))
            }
        }
    }, [accountId])

    useEffect(() => {
        listGeneration.current += 1
        setCreateOpen(false)
        clearFacts()
        void loadAccounts()
        return () => { listGeneration.current += 1 }
        function clearFacts() {
            setDetail(undefined)
            setPositions([])
            setOrders(undefined)
            setLedger(undefined)
            setEquity(undefined)
            setFills(undefined)
        }
    }, [loadAccounts, workspaceId])

    useEffect(() => {
        detailGeneration.current += 1
        positionsGeneration.current += 1
        ordersGeneration.current += 1
        ledgerGeneration.current += 1
        equityGeneration.current += 1
        fillsGeneration.current += 1
        setDetail(undefined)
        setPositions([])
        setOrders(undefined)
        setLedger(undefined)
        setEquity(undefined)
        setFills(undefined)
        setFillInstrumentId(null)
        setFillWindow(undefined)
        setRiskOpen(false)
        setTradeOpen(false)
        setOrderPage(1)
        setLedgerPage(1)
        if (accountId === undefined) {
            setDetailState('idle')
            setPositionsState('idle')
            setOrdersState('idle')
            setLedgerState('idle')
            setEquityState('idle')
            setFillsState('idle')
            return
        }
        void loadDetail(accountId)
        void loadPositions(accountId)
        void loadOrders(accountId, 1)
        void loadLedger(accountId, 1)
        void loadEquity(accountId)
    }, [accountId, loadDetail, loadEquity, loadLedger, loadOrders, loadPositions])

    function selectAccount(id: number | undefined) {
        const account = visibleAccounts.find((value) => value.id === id)
        setCurrentPaperAccount(account ? {
            id: account.id,
            workspaceId: account.workspaceId,
            name: account.name,
            baseCurrency: account.baseCurrency,
            status: account.status,
        } : null)
    }

    async function create(request: CreateInvestmentPaperAccountRequest) {
        if (workspaceId === undefined) throw new Error('请先选择工作区')
        const response = await createInvestmentPaperAccount(workspaceId, request)
        setAccounts((current) => replaceAccount(current, response.account))
        setAccountListState('ready')
        selectCreated(response.account)
        return response
    }

    function selectCreated(account: InvestmentPaperAccountResponse) {
        setCurrentPaperAccount({
            id: account.id, workspaceId: account.workspaceId, name: account.name,
            baseCurrency: account.baseCurrency, status: account.status,
        })
    }

    async function saveRisk(request: UpdateInvestmentRiskProfileRequest) {
        if (accountId === undefined) throw new Error('请先选择模拟账户')
        const updated = await updateInvestmentRiskProfile(accountId, request)
        setDetail((current) => current ? {...current, riskProfile: updated} : current)
        return updated
    }

    async function changeSwitch(field: 'tradingEnabled' | 'agentAutoTradeEnabled', checked: boolean) {
        if (!detail || switchSaving) return
        const previous = detail.account
        const optimistic = {...previous, [field]: checked}
        setDetail({...detail, account: optimistic})
        setAccounts((current) => replaceAccount(current, optimistic))
        setSwitchSaving(true)
        try {
            const updated = await updateInvestmentPaperAccountSwitches(previous.id, {
                tradingEnabled: optimistic.tradingEnabled,
                agentAutoTradeEnabled: optimistic.agentAutoTradeEnabled,
                version: previous.version,
            })
            setDetail((current) => current ? {...current, account: updated} : current)
            setAccounts((current) => replaceAccount(current, updated))
        } catch (reason) {
            setDetail((current) => current ? {...current, account: previous} : current)
            setAccounts((current) => replaceAccount(current, previous))
            void message.error(resolveErrorMessage(reason))
        } finally {
            setSwitchSaving(false)
        }
    }

    async function submitTrade(request: SubmitInvestmentPaperTradeRequest): Promise<InvestmentPaperTradeResult> {
        if (accountId === undefined) throw new Error('请先选择模拟账户')
        const response = await submitInvestmentPaperTrade(accountId, request)
        void refreshAccountFacts(accountId)
        return response
    }

    async function cancelOrder(orderId: number) {
        if (accountId === undefined || cancellingId !== undefined) return
        setCancellingId(orderId)
        try {
            await cancelInvestmentPaperOrder(orderId)
            await loadOrders(accountId, orderPage)
            void message.success(`模拟委托 #${orderId} 已取消`)
        } catch (reason) {
            void message.error(resolveErrorMessage(reason))
        } finally {
            setCancellingId(undefined)
        }
    }

    async function queryFills(page = 1) {
        if (accountId === undefined || !fillInstrumentId) return
        const window = page === 1 || !fillWindow ? recentWindow() : fillWindow
        if (page === 1) setFillWindow(window)
        const generation = ++fillsGeneration.current
        setFillsState('loading')
        setFillsError(undefined)
        try {
            const response = await listInvestmentPaperFills(
                accountId, fillInstrumentId, window.from, window.to, page, 100,
            )
            if (generation === fillsGeneration.current) {
                setFills(response)
                setFillsState(response.records.length === 0 ? 'empty' : 'ready')
            }
        } catch (reason) {
            if (generation === fillsGeneration.current) {
                setFills(undefined)
                setFillsState('error')
                setFillsError(resolveErrorMessage(reason))
            }
        }
    }

    function changeFillInstrument(value: number | null) {
        fillsGeneration.current += 1
        setFillInstrumentId(value)
        setFillWindow(undefined)
        setFills(undefined)
        setFillsError(undefined)
        setFillsState('idle')
    }

    function refreshAccountFacts(id: number) {
        void loadDetail(id)
        void loadPositions(id)
        void loadOrders(id, orderPage)
        void loadLedger(id, ledgerPage)
        void loadEquity(id)
    }

    const visibleAccounts = accountsWorkspaceId === workspaceId ? accounts : []
    const visibleAccountListState = accountsWorkspaceId === workspaceId
        ? accountListState
        : 'loading'

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Card>
                <Flex align="center" gap={12} justify="space-between" wrap>
                    <div>
                        <Typography.Title level={4}>USDT 合约模拟盘</Typography.Title>
                        <Typography.Text type="secondary">账户、仓位、保证金、资金费和强平结果均由服务端计算。</Typography.Text>
                    </div>
                    {canTrade && <Button onClick={() => setCreateOpen(true)} type="primary">创建模拟账户</Button>}
                </Flex>
            </Card>
            <Card title="模拟账户">
                <InvestmentAsyncState
                    emptyDescription="当前工作区暂无模拟账户"
                    error={accountListError}
                    onRetry={() => void loadAccounts()}
                    state={visibleAccountListState}
                >
                    <Select
                        aria-label="模拟账户"
                        onChange={selectAccount}
                        options={visibleAccounts.map((account) => ({
                            label: `${account.name} · ${account.equity} ${account.baseCurrency}`,
                            value: account.id,
                        }))}
                        placeholder="选择一个私人模拟账户"
                        style={{minWidth: 320}}
                        value={accountId}
                    />
                </InvestmentAsyncState>
            </Card>
            {accountId === undefined ? <Empty description="请选择模拟账户后查看私人仓位与交易事实"/> : (
                <>
                    <Card title="账户事实" extra={canTrade && <Space>
                        <Button onClick={() => refreshAccountFacts(accountId)}>刷新</Button>
                        <Button onClick={() => setRiskOpen(true)}>风险限制</Button>
                        <Button disabled={!detail?.account.tradingEnabled} onClick={() => setTradeOpen(true)} type="primary">手工模拟下单</Button>
                    </Space>}>
                        <InvestmentAsyncState state={detailState} error="模拟账户详情加载失败" onRetry={() => void loadDetail(accountId)}>
                            {detail && <AccountFacts detail={detail} switchSaving={switchSaving} canTrade={canTrade}
                                onSwitch={(field, value) => void changeSwitch(field, value)}/>}
                        </InvestmentAsyncState>
                    </Card>
                    <Card title="权益与回撤">
                        <FactError error={equityError} state={equityState}/>
                        {equityState === 'loading' ? <InvestmentAsyncState state="loading"><span/></InvestmentAsyncState>
                            : <PortfolioEquityChart points={equity?.records ?? []}/>}
                    </Card>
                    <Card title="当前持仓">
                        <FactError error={positionsError} state={positionsState}/>
                        <PortfolioPositionsTable positions={positions} loading={positionsState === 'loading'}/>
                    </Card>
                    <Card title="模拟委托">
                        <FactError error={ordersError} state={ordersState}/>
                        <PortfolioOrdersTable
                            cancellingId={cancellingId}
                            loading={ordersState === 'loading'}
                            onCancel={(id) => void cancelOrder(id)}
                            onPageChange={(page) => { setOrderPage(page); void loadOrders(accountId, page) }}
                            page={orders}
                        />
                    </Card>
                    <Card title="成交与强平标记" extra={<Space>
                        <InputNumber aria-label="成交合约 ID" min={1} onChange={changeFillInstrument} precision={0} value={fillInstrumentId}/>
                        <Button disabled={!fillInstrumentId} loading={fillsState === 'loading'} onClick={() => void queryFills(1)}>查询最近 30 天</Button>
                    </Space>}>
                        <FactError error={fillsError} state={fillsState}/>
                        <PortfolioFillsTable
                            loading={fillsState === 'loading'}
                            onPageChange={(page) => void queryFills(page)}
                            page={fills}
                        />
                    </Card>
                    <Card title="保证金与资金流水">
                        <FactError error={ledgerError} state={ledgerState}/>
                        <PortfolioLedgerTable
                            loading={ledgerState === 'loading'}
                            onPageChange={(page) => { setLedgerPage(page); void loadLedger(accountId, page) }}
                            page={ledger}
                        />
                    </Card>
                </>
            )}
            <PaperAccountCreateDrawer onClose={() => setCreateOpen(false)} onCreate={create} open={createOpen}/>
            <RiskProfileDrawer
                onClose={() => setRiskOpen(false)}
                onSave={saveRisk}
                open={riskOpen}
                profile={detail?.riskProfile}
            />
            <TradeIntentDrawer onClose={() => setTradeOpen(false)} onSubmit={submitTrade} open={tradeOpen}/>
        </Space>
    )
}

function AccountFacts({detail, switchSaving, canTrade, onSwitch}: {
    detail: InvestmentPaperAccountDetail
    switchSaving: boolean
    canTrade: boolean
    onSwitch: (field: 'tradingEnabled' | 'agentAutoTradeEnabled', value: boolean) => void
}) {
    const account = detail.account
    return (
        <Descriptions bordered column={{xs: 1, sm: 2, lg: 4}} items={[
            {key: 'equity', label: '权益', children: <InvestmentDecimalText value={account.equity} suffix="USDT"/>},
            {key: 'wallet', label: '钱包余额', children: <InvestmentDecimalText value={account.walletBalance} suffix="USDT"/>},
            {key: 'available', label: '可用余额', children: <InvestmentDecimalText value={account.availableBalance} suffix="USDT"/>},
            {key: 'margin', label: '已用保证金', children: <InvestmentDecimalText value={account.usedMargin} suffix="USDT"/>},
            {key: 'exposure', label: '总敞口', children: <InvestmentDecimalText value={account.grossExposure} suffix="USDT"/>},
            {key: 'pnl', label: '未实现损益', children: <InvestmentDecimalText value={account.unrealizedPnl} suffix="USDT"/>},
            {key: 'manual', label: '手工模拟交易', children: <Switch checked={account.tradingEnabled} disabled={!canTrade || switchSaving} onChange={(value) => onSwitch('tradingEnabled', value)}/>},
            {key: 'agent', label: 'Agent 自动模拟交易', children: <Switch checked={account.agentAutoTradeEnabled} disabled={!canTrade || switchSaving} onChange={(value) => onSwitch('agentAutoTradeEnabled', value)}/>},
        ]}/>
    )
}

function FactError({state, error}: {state: InvestmentLoadState; error?: string}) {
    return state === 'error' ? <Alert description={error} showIcon title="该事实区加载失败" type="error"/> : null
}

function replaceAccount(current: InvestmentPaperAccountResponse[], account: InvestmentPaperAccountResponse) {
    return [account, ...current.filter((value) => value.id !== account.id)]
}

function recentWindow() {
    const to = new Date()
    const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000)
    return {from: from.toISOString(), to: to.toISOString()}
}

export const Component = InvestmentPortfolioPage
