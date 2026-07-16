import {Alert, Card, Descriptions, Flex, Space, Table, Tag, Typography} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import {useParams} from 'react-router'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {
    getInvestmentInstrument,
    getInvestmentQuote,
    listInvestmentFundingRates,
    listInvestmentPositionTiers,
} from '../services/investmentMarketService'
import type {InvestmentDecimal, InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentFundingRateResponse,
    InvestmentInstrumentDetailResponse,
    InvestmentPositionTierResponse,
    InvestmentQuoteResponse,
} from '../types/investmentMarketTypes'
import {InvestmentKlinePanel} from './InvestmentKlinePanel'

const FUNDING_LOOKBACK_DAYS = 30

export default function InvestmentInstrumentPage() {
    const {instrumentId: instrumentIdParam} = useParams()
    const instrumentId = parseInstrumentId(instrumentIdParam)
    const [instrument, setInstrument] = useState<InvestmentInstrumentDetailResponse>()
    const [quote, setQuote] = useState<InvestmentQuoteResponse>()
    const [fundingRates, setFundingRates] = useState<InvestmentFundingRateResponse[]>([])
    const [positionTiers, setPositionTiers] = useState<InvestmentPositionTierResponse[]>([])
    const [fundingError, setFundingError] = useState<string>()
    const [tierError, setTierError] = useState<string>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>(instrumentId ? 'loading' : 'error')
    const [loadError, setLoadError] = useState<string | undefined>(instrumentId ? undefined : '无效的合约编号')
    const generationRef = useRef(0)

    const load = useCallback(async () => {
        const generation = ++generationRef.current
        setInstrument(undefined)
        setQuote(undefined)
        setFundingRates([])
        setPositionTiers([])
        setFundingError(undefined)
        setTierError(undefined)
        if (!instrumentId) {
            setLoadState('error')
            setLoadError('无效的合约编号')
            return
        }
        setLoadState('loading')
        setLoadError(undefined)
        try {
            const detail = await getInvestmentInstrument(instrumentId)
            if (generation !== generationRef.current) {
                return
            }
            const cutoff = detail.dataAsOf
            const hasQuote = detail.availableCapabilities.includes('LATEST_TICKER')
            const quoteRequest = hasQuote ? getInvestmentQuote(instrumentId) : Promise.resolve(undefined)
            void loadFunding(detail, generation)
            void loadTiers(detail, generation)
            const currentQuote = await quoteRequest
            if (generation !== generationRef.current) {
                return
            }
            setInstrument(detail)
            setQuote(currentQuote)
            setLoadState('ready')

            async function loadFunding(current: InvestmentInstrumentDetailResponse, requestGeneration: number) {
                if (!current.availableCapabilities.includes('FUNDING_RATE')) {
                    return
                }
                const to = new Date(cutoff)
                const from = new Date(to.getTime() - FUNDING_LOOKBACK_DAYS * 24 * 60 * 60 * 1_000)
                try {
                    const response = await listInvestmentFundingRates(
                        instrumentId as number,
                        from.toISOString(),
                        to.toISOString(),
                        cutoff,
                    )
                    if (requestGeneration === generationRef.current) {
                        setFundingRates(response.records)
                    }
                } catch (reason) {
                    if (requestGeneration === generationRef.current) {
                        setFundingError(errorMessage(reason, '资金费率暂不可用'))
                    }
                }
            }

            async function loadTiers(current: InvestmentInstrumentDetailResponse, requestGeneration: number) {
                if (!current.availableCapabilities.includes('POSITION_TIER')) {
                    return
                }
                try {
                    const response = await listInvestmentPositionTiers(instrumentId as number, cutoff)
                    if (requestGeneration === generationRef.current) {
                        setPositionTiers(response)
                    }
                } catch (reason) {
                    if (requestGeneration === generationRef.current) {
                        setTierError(errorMessage(reason, '仓位档位暂不可用'))
                    }
                }
            }
        } catch (reason) {
            if (generation !== generationRef.current) {
                return
            }
            setLoadState('error')
            setLoadError(errorMessage(reason, '合约详情加载失败'))
        }
    }, [instrumentId])

    useEffect(() => {
        void load()
        return () => {
            generationRef.current += 1
        }
    }, [load])

    return (
        <InvestmentAsyncState error={loadError} onRetry={() => void load()} state={loadState}>
            {instrument && (
                <Space direction="vertical" size={16} style={{width: '100%'}}>
                    <InstrumentHeader instrument={instrument}/>
                    <QuoteCard instrument={instrument} quote={quote}/>
                    <ContractSpecCard instrument={instrument}/>
                    {instrument.availablePriceTypes.length > 0 && instrument.availableIntervals.length > 0 ? (
                        <InvestmentKlinePanel
                            availableIntervals={instrument.availableIntervals}
                            availablePriceTypes={instrument.availablePriceTypes}
                            instrumentId={instrument.instrumentId}
                        />
                    ) : (
                        <Alert showIcon title="该合约尚未接入 K 线能力" type="info"/>
                    )}
                    <FundingRateCard
                        available={instrument.availableCapabilities.includes('FUNDING_RATE')}
                        error={fundingError}
                        fundingRates={fundingRates}
                    />
                    <PositionTierCard
                        available={instrument.availableCapabilities.includes('POSITION_TIER')}
                        error={tierError}
                        tiers={positionTiers}
                    />
                </Space>
            )}
        </InvestmentAsyncState>
    )
}

