import {LineChartOutlined} from '@ant-design/icons'
import {App, Form, Select} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {FilterBar} from '../../../components/FilterBar'
import {PageStack} from '../../../components/PageSection'
import {PageToolbar} from '../../../components/PageToolbar'
import type {PageResult} from '../../../types/api'
import {InvestmentTableFailure, investmentTableLocale} from '../components/InvestmentAsyncState'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {
    addInvestmentWatchlistItem,
    listInvestmentInstruments,
    listInvestmentWatchlists,
} from '../services/investmentMarketService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentInstrumentStatus,
    InvestmentInstrumentSummaryResponse,
    InvestmentMarketSort,
    InvestmentWatchlistResponse,
} from '../types/investmentMarketTypes'
import {investmentMarketColumns} from './investmentMarketColumns'

const PAGE_SIZE = 20
const DEFAULT_SORT: InvestmentMarketSort = 'SYMBOL_ASC'

type MarketFilters = {
    status?: InvestmentInstrumentStatus
    sort?: InvestmentMarketSort
}

export default function InvestmentMarketPage() {
    const {message} = App.useApp()
    const {currentWorkspace} = useInvestmentWorkspace()
    const [page, setPage] = useState(1)
    const [status, setStatus] = useState<InvestmentInstrumentStatus>()
    const [sort, setSort] = useState<InvestmentMarketSort>(DEFAULT_SORT)
    const [result, setResult] = useState<PageResult<InvestmentInstrumentSummaryResponse>>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('loading')
    const [loadError, setLoadError] = useState<string>()
    const [watchlists, setWatchlists] = useState<InvestmentWatchlistResponse[]>([])
    const [currentWatchlistId, setCurrentWatchlistId] = useState<number>()
    const [addingInstrumentId, setAddingInstrumentId] = useState<number>()
    const marketGenerationRef = useRef(0)
    const watchlistGenerationRef = useRef(0)

    const loadMarket = useCallback(async () => {
        const generation = ++marketGenerationRef.current
        setLoadState('loading')
        setLoadError(undefined)
        try {
            const response = await listInvestmentInstruments({page, size: PAGE_SIZE, status, sort})
            if (generation !== marketGenerationRef.current) {
                return
            }
            setResult(response)
            setLoadState(response.records.length === 0 ? 'empty' : 'ready')
        } catch (reason) {
            if (generation !== marketGenerationRef.current) {
                return
            }
            setLoadState('error')
            setLoadError(reason instanceof Error ? reason.message : '合约行情加载失败')
        }
    }, [page, sort, status])

    useEffect(() => {
        void loadMarket()
        return () => {
            marketGenerationRef.current += 1
        }
    }, [loadMarket])

    useEffect(() => {
        const generation = ++watchlistGenerationRef.current
        setWatchlists([])
        setCurrentWatchlistId(undefined)
        if (!currentWorkspace) {
            return
        }
        void listInvestmentWatchlists(currentWorkspace.id).then((response) => {
            if (generation !== watchlistGenerationRef.current) {
                return
            }
            setWatchlists(response.records)
            setCurrentWatchlistId(response.records[0]?.id)
        }).catch(() => {
            if (generation === watchlistGenerationRef.current) {
                setWatchlists([])
            }
        })
        return () => {
            watchlistGenerationRef.current += 1
        }
    }, [currentWorkspace])

    const watchlistDisabledReason = !currentWorkspace
        ? '请先选择或创建投资工作区'
        : watchlists.length === 0
            ? '请先创建一个自选列表'
            : undefined
    const canAddToWatchlist = Boolean(currentWorkspace && currentWatchlistId)

    async function addToWatchlist(instrument: InvestmentInstrumentSummaryResponse) {
        if (!currentWorkspace || !currentWatchlistId) {
            return
        }
        setAddingInstrumentId(instrument.instrumentId)
        try {
            await addInvestmentWatchlistItem(currentWorkspace.id, currentWatchlistId, {
                instrumentId: instrument.instrumentId,
                note: null,
            })
            await message.success(`${instrument.symbol} 已加入自选`)
        } catch (reason) {
            await message.error(reason instanceof Error ? reason.message : '加入自选失败')
        } finally {
            setAddingInstrumentId(undefined)
        }
    }

    function applyFilters(values: MarketFilters) {
        setPage(1)
        setStatus(values.status)
        setSort(values.sort ?? DEFAULT_SORT)
    }

    const columns = investmentMarketColumns({
        canAddToWatchlist,
        watchlistDisabledReason,
        addingInstrumentId,
        onAddToWatchlist: (instrument) => void addToWatchlist(instrument),
    })

    return (
        <PageStack>
            <PageToolbar
                description="仅展示服务端代码已接入的数据范围"
                icon={<LineChartOutlined/>}
                title="永续合约行情"
            />
            <FilterBar<MarketFilters>
                initialValues={{sort: DEFAULT_SORT}}
                instant
                onReset={() => applyFilters({sort: DEFAULT_SORT})}
                onSearch={applyFilters}
            >
                <Form.Item label="合约状态" name="status">
                    <Select
                        allowClear
                        options={[
                            {label: '交易中', value: 'ACTIVE'},
                            {label: '暂停', value: 'SUSPENDED'},
                            {label: '下线', value: 'OFFLINE'},
                        ]}
                        placeholder="全部状态"
                    />
                </Form.Item>
                <Form.Item label="合约排序" name="sort">
                    <Select
                        options={[
                            {label: '合约升序', value: 'SYMBOL_ASC'},
                            {label: '合约降序', value: 'SYMBOL_DESC'},
                        ]}
                    />
                </Form.Item>
            </FilterBar>
            <DataTable<InvestmentInstrumentSummaryResponse>
                columns={columns}
                count={result?.total ?? 0}
                dataSource={result?.records ?? []}
                emptyDescription="行情范围由服务端代码接入决定；换一个状态筛选，或等待管理员接入更多合约。"
                emptyTitle="暂无代码接入的合约"
                loading={loadState === 'loading'}
                locale={investmentTableLocale(
                    loadState,
                    <InvestmentTableFailure error={loadError} onRetry={() => void loadMarket()} state={loadState}/>,
                )}
                pagination={{
                    current: result?.page ?? page,
                    pageSize: result?.size ?? PAGE_SIZE,
                    total: result?.total ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
                rowKey="instrumentId"
                scroll={{x: 1100}}
                title="合约列表"
                toolbar={currentWorkspace && watchlists.length > 0 && (
                    <Select
                        aria-label="目标自选列表"
                        className="investment-inline-select"
                        onChange={setCurrentWatchlistId}
                        options={watchlists.map(({id, name}) => ({label: name, value: id}))}
                        value={currentWatchlistId}
                    />
                )}
            />
        </PageStack>
    )
}

export const Component = InvestmentMarketPage
