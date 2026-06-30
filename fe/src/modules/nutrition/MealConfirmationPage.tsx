import {CheckOutlined} from '@ant-design/icons'
import {Alert, Button, Form, Input, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {RiskTag} from './components/RiskTag'
import {mealConfirmations, mealPlans, nutritionMembers} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionMealConfirmationResponse, NutritionMealPlanItemResponse} from './nutritionTypes'

const menuColumns: ColumnsType<NutritionMealPlanItemResponse> = [
    {title: '餐次', dataIndex: 'mealType', width: 110},
    {title: '当前菜单', dataIndex: 'dishName'},
    {title: '建议份数', dataIndex: 'servingCount', width: 120},
]

const confirmationColumns: ColumnsType<NutritionMealConfirmationResponse> = [
    {title: '成员档案', dataIndex: 'memberProfileId', width: 120},
    {title: '状态', dataIndex: 'confirmationStatus', width: 130, render: (_, record) => <Tag color="success">{record.confirmationStatus}</Tag>},
    {title: '风险确认', dataIndex: 'riskConfirmed', width: 120, render: (_, record) => record.riskConfirmed ? '已确认' : '未确认'},
    {title: '备注', dataIndex: 'remark', render: (_, record) => record.remark || '-'},
]

function MealConfirmationPage() {
    const mealPlan = mealPlans[0]

    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<CheckOutlined/>} type="primary">提交确认</Button>}
                description="确认成员是否在家用餐、餐次和份量；中风险需确认，高风险阻断提交。"
                title="用餐确认"
            />
            <NutritionPageGrid>
                <NutritionSection title="当前菜单">
                    <Table<NutritionMealPlanItemResponse>
                        columns={menuColumns}
                        dataSource={mealPlan.items}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection title="确认控制">
                    <Form layout="vertical">
                        <Form.Item label="成员档案" name="memberProfileId">
                            <Select options={nutritionMembers.map((member) => ({label: member.nickname, value: member.id}))}/>
                        </Form.Item>
                        <Form.Item label="份量备注" name="remark">
                            <Input placeholder="少盐、半份或不吃主食"/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionPageGrid>
                <Alert showIcon title="中风险需确认：控钠目标接近上限，请填写确认说明。" type="warning"/>
                <Alert showIcon title="高风险阻断：命中花生过敏时不允许提交确认。" type="error"/>
            </NutritionPageGrid>
            <NutritionSection title="确认记录">
                <div style={{marginBottom: 12}}>
                    <RiskTag value="MEDIUM"/>
                    <span style={{marginLeft: 8}}>中风险需确认</span>
                    <span style={{marginLeft: 16}}>高风险阻断</span>
                </div>
                <Table<NutritionMealConfirmationResponse>
                    columns={confirmationColumns}
                    dataSource={mealConfirmations}
                    pagination={false}
                    rowKey="id"
                    size="small"
                />
            </NutritionSection>
        </NutritionStack>
    )
}

export const Component = MealConfirmationPage
