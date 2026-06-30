import {DollarOutlined} from '@ant-design/icons'
import {Button, Descriptions, Table} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {MoneyText} from './components/MoneyText'
import {monthlyBudget, weeklyBudget} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionBudgetSummaryResponse} from './nutritionTypes'

const budgetRows = [
    {id: 'weekly', name: '周预算', summary: weeklyBudget},
    {id: 'monthly', name: '月预算', summary: monthlyBudget},
]

type BudgetRow = (typeof budgetRows)[number]

const columns: ColumnsType<BudgetRow> = [
    {title: '周期', dataIndex: 'name'},
    {title: '开始', render: (_, record) => record.summary.periodStart},
    {title: '结束', render: (_, record) => record.summary.periodEnd},
    {title: '预算', render: (_, record) => <MoneyText value={record.summary.totalAmount}/>},
    {title: '实际', render: (_, record) => <MoneyText value={record.summary.totalActualAmount}/>},
    {title: '使用率', render: (_, record) => `${record.summary.usageRate}%`},
    {title: '人均成本', render: (_, record) => <MoneyText value={record.summary.perPersonCost}/>},
]

function BudgetPage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<DollarOutlined/>} type="primary">更新预算规则</Button>}
                description="查看家庭周/月成本、预算使用率和预算规则。"
                title="预算分析"
            />
            <NutritionPageGrid>
                <BudgetSummary title="周预算" value={weeklyBudget}/>
                <BudgetSummary title="月预算" value={monthlyBudget}/>
                <NutritionSection title="预算规则">
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="周规则">工作日控制在 620 元以内</Descriptions.Item>
                        <Descriptions.Item label="月规则">月度总预算 2600 元，超 80% 提醒</Descriptions.Item>
                        <Descriptions.Item label="人均成本">按确认成员与餐次分摊</Descriptions.Item>
                    </Descriptions>
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionSection title="周/月汇总">
                <Table<BudgetRow>
                    columns={columns}
                    dataSource={budgetRows}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 920}}
                    size="small"
                />
            </NutritionSection>
        </NutritionStack>
    )
}

function BudgetSummary({title, value}: { title: string; value: NutritionBudgetSummaryResponse }) {
    return (
        <NutritionSection title={title}>
            <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="预算"><MoneyText value={value.totalAmount}/></Descriptions.Item>
                <Descriptions.Item label="实际"><MoneyText value={value.totalActualAmount}/></Descriptions.Item>
                <Descriptions.Item label="使用率">{value.usageRate}%</Descriptions.Item>
                <Descriptions.Item label="人均成本"><MoneyText value={value.perPersonCost}/></Descriptions.Item>
            </Descriptions>
        </NutritionSection>
    )
}

export const Component = BudgetPage