function InstrumentHeader({instrument}: {instrument: InvestmentInstrumentDetailResponse}) {
    return (
        <Flex align="flex-start" gap={12} justify="space-between" wrap>
            <div>
                <Typography.Title level={4}>{instrument.symbol}</Typography.Title>
                <Typography.Text type="secondary">
                    {instrument.venueCode} · {instrument.baseAsset}/{instrument.quoteAsset} · {instrument.contractType}
                </Typography.Text>
            </div>
            <Space wrap>
                <Tag>{instrument.status}</Tag>
                <Tag color={freshnessColor(instrument.freshness.status)}>{instrument.freshness.status}</Tag>
                <Typography.Text type="secondary">数据截止：{instrument.dataAsOf}</Typography.Text>
            </Space>
        </Flex>
    )
}

function QuoteCard({instrument, quote}: {
    instrument: InvestmentInstrumentDetailResponse
    quote?: InvestmentQuoteResponse
}) {
    if (!instrument.availableCapabilities.includes('LATEST_TICKER')) {
        return <Alert showIcon title="该合约尚未接入最新行情能力" type="info"/>
    }
    return (
        <Card title="最新行情">
            {quote && (
                <Descriptions column={{xs: 1, sm: 2, lg: 4}} size="small">
                    <Descriptions.Item label="最新价"><InvestmentDecimalText value={quote.lastPrice}/></Descriptions.Item>
                    <Descriptions.Item label="标记价"><InvestmentDecimalText value={quote.markPrice}/></Descriptions.Item>
                    <Descriptions.Item label="指数价"><InvestmentDecimalText value={quote.indexPrice}/></Descriptions.Item>
                    <Descriptions.Item label="24h 涨跌"><InvestmentDecimalText suffix="%" value={quote.change24h}/></Descriptions.Item>
                    <Descriptions.Item label="买一"><InvestmentDecimalText value={quote.bidPrice}/></Descriptions.Item>
                    <Descriptions.Item label="卖一"><InvestmentDecimalText value={quote.askPrice}/></Descriptions.Item>
                    <Descriptions.Item label="24h 最高"><InvestmentDecimalText value={quote.high24h}/></Descriptions.Item>
                    <Descriptions.Item label="24h 最低"><InvestmentDecimalText value={quote.low24h}/></Descriptions.Item>
                    <Descriptions.Item label="24h 基础量"><InvestmentDecimalText value={quote.baseVolume24h}/></Descriptions.Item>
                    <Descriptions.Item label="24h 成交额"><InvestmentDecimalText value={quote.quoteVolume24h}/></Descriptions.Item>
                    <Descriptions.Item label="资金费率"><InvestmentDecimalText value={quote.fundingRate}/></Descriptions.Item>
                    <Descriptions.Item label="持仓量"><InvestmentDecimalText value={quote.openInterest}/></Descriptions.Item>
                </Descriptions>
            )}
        </Card>
    )
}

