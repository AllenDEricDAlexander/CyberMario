import {ImportOutlined, PlusOutlined} from '@ant-design/icons'
import {Button, Form, Input, InputNumber, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {PageToolbar} from '../../components/PageToolbar'
import {ImportJobPanel} from './components/ImportJobPanel'
import {familyRecipes, platformImportJob, standardFoods} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionRecipeIngredientResponse, NutritionRecipeResponse, NutritionStandardFoodResponse} from './nutritionTypes'

const foodColumns: ColumnsType<NutritionStandardFoodResponse> = [
    {title: '公开标准食材', dataIndex: 'nameCn'},
    {title: '分类', dataIndex: 'category', width: 140},
    {title: '热量/100g', dataIndex: 'caloriesPer100g', width: 120},
    {title: '蛋白/100g', dataIndex: 'proteinPer100g', width: 120},
]

const recipeColumns: ColumnsType<NutritionRecipeResponse> = [
    {title: '家庭菜谱', dataIndex: 'name'},
    {title: '来源', dataIndex: 'sourceType', width: 150, render: (value) => <Tag>{value}</Tag>},
    {title: '分类', dataIndex: 'category', width: 140},
    {title: '份数', dataIndex: 'servingCount', width: 90},
    {title: '操作', width: 120, render: () => <Button disabled size="small">编辑食材</Button>},
]

const ingredientRows: NutritionRecipeIngredientResponse[] = familyRecipes.flatMap((recipe) => recipe.ingredients)

const ingredientColumns: ColumnsType<NutritionRecipeIngredientResponse> = [
    {title: '原始食材', dataIndex: 'rawFoodName'},
    {title: '数量', dataIndex: 'amount', width: 100},
    {title: '单位', dataIndex: 'unit', width: 90},
    {title: '食材映射', dataIndex: 'mappingStatus', width: 140, render: (value) => <Tag color={value === 'MAPPED' ? 'success' : 'warning'}>{value}</Tag>},
]

function RecipeLibraryPage() {
    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button disabled icon={<PlusOutlined/>} type="primary">新建菜谱</Button>}
                description="维护公共标准食材、家庭菜谱、食材映射和批量导入校验。"
                title="家庭菜谱"
            />
            <NutritionPageGrid>
                <NutritionSection title="公开标准食材">
                    <Table<NutritionStandardFoodResponse>
                        columns={foodColumns}
                        dataSource={standardFoods}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection title="家庭菜谱">
                    <Table<NutritionRecipeResponse>
                        columns={recipeColumns}
                        dataSource={familyRecipes}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionPageGrid>
                <NutritionSection title="食材映射">
                    <Table<NutritionRecipeIngredientResponse>
                        columns={ingredientColumns}
                        dataSource={ingredientRows}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection extra={<Button disabled icon={<ImportOutlined/>} size="small">提交导入</Button>} title="导入面板">
                    <Form layout="vertical">
                        <Form.Item label="导入类型" name="importType">
                            <Select options={[{label: '家庭菜谱', value: 'PRIVATE_RECIPE'}, {label: '食材映射', value: 'FAMILY_INGREDIENT_MAPPING'}]}/>
                        </Form.Item>
                        <Form.Item label="文件名" name="fileName">
                            <Input placeholder="recipes.csv"/>
                        </Form.Item>
                        <Form.Item label="预估行数" name="rows">
                            <InputNumber min={1} style={{width: '100%'}}/>
                        </Form.Item>
                    </Form>
                </NutritionSection>
            </NutritionPageGrid>
            <ImportJobPanel job={{...platformImportJob, status: 'UPLOADED'}}/>
        </NutritionStack>
    )
}

export const Component = RecipeLibraryPage
