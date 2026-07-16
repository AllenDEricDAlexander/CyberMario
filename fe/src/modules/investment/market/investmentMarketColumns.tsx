import {Button, Space, Tag, Tooltip} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {Link} from 'react-router'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import type {InvestmentInstrumentSummaryResponse} from '../types/investmentMarketTypes'

type InvestmentMarketColumnOptions = {
    canAddToWatchlist: boolean
    watchlistDisabledReason?: string
    addingInstrumentId?: number | null
    onAddToWatchlist: (instrument: InvestmentInstrumentSummaryResponse) => void
}

export function investmentMarketColumns({
    canAddToWatchlist,
    watchlistDisabledReason,
    addingInstrumentId,
    onAddToWatchlist,
}: InvestmentMarketColumnOptions): ColumnsType<InvestmentInstrumentSummaryResponse> {
    return [
        {
            title: '合约',
            key: 'symbol',
            render: (_, record) => (
                <div>
                    <strong>{record.symbol}</strong>
                    <div>{record.venueCode} · {record.baseAsset}/{record.quoteAsset}</div>
                </div>
            ),
        },
        {
            title: '最新价',
            dataIndex: 'lastPrice',
            render: (value) => <InvestmentDecimalText value={value}/>,
        },
        {
            title: '标记价',
            dataIndex: 'markPrice',
            render: (value) => <InvestmentDecimalText value={value}/>,
        },
        {
            title: '24h 涨跌',
            dataIndex: 'change24h',
            render: (value) => <InvestmentDecimalText suffix="%" value={value}/>,
        },
        {
            title: '新鲜度',
            key: 'freshness',
            render: (_, record) => (
                <Tag color={freshnessColor(record.freshness.status)}>{record.freshness.status}</Tag>
            ),
        },
        {
            title: '数据能力',
            dataIndex: 'availableCapabilities',
            render: (capabilities: string[]) => (
                <Space size={[0, 4]} wrap>
                    {capabilities.map((capability) => <Tag key={capability}>{capability}</Tag>)}
                </Space>
            ),
        },
        {
            title: '操作',
            key: 'actions',
            render: (_, record) => (
                <Space>
                    <Link aria-label={`查看 ${record.symbol} 详情`} to={`/investment/instruments/${record.instrumentId}`}>
                        查看详情
                    </Link>
                    <Tooltip title={watchlistDisabledReason}>
                        <span>
                            <Button
                                aria-label={`加入自选 ${record.symbol}`}
                                disabled={!canAddToWatchlist}
                                loading={addingInstrumentId === record.instrumentId}
                                onClick={() => onAddToWatchlist(record)}
                                size="small"
                                title={watchlistDisabledReason}
                            >
                                加入自选
                            </Button>
                        </span>
                    </Tooltip>
                </Space>
            ),
        },
    ]
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
