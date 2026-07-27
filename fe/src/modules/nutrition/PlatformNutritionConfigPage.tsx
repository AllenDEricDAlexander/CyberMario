import {ImportOutlined, PlusOutlined, SettingOutlined} from '@ant-design/icons'
import {Alert, App, Button, Form, Input, InputNumber, Select, Space, Switch, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {FormDrawer} from '../../components/FormDrawer'
import {PageGrid} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions, type RowAction} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
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
import {NutritionPageGrid, NutritionStack} from './NutritionPageLayout'

/** Each drawer footer submits its form by id, so the buttons live outside the `<Form>`. */
const importFormId = 'nutrition-platform-import-form'
const editorFormId = 'nutrition-platform-editor-form'

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

    /** 编辑 + 停用 for every platform dictionary — the label only differs in the confirm copy. */
    function dictionaryActions(
        mode: EditorMode,
        record: NutritionStandardFoodResponse | NutritionHealthTagResponse | NutritionRecipeResponse,
        options: {name: string; deactivate: () => Promise<void>; success: string},
    ): RowAction[] {
        return [
            {key: 'edit', label: '编辑', disabled: !canMutate, onClick: () => openEditor(mode, record)},
            {
                key: 'deactivate',
                label: '停用',
                danger: true,
                disabled: !canMutate,
                confirm: `确认停用「${options.name}」？停用后所有家庭都不再可选。`,
                onClick: () => void mutate(async () => {
                    await options.deactivate()
                    await loadData()
                }, options.success),
            },
        ]
    }

    const foodColumns: ColumnsType<NutritionStandardFoodResponse> = [
        {
            title: '标准食材',
            dataIndex: 'nameCn',
            render: (value: string, record) => (
                <StackedCell primary={value} secondary={[record.nameEn, record.category].filter(Boolean).join(' · ')}/>
            ),
        },
        {title: '热量/100g', dataIndex: 'caloriesPer100g', width: 110},
        {title: '状态', dataIndex: 'status', width: 90, render: (value) => <Tag>{value}</Tag>},
        {
            title: '操作',
            width: 150,
            render: (_, record) => (
                <RowActions actions={dictionaryActions('food', record, {
                    name: record.nameCn,
                    deactivate: () => deactivateNutritionStandardFood(record.id).then(() => undefined),
                    success: '标准食材已停用',
                })}/>
            ),
        },
    ]
    const tagColumns: ColumnsType<NutritionHealthTagResponse> = [
        {
            title: '标签',
            dataIndex: 'name',
            render: (value: string, record) => <StackedCell primary={value} secondary={`${record.tagCode} · ${record.tagType}`}/>,
        },
        {title: '排序', dataIndex: 'sortOrder', width: 80},
        {
            title: '操作',
            width: 150,
            render: (_, record) => (
                <RowActions actions={dictionaryActions('tag', record, {
                    name: record.name,
                    deactivate: () => deactivateNutritionPlatformHealthTag(record.id).then(() => undefined),
                    success: '标签已停用',
                })}/>
            ),
        },
    ]
    const recipeColumns: ColumnsType<NutritionRecipeResponse> = [
        {
            title: '公共菜谱',
            dataIndex: 'name',
            render: (value: string, record) => (
                <StackedCell primary={value} secondary={`${record.category ?? '未分类'} · ${record.servingCount} 份 · ${record.ingredients.length} 种食材`}/>
            ),
        },
        {
            title: '操作',
            width: 150,
            render: (_, record) => (
                <RowActions actions={dictionaryActions('recipe', record, {
                    name: record.name,
                    deactivate: () => deactivateNutritionPlatformRecipe(record.id).then(() => undefined),
                    success: '公共菜谱已停用',
                })}/>
            ),
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
                icon={<SettingOutlined/>}
                title="营养平台"
            />
            <Alert showIcon title="仅平台管理员：本页配置会影响所有家庭的 AI 菜单、风险检查和导入模板。" type="warning"/>
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState error={error} onRetry={() => void loadData()} state={state}>
                <NutritionPageGrid>
                    <DataTable<NutritionStandardFoodResponse>
                        columns={foodColumns}
                        count={foods.length}
                        dataSource={foods}
                        emptyDescription="新增一条标准食材，或用导入任务批量建库。"
                        emptyTitle="暂无标准食材"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 620}}
                        size="small"
                        title="标准食材"
                    />
                    <DataTable<NutritionHealthTagResponse>
                        columns={tagColumns}
                        count={tags.length}
                        dataSource={tags}
                        emptyDescription="标签用于健康档案的过敏、不喜与饮食目标选项。"
                        emptyTitle="暂无标签配置"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 560}}
                        size="small"
                        title="标签配置"
                    />
                    <DataTable<NutritionRecipeResponse>
                        columns={recipeColumns}
                        count={recipes.length}
                        dataSource={recipes}
                        emptyDescription="公共菜谱对所有家庭可见，可作为 AI 配餐的候选。"
                        emptyTitle="暂无公共菜谱"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 560}}
                        size="small"
                        title="公共菜谱"
                    />
                </NutritionPageGrid>
            </NutritionAsyncState>
            <ImportJobPanel confirming={confirming} job={importJob} onConfirm={(jobId) => void confirmImport(jobId)}/>
            <FormDrawer
                footerHint="预览只做校验，确认后才会写入平台字典。"
                formId={importFormId}
                loading={saving}
                onClose={() => setImportOpen(false)}
                open={importOpen}
                size="md"
                submitText="生成预览"
                title="创建导入任务"
            >
                <Form form={importForm} id={importFormId} layout="vertical" onFinish={(values) => void createImportPreview(values)}>
                    <Form.Item label="导入类型" name="importType" rules={[{required: true}]}>
                        <Select aria-label="导入类型" options={importTypeOptions}/>
                    </Form.Item>
                    <Form.Item label="家庭 ID" name="familyId"><InputNumber className="u-full-width" min={1}/></Form.Item>
                    <Form.Item label="文件名" name="fileName" rules={[{required: true}]}><Input aria-label="文件名"/></Form.Item>
                    <Form.Item label="CSV 内容" name="csvContent" rules={[{required: true}]}><Input.TextArea aria-label="CSV 内容" rows={12}/></Form.Item>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={editorFormId}
                loading={saving}
                onClose={() => setEditorMode(undefined)}
                open={Boolean(editorMode)}
                size="lg"
                submitText="保存配置"
                title={editorTitle(editorMode, Boolean(editingRecord))}
            >
                <Form form={editorForm} id={editorFormId} layout="vertical" onFinish={(values) => void saveEditor(values)}>
                    {editorMode === 'food' && <FoodEditor/>}
                    {editorMode === 'tag' && <TagEditor/>}
                    {editorMode === 'recipe' && <RecipeEditor/>}
                </Form>
            </FormDrawer>
        </NutritionStack>
    )
}

