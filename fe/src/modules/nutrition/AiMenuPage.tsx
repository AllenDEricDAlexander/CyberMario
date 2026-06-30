import {CloudUploadOutlined, RobotOutlined} from '@ant-design/icons'
import {Alert, Button, Descriptions, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {MoneyText} from './components/MoneyText'
import {RiskTag} from './components/RiskTag'
import {aiJobs, aiRecommendations, mealPlans} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionAiRecommendationJobResponse, NutritionMealPlanItemResponse} from './nutritionTypes'

const jobColumns: ColumnsType<NutritionAiRecommendationJobResponse> = [
    {title: '任务 ID', dataIndex: 'id', width: 100},
    {title: '计划日期', dataIndex: 'plannedDate', width: 120},
    {title: '触发方式', dataIndex: 'triggerType', width: 120},
    {title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color="success">{value}</Tag>},
    {title: '推荐 ID', dataIndex: 'recommendationId', width: 120},
]

const adjustedColumns: ColumnsType<NutritionMealPlanItemResponse> = [
    {title: '餐次', dataIndex: 'mealType', width: 110},
    {title: '厨师调整菜单', dataIndex: 'dishName'},
    {title: '份数', dataIndex: 'servingCount', width: 100},
    {title: '排序', dataIndex: 'sortOrder', width: 90},
]

function AiMenuPage() {
    const recommendation = aiRecommendations[0]
    const mealPlan = mealPlans[0]

    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<RobotOutlined/>} type="primary">生成 AI 建议</Button>}
                description="审核 AI 建议、风险检查和厨师调整后的可发布菜单。"
                title="AI 菜单"
            />
            <Table<NutritionAiRecommendationJobResponse>
                columns={jobColumns}
                dataSource={aiJobs}
                pagination={false}
                rowKey="id"
                size="small"
            />
            <NutritionPageGrid>
                <NutritionSection title="AI 建议">
                    <Descriptions column={1} bordered size="small">
                        <Descriptions.Item label="标题">{recommendation.title}</Descriptions.Item>
                        <Descriptions.Item label="推荐理由">{recommendation.reason}</Descriptions.Item>
                        <Descriptions.Item label="成本预估"><MoneyText value={recommendation.costEstimate}/></Descriptions.Item>
                        <Descriptions.Item label="餐次">{recommendation.mealTypes.join(', ')}</Descriptions.Item>
                    </Descriptions>
                </NutritionSection>
                <NutritionSection title="风险检查">
                    <Alert
                        showIcon
                        title="中风险：儿童花生过敏已避开，控钠目标需要发布前确认。"
                        type="warning"
                    />
                    <div style={{marginTop: 12}}>
                        <RiskTag value="MEDIUM"/>
                    </div>
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionSection
                extra={<Button disabled icon={<CloudUploadOutlined/>} type="primary">发布菜单</Button>}
                title="厨师调整菜单"
            >
                <Table<NutritionMealPlanItemResponse>
                    columns={adjustedColumns}
                    dataSource={mealPlan.items}
                    pagination={false}
                    rowKey="id"
                    size="small"
                />
            </NutritionSection>
        </NutritionStack>
    )
}

export const Component = AiMenuPage
