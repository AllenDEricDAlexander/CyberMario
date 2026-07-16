import {Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import type {InvestmentPosition} from '../types/investmentPortfolioTypes'

export function PortfolioPositionsTable({positions, loading}: {
    positions: InvestmentPosition[]
    loading?: boolean
}) {
    return (
        <Table
            columns={columns}
            dataSource={positions}
            loading={loading}
            locale={{emptyText: '当前账户暂无持仓'}}
            pagination={false}
            rowKey="id"
            scroll={{x: 1_400}}
            size="small"
        />
    )
}

const columns: ColumnsType<InvestmentPosition> = [
    {title: '合约 ID', dataIndex: 'instrumentId'},
    {title: '方向', dataIndex: 'positionSide', render: (value: string) => <Tag color={value === 'LONG' ? 'green' : 'red'}>{value}</Tag>},
    {title: '数量', dataIndex: 'quantity', render: decimal},
    {title: '开仓价', dataIndex: 'entryPrice', render: decimal},
    {title: '标记价', dataIndex: 'markPrice', render: decimal},
    {title: '杠杆', dataIndex: 'leverage', render: decimal},
    {title: '隔离保证金', dataIndex: 'isolatedMargin', render: decimal},
    {title: '维持保证金', dataIndex: 'maintenanceMargin', render: decimal},
    {title: '强平价', dataIndex: 'liquidationPrice', render: decimal},
    {title: '未实现损益', dataIndex: 'unrealizedPnl', render: decimal},
    {title: '已实现损益', dataIndex: 'realizedPnl', render: decimal},
    {title: '资金费', dataIndex: 'fundingPnl', render: decimal},
    {title: '最后保证金检查', dataIndex: 'lastMarginCheckAt'},
]

function decimal(value: string | null) {
    return <InvestmentDecimalText value={value}/>
}