function ContractSpecCard({instrument}: {instrument: InvestmentInstrumentDetailResponse}) {
    const spec = instrument.contractSpec
    if (!instrument.contractSpecAvailable || !spec) {
        return <Alert showIcon title="该合约尚无可用规格快照" type="info"/>
    }
    return (
        <Card title="合约规格">
            <Descriptions column={{xs: 1, sm: 2, lg: 4}} size="small">
                <Descriptions.Item label="价格精度">{spec.pricePrecision}</Descriptions.Item>
                <Descriptions.Item label="数量精度">{spec.quantityPrecision}</Descriptions.Item>
                <Descriptions.Item label="价格步长"><InvestmentDecimalText value={spec.priceEndStep}/></Descriptions.Item>
                <Descriptions.Item label="数量步长"><InvestmentDecimalText value={spec.quantityStep}/></Descriptions.Item>
                <Descriptions.Item label="合约乘数"><InvestmentDecimalText value={spec.contractMultiplier}/></Descriptions.Item>
                <Descriptions.Item label="最小下单量"><InvestmentDecimalText value={spec.minTradeQuantity}/></Descriptions.Item>
                <Descriptions.Item label="最小名义价值"><InvestmentDecimalText value={spec.minTradeNotional}/></Descriptions.Item>
                <Descriptions.Item label="杠杆范围">
                    <InvestmentDecimalText value={spec.minLeverage}/> - <InvestmentDecimalText value={spec.maxLeverage}/>
                </Descriptions.Item>
                <Descriptions.Item label="Maker 费率"><InvestmentDecimalText value={spec.makerFeeRate}/></Descriptions.Item>
                <Descriptions.Item label="Taker 费率"><InvestmentDecimalText value={spec.takerFeeRate}/></Descriptions.Item>
                <Descriptions.Item label="资金费间隔">{spec.fundingIntervalHours} 小时</Descriptions.Item>
                <Descriptions.Item label="规格版本">{spec.revision}</Descriptions.Item>
            </Descriptions>
        </Card>
    )
}

function FundingRateCard({available, error, fundingRates}: {
    available: boolean
    error?: string
    fundingRates: InvestmentFundingRateResponse[]
}) {
    return (
        <Card title="近 30 天资金费率">
            {!available && <Alert showIcon title="该合约尚未接入资金费率" type="info"/>}
            {error && <Alert description={error} showIcon title="资金费率独立加载失败" type="warning"/>}
            {available && !error && (
                <Table
                    columns={[
                        {title: '结算时间', dataIndex: 'fundingTime'},
                        {title: '资金费率', dataIndex: 'fundingRate', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '数据修订', dataIndex: 'revision'},
                    ]}
                    dataSource={fundingRates}
                    locale={{emptyText: '当前范围暂无资金费率'}}
                    pagination={{pageSize: 10, hideOnSinglePage: true}}
                    rowKey={(rate) => `${rate.fundingTime}:${rate.revision}`}
                    size="small"
                />
            )}
        </Card>
    )
}

function PositionTierCard({available, error, tiers}: {
    available: boolean
    error?: string
    tiers: InvestmentPositionTierResponse[]
}) {
    return (
        <Card title="仓位档位">
            {!available && <Alert showIcon title="该合约尚未接入仓位档位" type="info"/>}
            {error && <Alert description={error} showIcon title="仓位档位独立加载失败" type="warning"/>}
            {available && !error && (
                <Table
                    columns={[
                        {title: '档位', dataIndex: 'tierLevel'},
                        {title: '起始名义价值', dataIndex: 'startNotional', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '结束名义价值', dataIndex: 'endNotional', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '最高杠杆', dataIndex: 'maxLeverage', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '维持保证金率', dataIndex: 'maintenanceMarginRate', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                    ]}
                    dataSource={tiers}
                    locale={{emptyText: '当前没有仓位档位'}}
                    pagination={false}
                    rowKey={(tier) => `${tier.observedAt}:${tier.tierLevel}`}
                    size="small"
                />
            )}
        </Card>
    )
}

function parseInstrumentId(value?: string) {
    if (!value || !/^\d+$/.test(value)) {
        return undefined
    }
    const parsed = Number(value)
    return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

function freshnessColor(status: string) {
    if (status === 'FRESH') {
        return 'success'
    }
    if (status === 'STALE') {
        return 'warning'
    }
    return 'default'
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
