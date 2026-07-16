import {Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import type {InvestmentLedgerPage} from '../types/investmentPortfolioTypes'

export function PortfolioLedgerTable({page, loading, onPageChange}: {
    page?: InvestmentLedgerPage
    loading?: boolean
    onPageChange: (page: number) => void
}) {
    const columns: ColumnsType<InvestmentLedgerPage['records'][number]> = [
        {title: '序号', dataIndex: 'sequenceNo'},
        {title: '事件', dataIndex: 'eventType', render: (value: string) => <Tag>{value}</Tag>},
        {title: '金额', dataIndex: 'amount', render: decimal},
        {title: '余额', dataIndex: 'balanceAfter', render: decimal},
        {title: '合约 ID', dataIndex: 'instrumentId', render: (value: number | null) => value ?? '-'},
        {title: '引用', key: 'reference', render: (_, row) => `${row.referenceType} / ${row.referenceId}`},
        {title: '发生时间', dataIndex: 'occurredAt'},
    ]
    return (
        <Table
            columns={columns}
            dataSource={page?.records ?? []}
            loading={loading}
            locale={{emptyText: '当前账户暂无资金流水'}}
            pagination={{
                current: page?.page ?? 1,
                pageSize: page?.size ?? 50,
                total: page?.total ?? 0,
                showSizeChanger: false,
                onChange: onPageChange,
            }}
            rowKey="id"
            scroll={{x: 900}}
            size="small"
        />
    )
}

function decimal(value: string) {
    return <InvestmentDecimalText value={value}/>
}
