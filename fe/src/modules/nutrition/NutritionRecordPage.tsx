import {FileTextOutlined, PlusOutlined} from '@ant-design/icons'
import {Button, Descriptions, Form, Input, InputNumber, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {RiskTag} from './components/RiskTag'
import {nutritionMembers, weeklyBudget} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionRecordResponse} from './nutritionTypes'

const records: NutritionRecordResponse[] = [
    {
        id: 901,
        familyId: 1,
        memberProfileId: 11,
        mealPlanId: 501,
        mealConfirmationId: 601,
        recordDate: '2026-07-02',
        mealType: 'DINNER',
        sourceType: 'MEAL_PLAN',
        nutrients: {calories: 620, protein: 42, fat: 18, carbs: 68, sugar: 9, sodium: 760, fiber: 7, cholesterol: 88},
        riskTags: 'MEDIUM',
        calculationSnapshot: null,
        metadataJson: null,
        createdAt: '2026-07-01T00:00:00Z',
        updatedAt: '2026-07-01T00:00:00Z',
    },
]

const columns: ColumnsType<NutritionRecordResponse> = [
    {title: '成员', dataIndex: 'memberProfileId', width: 100},
    {title: '日期', dataIndex: 'recordDate', width: 120},
    {title: '餐次', dataIndex: 'mealType', width: 110},
    {title: '热量', render: (_, record) => record.nutrients.calories},
    {title: '蛋白', render: (_, record) => record.nutrients.protein},
    {title: '风险', dataIndex: 'riskTags', width: 120, render: () => <RiskTag value="MEDIUM"/>},
    {title: '操作', width: 120, render: () => <Button disabled size="small">调整记录</Button>},
]

function NutritionRecordPage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<PlusOutlined/>} type="primary">加餐登记</Button>}
                description="查看每日摄入、发起营养调整，并生成家庭报告。"
                title="营养记录"
            />
            <NutritionSection title="每日摄入">
                <Table<NutritionRecordResponse>
                    columns={columns}
                    dataSource={records}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 860}}
                    size="small"
                />
            </NutritionSection>
            <NutritionPageGrid>
                <NutritionSection title="调整记录">
                    <Form layout="vertical">
                        <Form.Item label="成员档案" name="memberProfileId">
                            <Select options={nutritionMembers.map((member) => ({label: member.nickname, value: member.id}))}/>
                        </Form.Item>
                        <Form.Item label="调整原因" name="reason">
                            <Input placeholder="实际食用半份主食"/>
                        </Form.Item>
                        <Form.Item label="调整热量" name="calories">
                            <InputNumber style={{width: '100%'}}/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
                <NutritionSection title="加餐登记">
                    <Form layout="vertical">
                        <Form.Item label="食物名称" name="foodName">
                            <Input placeholder="酸奶"/>
                        </Form.Item>
                        <Form.Item label="数量" name="amount">
                            <InputNumber min={0} style={{width: '100%'}}/>
                        </Form.Item>
                        <Form.Item label="餐次" name="mealType">
                            <Select options={[{label: '加餐', value: 'SNACK'}, {label: '晚餐', value: 'DINNER'}]}/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
                <NutritionSection title="家庭报告">
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="周成本">{weeklyBudget.totalActualAmount}</Descriptions.Item>
                        <Descriptions.Item label="风险概览"><Tag color="warning">中风险 1</Tag></Descriptions.Item>
                        <Descriptions.Item label="导出"><Button disabled icon={<FileTextOutlined/>} size="small">生成家庭报告</Button></Descriptions.Item>
                    </Descriptions>
                </NutritionSection>
            </NutritionPageGrid>
        </NutritionStack>
    )
}

export const Component = NutritionRecordPage
