import {SaveOutlined} from '@ant-design/icons'
import {Button, Form, Input, InputNumber, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {MoneyText} from './components/MoneyText'
import {shoppingList} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionShoppingListItemResponse} from './nutritionTypes'

const columns: ColumnsType<NutritionShoppingListItemResponse> = [
    {title: '采购项', dataIndex: 'rawFoodName'},
    {title: '分类', dataIndex: 'category', width: 120},
    {title: '计划数量', width: 120, render: (_, record) => `${record.plannedAmount ?? '-'}${record.plannedUnit ?? ''}`},
    {title: '已勾选', dataIndex: 'itemStatus', width: 110, render: (_, record) => record.itemStatus === 'CHECKED' ? <Tag color="success">已勾选</Tag> : <Tag>待采购</Tag>},
    {title: '渠道', dataIndex: 'channel', width: 110, render: (_, record) => record.channel || '-'},
    {title: '总价', dataIndex: 'totalPrice', width: 110, render: (_, record) => <MoneyText value={record.totalPrice}/>},
]

function ShoppingListPage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<SaveOutlined/>} type="primary">记录价格</Button>}
                description="跟踪采购项勾选状态，并沉淀渠道、规格和价格明细。"
                title="采购清单"
            />
            <NutritionSection title="采购项">
                <Table<NutritionShoppingListItemResponse>
                    columns={columns}
                    dataSource={shoppingList.items}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 840}}
                    size="small"
                />
            </NutritionSection>
            <NutritionPageGrid>
                <NutritionSection title="渠道价格明细">
                    <Form layout="vertical">
                        <Form.Item label="采购渠道" name="channel">
                            <Select options={[{label: '盒马', value: '盒马'}, {label: '菜市场', value: '菜市场'}, {label: '山姆', value: '山姆'}]}/>
                        </Form.Item>
                        <Form.Item label="品牌" name="brand">
                            <Input placeholder="日日鲜"/>
                        </Form.Item>
                        <Form.Item label="规格数量" name="specAmount">
                            <InputNumber min={0} style={{width: '100%'}}/>
                        </Form.Item>
                        <Form.Item label="总价" name="totalPrice">
                            <InputNumber min={0} precision={2} style={{width: '100%'}}/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
                <NutritionSection title="价格记录表">
                    <Table<NutritionShoppingListItemResponse>
                        columns={[
                            {title: '食材', dataIndex: 'rawFoodName'},
                            {title: '渠道', dataIndex: 'channel', render: (_, record) => record.channel || '-'},
                            {title: '品牌', dataIndex: 'brand', render: (_, record) => record.brand || '-'},
                            {title: '归一单价', dataIndex: 'normalizedUnitPrice', render: (_, record) => <MoneyText value={record.normalizedUnitPrice}/>},
                        ]}
                        dataSource={shoppingList.items.filter((item) => item.channel)}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
            </NutritionPageGrid>
        </NutritionStack>
    )
}

export const Component = ShoppingListPage
