import {ImportOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Drawer, Form, Input, InputNumber, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ImportJobPanel} from './components/ImportJobPanel'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    confirmNutritionImportJob,
    createNutritionImportJob,
    createNutritionPlatformHealthTag,
    createNutritionPlatformRecipe,
    createNutritionStandardFood,
    deactivateNutritionPlatformHealthTag,
    deactivateNutritionPlatformRecipe,
    deactivateNutritionStandardFood,
    listNutritionPlatformHealthTags,
    listNutritionPlatformRecipes,
    listNutritionStandardFoods,
    updateNutritionPlatformHealthTag,
    updateNutritionPlatformRecipe,
    updateNutritionStandardFood,
} from './nutritionService'
import type {
    NutritionCreateImportJobRequest,
    NutritionCreateRecipeRequest,
    NutritionCreateStandardFoodRequest,
    NutritionHealthTagResponse,
    NutritionImportJobResponse,
    NutritionImportType,
    NutritionLoadState,
    NutritionRecipeResponse,
    NutritionStandardFoodResponse,
    NutritionUpsertHealthTagRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'

type EditorMode = 'food' | 'tag' | 'recipe'
type NutritionEditorFormValues = Partial<
    NutritionCreateStandardFoodRequest & NutritionUpsertHealthTagRequest & NutritionCreateRecipeRequest
>

function PlatformNutritionConfigPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [editorForm] = Form.useForm<NutritionEditorFormValues>()
    const [importForm] = Form.useForm<NutritionCreateImportJobRequest>()
    const [foods, setFoods] = useState<NutritionStandardFoodResponse[]>([])
    const [tags, setTags] = useState<NutritionHealthTagResponse[]>([])
    const [recipes, setRecipes] = useState<NutritionRecipeResponse[]>([])
    const [editorMode, setEditorMode] = useState<EditorMode>()
    const [editingRecord, setEditingRecord] = useState<NutritionStandardFoodResponse | NutritionHealthTagResponse | NutritionRecipeResponse>()
    const [importOpen, setImportOpen] = useState(false)
    const [importJob, setImportJob] = useState<NutritionImportJobResponse>()
    const [state, setState] = useState<NutritionLoadState>('loading')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const [confirming, setConfirming] = useState(false)
    const canMutate = canUseRbacButton(auth, 'btn:nutrition:platform:manage')
        || auth.hasPermission(nutritionApiCodes.platform)

    const loadData = useCallback(async () => {
        setState('loading')
        try {
            const [foodRows, tagRows, recipeRows] = await Promise.all([
                listNutritionStandardFoods(),
                listNutritionPlatformHealthTags(),
                listNutritionPlatformRecipes(),
            ])
            setFoods(foodRows)
            setTags(tagRows)
            setRecipes(recipeRows)
            setState(foodRows.length || tagRows.length || recipeRows.length ? 'ready' : 'empty')
            setError(undefined)
        } catch (reason) {
            const failure = nutritionLoadFailure(reason)
            setState(failure.state)
            setError(failure.error)
        }
    }, [])

    useEffect(() => {
        void loadData()
    }, [loadData])

    function openEditor(mode: EditorMode, record?: typeof editingRecord) {
        setEditorMode(mode)
        setEditingRecord(record)
        editorForm.resetFields()
        if (record && mode === 'food' && 'nameCn' in record) {
            editorForm.setFieldsValue(record)
        } else if (record && mode === 'tag' && 'tagCode' in record) {
            editorForm.setFieldsValue({
                tagType: record.tagType,
                tagCode: record.tagCode,
                name: record.name,
                description: record.description ?? undefined,
                sortOrder: record.sortOrder,
            })
        } else if (record && mode === 'recipe' && 'ingredients' in record) {
            editorForm.setFieldsValue({
                name: record.name,
                category: record.category ?? undefined,
                description: record.description ?? undefined,
                servingCount: record.servingCount,
                cookingMinutes: record.cookingMinutes ?? undefined,
                difficultyLevel: record.difficultyLevel ?? undefined,
                suitableTags: record.suitableTags,
                allergenTags: record.allergenTags,
                ingredients: record.ingredients.map((ingredient) => ({
                    standardFoodId: ingredient.standardFoodId ?? undefined,
                    foodName: ingredient.rawFoodName,
                    amount: ingredient.amount,
                    unit: ingredient.unit,
                    gramsPerUnit: ingredient.gramsPerUnit ?? undefined,
                    optional: ingredient.optional,
                })),
                steps: record.steps.map((step) => ({
                    stepNo: step.stepNo,
                    title: step.title ?? undefined,
                    instruction: step.instruction,
                })),
            })
        } else if (mode === 'food') {
            editorForm.setFieldsValue({dataQuality: 'CURATED', status: 'ACTIVE'})
        } else if (mode === 'tag') {
            editorForm.setFieldsValue({sortOrder: 0})
        } else {
            editorForm.setFieldsValue({servingCount: 1, ingredients: [{unit: 'g', optional: false}], steps: []})
        }
    }

    async function saveEditor(values: NutritionEditorFormValues) {
        if (!editorMode) return
        await mutate(async () => {
            if (editorMode === 'food') {
                const request = values as NutritionCreateStandardFoodRequest
                if (editingRecord) await updateNutritionStandardFood(editingRecord.id, request)
                else await createNutritionStandardFood(request)
            } else if (editorMode === 'tag') {
                const request = values as NutritionUpsertHealthTagRequest
                if (editingRecord) await updateNutritionPlatformHealthTag(editingRecord.id, request)
                else await createNutritionPlatformHealthTag(request)
            } else {
                const request = values as NutritionCreateRecipeRequest
                if (editingRecord) await updateNutritionPlatformRecipe(editingRecord.id, request)
                else await createNutritionPlatformRecipe(request)
            }
            setEditorMode(undefined)
            await loadData()
        }, '平台配置已保存')
    }

    async function createImportPreview(values: NutritionCreateImportJobRequest) {
        setSaving(true)
        setMutationError(undefined)
        try {
            const job = await createNutritionImportJob(values)
            setImportJob(job)
            setImportOpen(false)
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    async function confirmImport(jobId: number) {
        if (!canMutate) return
        setConfirming(true)
        setMutationError(undefined)
        try {
            setImportJob(await confirmNutritionImportJob(jobId))
            void message.success('导入任务已确认')
            await loadData()
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
        } finally {
            setConfirming(false)
        }
    }

    async function mutate(operation: () => Promise<void>, success: string) {
        setSaving(true)
        setMutationError(undefined)
        try {
            await operation()
            void message.success(success)
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    const foodColumns: ColumnsType<NutritionStandardFoodResponse> = [
        {title: '标准食材', dataIndex: 'nameCn'},
        {title: '英文名', dataIndex: 'nameEn'},
        {title: '分类', dataIndex: 'category', width: 110},
        {title: '热量/100g', dataIndex: 'caloriesPer100g', width: 110},
        {title: '状态', dataIndex: 'status', width: 90, render: (value) => <Tag>{value}</Tag>},
        {
            title: '操作', width: 150, render: (_, record) => <Space size="small">
                <Button disabled={!canMutate} onClick={() => openEditor('food', record)} size="small">编辑</Button>
                <Button danger disabled={!canMutate} onClick={() => void mutate(async () => {
                    await deactivateNutritionStandardFood(record.id)
                    await loadData()
                }, '标准食材已停用')} size="small">停用</Button>
            </Space>,
        },
    ]
    const tagColumns: ColumnsType<NutritionHealthTagResponse> = [
        {title: '标签', dataIndex: 'name'},
        {title: '编码', dataIndex: 'tagCode'},
        {title: '类型', dataIndex: 'tagType'},
        {title: '排序', dataIndex: 'sortOrder', width: 80},
        {
            title: '操作', width: 150, render: (_, record) => <Space size="small">
                <Button disabled={!canMutate} onClick={() => openEditor('tag', record)} size="small">编辑</Button>
                <Button danger disabled={!canMutate} onClick={() => void mutate(async () => {
                    await deactivateNutritionPlatformHealthTag(record.id)
                    await loadData()
                }, '标签已停用')} size="small">停用</Button>
            </Space>,
        },
    ]
    const recipeColumns: ColumnsType<NutritionRecipeResponse> = [
        {title: '公共菜谱', dataIndex: 'name'},
        {title: '分类', dataIndex: 'category'},
        {title: '份数', dataIndex: 'servingCount', width: 80},
        {title: '食材数', render: (_, record) => record.ingredients.length, width: 90},
        {
            title: '操作', width: 150, render: (_, record) => <Space size="small">
                <Button disabled={!canMutate} onClick={() => openEditor('recipe', record)} size="small">编辑</Button>
                <Button danger disabled={!canMutate} onClick={() => void mutate(async () => {
                    await deactivateNutritionPlatformRecipe(record.id)
                    await loadData()
                }, '公共菜谱已停用')} size="small">停用</Button>
            </Space>,
        },
    ]

    return (
        <NutritionStack>
            <PageToolbar
                actions={(
                    <Space wrap>
                        <Button disabled={!canMutate} icon={<PlusOutlined/>} onClick={() => openEditor('food')} type="primary">新增标准食材</Button>
                        <Button disabled={!canMutate} onClick={() => openEditor('tag')}>新增标签</Button>
                        <Button disabled={!canMutate} onClick={() => openEditor('recipe')}>新增公共菜谱</Button>
                        <Button disabled={!canMutate} icon={<ImportOutlined/>} onClick={() => setImportOpen(true)}>创建导入任务</Button>
                    </Space>
                )}
                description="仅平台管理员可维护标准食材、标签、公共菜谱和九类平台导入任务。"
                title="营养平台"
            />
            <Alert showIcon title="仅平台管理员：本页配置会影响所有家庭的 AI 菜单、风险检查和导入模板。" type="warning"/>
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState error={error} onRetry={() => void loadData()} state={state}>
                <NutritionPageGrid>
                    <NutritionSection title="标准食材">
                        <Table columns={foodColumns} dataSource={foods} pagination={false} rowKey="id" scroll={{x: 900}} size="small"/>
                    </NutritionSection>
                    <NutritionSection title="标签配置">
                        <Table columns={tagColumns} dataSource={tags} pagination={false} rowKey="id" scroll={{x: 760}} size="small"/>
                    </NutritionSection>
                    <NutritionSection title="公共菜谱">
                        <Table columns={recipeColumns} dataSource={recipes} pagination={false} rowKey="id" scroll={{x: 760}} size="small"/>
                    </NutritionSection>
                </NutritionPageGrid>
            </NutritionAsyncState>
            <ImportJobPanel confirming={confirming} job={importJob} onConfirm={(jobId) => void confirmImport(jobId)}/>
            <Drawer destroyOnHidden loading={saving} onClose={() => setImportOpen(false)} open={importOpen} size={560} title="创建导入任务">
                <Form form={importForm} layout="vertical" onFinish={(values) => void createImportPreview(values)}>
                    <Form.Item label="导入类型" name="importType" rules={[{required: true}]}>
                        <Select aria-label="导入类型" options={importTypeOptions}/>
                    </Form.Item>
                    <Form.Item label="家庭 ID" name="familyId"><InputNumber min={1} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="文件名" name="fileName" rules={[{required: true}]}><Input aria-label="文件名"/></Form.Item>
                    <Form.Item label="CSV 内容" name="csvContent" rules={[{required: true}]}><Input.TextArea aria-label="CSV 内容" rows={12}/></Form.Item>
                    <Button htmlType="submit" loading={saving} type="primary">生成预览</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden loading={saving} onClose={() => setEditorMode(undefined)} open={Boolean(editorMode)} size={760} title={editorTitle(editorMode, Boolean(editingRecord))}>
                <Form form={editorForm} layout="vertical" onFinish={(values) => void saveEditor(values)}>
                    {editorMode === 'food' && <FoodEditor/>}
                    {editorMode === 'tag' && <TagEditor/>}
                    {editorMode === 'recipe' && <RecipeEditor/>}
                    <Button htmlType="submit" loading={saving} type="primary">保存配置</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

function FoodEditor() {
    return <>
        <NutritionPageGrid>
            <Form.Item label="中文名" name="nameCn" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="英文名" name="nameEn"><Input/></Form.Item>
            <Form.Item label="分类" name="category" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="数据质量" name="dataQuality" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="状态" name="status" rules={[{required: true}]}><Select options={statusOptions}/></Form.Item>
            <Form.Item label="外部来源" name="externalSource"><Input/></Form.Item>
            <Form.Item label="外部食材 ID" name="externalFoodId"><Input/></Form.Item>
            {nutrientFields.map((field) => <Form.Item key={field.name} label={field.label} name={field.name}><InputNumber min={0} style={{width: '100%'}}/></Form.Item>)}
            <Form.Item label="嘌呤等级" name="purineLevel"><Input/></Form.Item>
            <Form.Item label="GI" name="giValue"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
        </NutritionPageGrid>
        <Form.Item label="别名" name="aliases"><Select mode="tags"/></Form.Item>
        <Form.Item label="过敏标签" name="allergenTags"><Select mode="tags"/></Form.Item>
        <Form.Item label="适用标签" name="suitableTags"><Select mode="tags"/></Form.Item>
    </>
}

function TagEditor() {
    return <>
        <Form.Item label="标签类型" name="tagType" rules={[{required: true}]}><Select options={tagTypeOptions}/></Form.Item>
        <Form.Item label="标签编码" name="tagCode" rules={[{required: true}]}><Input/></Form.Item>
        <Form.Item label="标签名称" name="name" rules={[{required: true}]}><Input/></Form.Item>
        <Form.Item label="描述" name="description"><Input.TextArea/></Form.Item>
        <Form.Item label="排序" name="sortOrder" rules={[{required: true}]}><InputNumber min={0}/></Form.Item>
    </>
}

function RecipeEditor() {
    return <>
        <NutritionPageGrid>
            <Form.Item label="菜谱名称" name="name" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="分类" name="category"><Input/></Form.Item>
            <Form.Item label="份数" name="servingCount"><InputNumber min={1}/></Form.Item>
            <Form.Item label="烹饪分钟" name="cookingMinutes"><InputNumber min={0}/></Form.Item>
            <Form.Item label="难度" name="difficultyLevel"><Input/></Form.Item>
        </NutritionPageGrid>
        <Form.Item label="描述" name="description"><Input.TextArea/></Form.Item>
        <Form.Item label="适用标签" name="suitableTags"><Select mode="tags"/></Form.Item>
        <Form.Item label="过敏标签" name="allergenTags"><Select mode="tags"/></Form.Item>
        <Form.List name="ingredients">
            {(fields, {add, remove}) => <NutritionStack>
                {fields.map((field, index) => <Space align="start" key={field.key} wrap>
                    <Form.Item label={`食材 ${index + 1}`} name={[field.name, 'foodName']} rules={[{required: true}]}><Input/></Form.Item>
                    <Form.Item label="数量" name={[field.name, 'amount']} rules={[{required: true}]}><InputNumber min={0.001}/></Form.Item>
                    <Form.Item label="单位" name={[field.name, 'unit']} rules={[{required: true}]}><Input/></Form.Item>
                    <Form.Item label="可选" name={[field.name, 'optional']} valuePropName="checked"><Switch/></Form.Item>
                    <Button onClick={() => remove(field.name)} type="text">删除</Button>
                </Space>)}
                <Button onClick={() => add({unit: 'g', optional: false})}>添加食材</Button>
            </NutritionStack>}
        </Form.List>
        <Form.List name="steps">
            {(fields, {add, remove}) => <NutritionStack>
                {fields.map((field, index) => <Space align="start" key={field.key} wrap>
                    <Form.Item label={`步骤 ${index + 1}`} name={[field.name, 'instruction']} rules={[{required: true}]}><Input.TextArea/></Form.Item>
                    <Button onClick={() => remove(field.name)} type="text">删除</Button>
                </Space>)}
                <Button onClick={() => add({stepNo: fields.length + 1})}>添加步骤</Button>
            </NutritionStack>}
        </Form.List>
    </>
}

const importTypes: NutritionImportType[] = [
    'STANDARD_FOOD', 'PUBLIC_RECIPE', 'HEALTH_TAG', 'ALLERGY_TAG', 'DISLIKE_TAG',
    'DIET_GOAL', 'PRIVATE_RECIPE', 'FAMILY_INGREDIENT_MAPPING', 'HISTORICAL_PRICE',
]
const importTypeLabels: Record<NutritionImportType, string> = {
    STANDARD_FOOD: '标准食材',
    PUBLIC_RECIPE: '公共菜谱',
    HEALTH_TAG: '健康标签',
    ALLERGY_TAG: '过敏标签',
    DISLIKE_TAG: '不喜标签',
    DIET_GOAL: '饮食目标',
    PRIVATE_RECIPE: '家庭私有菜谱',
    FAMILY_INGREDIENT_MAPPING: '家庭食材映射',
    HISTORICAL_PRICE: '历史价格',
}
const importTypeOptions = importTypes.map((value) => ({label: importTypeLabels[value], value}))
const statusOptions = ['ACTIVE', 'DISABLED', 'ARCHIVED'].map((value) => ({label: value, value}))
const tagTypeOptions = ['HEALTH_TAG', 'ALLERGY_TAG', 'DISLIKE_TAG', 'DIET_GOAL'].map((value) => ({label: value, value}))
const nutrientFields = [
    {name: 'caloriesPer100g', label: '热量/100g'},
    {name: 'proteinPer100g', label: '蛋白/100g'},
    {name: 'fatPer100g', label: '脂肪/100g'},
    {name: 'carbsPer100g', label: '碳水/100g'},
    {name: 'sugarPer100g', label: '糖/100g'},
    {name: 'sodiumPer100g', label: '钠/100g'},
    {name: 'fiberPer100g', label: '纤维/100g'},
    {name: 'cholesterolPer100g', label: '胆固醇/100g'},
]

function editorTitle(mode?: EditorMode, editing = false) {
    if (!mode) return ''
    const title = {food: '标准食材', tag: '标签', recipe: '公共菜谱'}[mode]
    return `${editing ? '编辑' : '新增'}${title}`
}

export const Component = PlatformNutritionConfigPage
