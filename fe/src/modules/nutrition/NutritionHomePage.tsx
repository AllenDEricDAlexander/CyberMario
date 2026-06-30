import {CheckCircleOutlined, DollarOutlined, RobotOutlined} from '@ant-design/icons'
import {Button, Card, Statistic, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {MoneyText} from './components/MoneyText'
import {RiskTag} from './components/RiskTag'
import {mealPlans, weeklyBudget} from './nutritionPageData'
import {NutritionPageGrid, NutritionStack} from './NutritionPageLayout'

type PendingAction = {
    id: number
    title: string
    planDate: string
    status: string
    risk: 'LOW' | 'MEDIUM' | 'HIGH'
    pendingMembers: number
    estimatedCost: number
}

const pendingActions: PendingAction[] = mealPlans.map((plan) => ({
    id: plan.id,
    title: plan.title,
    planDate: plan.planDate,
    status: plan.status,
    risk: 'MEDIUM',
    pendingMembers: 2,
    estimatedCost: Number(plan.estimatedCost ?? 0),
}))

const columns: ColumnsType<PendingAction> = [
    {title: '菜单', dataIndex: 'title', width: 180},
    {title: '日期', dataIndex: 'planDate', width: 120},
    {title: '状态', dataIndex: 'status', width: 140, render: (_, record) => <Tag color="processing">{record.status}</Tag>},
    {title: '风险', dataIndex: 'risk', width: 120, render: (_, record) => <RiskTag value={record.risk}/>},
    {title: '待确认成员', dataIndex: 'pendingMembers', width: 130},
    {title: '预估成本', dataIndex: 'estimatedCost', width: 130, render: (_, record) => <MoneyText value={record.estimatedCost}/>},
    {title: '操作', width: 120, render: () => <Button disabled size="small">查看确认</Button>},
]

function NutritionHomePage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<RobotOutlined/>} type="primary">生成今日建议</Button>}
                description="跟踪家庭菜单、确认进度和预算使用，优先处理 AI 菜单待办。"
                title="营养首页"
            />
            <NutritionPageGrid>
                <Card>
                    <Statistic prefix={<RobotOutlined/>} title="待处理 AI 菜单" value={pendingActions.length}/>
                </Card>
                <Card>
                    <Statistic suffix="%" prefix={<DollarOutlined/>} title="预算使用率" value={weeklyBudget.usageRate}/>
                </Card>
                <Card>
                    <Statistic prefix={<CheckCircleOutlined/>} title="待确认成员" value={2}/>
                </Card>
            </NutritionPageGrid>
            <Table<PendingAction>
                columns={columns}
                dataSource={pendingActions}
                pagination={false}
                rowKey="id"
                scroll={{x: 920}}
                size="small"
            />
        </NutritionStack>
    )
}

export const Component = NutritionHomePage
