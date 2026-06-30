import {ShoppingCartOutlined} from '@ant-design/icons'
import {Button, Descriptions, Table} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {MoneyText} from './components/MoneyText'
import {mealPlans, mealSummary} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'

type DishSummaryRow = (typeof mealSummary.dishes)[number]

const columns: ColumnsType<DishSummaryRow> = [
    {title: '菜品', dataIndex: 'dishName'},
    {title: '餐次', dataIndex: 'mealType', width: 120},
    {title: '菜单份数', dataIndex: 'servingCount', width: 120},
    {title: '确认份数', dataIndex: 'confirmedServingTotal', width: 120},
]

function MealSummaryPage() {
    const mealPlan = mealPlans[0]

    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<ShoppingCartOutlined/>} type="primary">生成采购清单</Button>}
                description="汇总确认后的菜品份数，用于生成采购清单。"
                title="餐食汇总"
            />
            <NutritionPageGrid>
                <NutritionSection title="菜单信息">
                    <Descriptions column={1} bordered size="small">
                        <Descriptions.Item label="菜单">{mealPlan.title}</Descriptions.Item>
                        <Descriptions.Item label="确认人数">{mealSummary.confirmedMemberCount}</Descriptions.Item>
                        <Descriptions.Item label="预估成本"><MoneyText value={mealPlan.estimatedCost}/></Descriptions.Item>
                    </Descriptions>
                </NutritionSection>
                <NutritionSection title="生成采购清单">
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="来源">餐食汇总</Descriptions.Item>
                        <Descriptions.Item label="动作">生成采购清单</Descriptions.Item>
                    </Descriptions>
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionSection title="菜品份数汇总">
                <Table<DishSummaryRow>
                    columns={columns}
                    dataSource={mealSummary.dishes}
                    pagination={false}
                    rowKey="itemId"
                    size="small"
                />
            </NutritionSection>
        </NutritionStack>
    )
}

export const Component = MealSummaryPage
