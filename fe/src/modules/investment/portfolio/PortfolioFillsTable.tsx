import {Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import type {InvestmentFillMarkerPage} from '../types/investmentPortfolioTypes'

export function PortfolioFillsTable({page, loading, onPageChange}: {
    page?: InvestmentFillMarkerPage
    loading?: boolean
    onPageChange: (page: number) => void
}) {
    const columns: ColumnsType<InvestmentFillMarkerPage['records'][number]> = [
        {title: '成交 ID', dataIndex: 'id', render: (value: number) => `#${value}`},
        {title: '合约 ID', dataIndex: 'instrumentId'},
        {title: '事件', dataIndex: 'eventType', render: (value: string, row) => <Tag color={row.liquidation ? 'error' : 'blue'}>{value}</Tag>},
        {title: '动作', dataIndex: 'actionType'},
        {title: '方向', dataIndex: 'side'},
        {title: '来源', dataIndex: 'orderOrigin'},
        {title: '价格', dataIndex: 'price', render: decimal},
        {title: '数量', dataIndex: 'quantity', render: decimal},
        {title: '事件时间', dataIndex: 'eventTime'},
        {title: 'K 线时间', dataIndex: 'marketBarOpenTime', render: (value: string | null) => value ?? '-'},
    ]
    return (
        <Table
            columns={columns}
            dataSource={page?.records ?? []}
            loading={loading}
            locale={{emptyText: '请选择合约并查询最近成交'}}
            pagination={{
                current: page?.page ?? 1,
                pageSize: page?.size ?? 100,
                total: page?.total ?? 0,
                showSizeChanger: false,
                onChange: onPageChange,
            }}
            rowKey="id"
            scroll={{x: 1_100}}
            size="small"
        />
    )
}

function decimal(value: string) {
    return <InvestmentDecimalText value={value}/>
}