function FoodEditor() {
    return <>
        {/* Many short fields — a tighter minimum than the page grid keeps them side by side. */}
        <PageGrid minWidth={200}>
            <Form.Item label="中文名" name="nameCn" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="英文名" name="nameEn"><Input/></Form.Item>
            <Form.Item label="分类" name="category" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="数据质量" name="dataQuality" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="状态" name="status" rules={[{required: true}]}><Select options={statusOptions}/></Form.Item>
            <Form.Item label="外部来源" name="externalSource"><Input/></Form.Item>
            <Form.Item label="外部食材 ID" name="externalFoodId"><Input/></Form.Item>
            {nutrientFields.map((field) => <Form.Item key={field.name} label={field.label} name={field.name}><InputNumber className="u-full-width" min={0}/></Form.Item>)}
            <Form.Item label="嘌呤等级" name="purineLevel"><Input/></Form.Item>
            <Form.Item label="GI" name="giValue"><InputNumber className="u-full-width" min={0}/></Form.Item>
        </PageGrid>
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
        <PageGrid minWidth={200}>
            <Form.Item label="菜谱名称" name="name" rules={[{required: true}]}><Input/></Form.Item>
            <Form.Item label="分类" name="category"><Input/></Form.Item>
            <Form.Item label="份数" name="servingCount"><InputNumber className="u-full-width" min={1}/></Form.Item>
            <Form.Item label="烹饪分钟" name="cookingMinutes"><InputNumber className="u-full-width" min={0}/></Form.Item>
            <Form.Item label="难度" name="difficultyLevel"><Input/></Form.Item>
        </PageGrid>
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
