import {Button, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {InvestmentPaperOrderPage} from '../types/investmentPortfolioTypes'

export function PortfolioOrdersTable({page, loading, cancellingId, onPageChange, onCancel}: {
    page?: InvestmentPaperOrderPage
    loading?: boolean
    cancellingId?: number
    onPageChange: (page: number) => void
    onCancel: (orderId: number) => void
}) {
    const columns: ColumnsType<InvestmentPaperOrderPage['records'][number]> = [
        {title: '委托 ID', dataIndex: 'orderId', render: (value: number) => `#${value}`},
        {title: '状态', dataIndex: 'status', render: (value: string) => <Tag>{value}</Tag>},
        {title: '提交时间', dataIndex: 'submittedAt'},
        {title: '撮合时间', dataIndex: 'matchedAt', render: (value: string | null) => value ?? '-'},
        {
            title: '操作', key: 'action', render: (_, order) => order.status === 'PENDING_MATCH'
                ? <Button danger loading={cancellingId === order.orderId}
                    onClick={() => onCancel(order.orderId)} size="small">取消</Button>
                : '-',
        },
    ]
    return (
        <Table
            columns={columns}
            dataSource={page?.records ?? []}
            loading={loading}
            locale={{emptyText: '当前账户暂无委托'}}
            pagination={{
                current: page?.page ?? 1,
                pageSize: page?.size ?? 20,
                total: page?.total ?? 0,
                showSizeChanger: false,
                onChange: onPageChange,
            }}
            rowKey="orderId"
            scroll={{x: 800}}
            size="small"
        />
    )
}
