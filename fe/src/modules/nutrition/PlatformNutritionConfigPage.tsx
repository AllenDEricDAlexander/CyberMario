import {ImportOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, Button, Form, Input, InputNumber, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {ImportJobPanel} from './components/ImportJobPanel'
import {familyRecipes, platformImportJob, standardFoods} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionRecipeResponse, NutritionStandardFoodResponse} from './nutritionTypes'

type NutritionTag = {
    id: number
    type: string
    name: string
    status: string
}

const tags: NutritionTag[] = [
    {id: 1, type: 'ALLERGY_TAG', name: '花生', status: 'ACTIVE'},
    {id: 2, type: 'DIET_GOAL', name: '控钠', status: 'ACTIVE'},
    {id: 3, type: 'DISLIKE_TAG', name: '香菜', status: 'ACTIVE'},
]

const foodColumns: ColumnsType<NutritionStandardFoodResponse> = [
    {title: '标准食材', dataIndex: 'nameCn'},
    {title: '英文名', dataIndex: 'nameEn', render: (_, record) => record.nameEn || '-'},
    {title: '分类', dataIndex: 'category', width: 130},
    {title: '状态', dataIndex: 'status', width: 110, render: (_, record) => <Tag color="success">{record.status}</Tag>},
]

const tagColumns: ColumnsType<NutritionTag> = [
    {title: '标签配置', dataIndex: 'name'},
    {title: '类型', dataIndex: 'type'},
    {title: '状态', dataIndex: 'status', width: 110, render: (_, record) => <Tag color="success">{record.status}</Tag>},
]

const recipeColumns: ColumnsType<NutritionRecipeResponse> = [
    {title: '公共菜谱', dataIndex: 'name'},
    {title: '分类', dataIndex: 'category'},
    {title: '份数', dataIndex: 'servingCount', width: 90},
    {title: '来源', dataIndex: 'sourceType', width: 150},
]

function PlatformNutritionConfigPage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<PlusOutlined/>} type="primary">新增标准食材</Button>}
                description="仅平台管理员可维护标准食材、标签、公共菜谱和平台导入任务。"
                title="营养平台"
            />
            <Alert showIcon title="仅平台管理员：本页配置会影响所有家庭的 AI 菜单、风险检查和导入模板。" type="warning"/>
            <NutritionPageGrid>
                <NutritionSection title="标准食材">
                    <Table<NutritionStandardFoodResponse>
                        columns={foodColumns}
                        dataSource={standardFoods}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection title="标签配置">
                    <Table<NutritionTag>
                        columns={tagColumns}
                        dataSource={tags}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionPageGrid>
                <NutritionSection title="公共菜谱">
                    <Table<NutritionRecipeResponse>
                        columns={recipeColumns}
                        dataSource={familyRecipes.filter((recipe) => recipe.sourceType === 'PLATFORM_PUBLIC')}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection extra={<Button disabled icon={<ImportOutlined/>} size="small">创建导入任务</Button>} title="导入任务">
                    <Form layout="vertical">
                        <Form.Item label="导入类型" name="importType">
                            <Select options={[{label: '标准食材', value: 'STANDARD_FOOD'}, {label: '公共菜谱', value: 'PUBLIC_RECIPE'}, {label: '标签配置', value: 'HEALTH_TAG'}]}/>
                        </Form.Item>
                        <Form.Item label="文件名" name="fileName">
                            <Input placeholder="platform-nutrition.csv"/>
                        </Form.Item>
                        <Form.Item label="总行数" name="totalRows">
                            <InputNumber min={1} style={{width: '100%'}}/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
            </NutritionPageGrid>
            <ImportJobPanel job={{...platformImportJob, status: 'UPLOADED'}}/>
        </NutritionStack>
    )
}

export const Component = PlatformNutritionConfigPage
