import {DeleteOutlined, MinusCircleOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    createNutritionFamilyRecipe,
    deactivateNutritionFamilyRecipe,
    listNutritionFamilyRecipes,
    listNutritionFamilyStandardFoods,
    updateNutritionFamilyRecipe,
    updateNutritionRecipeIngredientMapping,
    validateNutritionRecipe,
} from './nutritionService'
import type {
    NutritionCreateRecipeRequest,
    NutritionLoadState,
    NutritionRecipeIngredientResponse,
    NutritionRecipeResponse,
    NutritionRecipeValidationResponse,
    NutritionStandardFoodResponse,
    NutritionUpdateRecipeIngredientMappingRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function RecipeLibraryPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [recipeForm] = Form.useForm<NutritionCreateRecipeRequest>()
    const [mappingForm] = Form.useForm<NutritionUpdateRecipeIngredientMappingRequest>()
    const [foods, setFoods] = useState<NutritionStandardFoodResponse[]>([])
    const [recipes, setRecipes] = useState<NutritionRecipeResponse[]>([])
    const [editingRecipe, setEditingRecipe] = useState<NutritionRecipeResponse>()
    const [mapping, setMapping] = useState<{recipe: NutritionRecipeResponse; ingredient: NutritionRecipeIngredientResponse}>()
    const [validation, setValidation] = useState<NutritionRecipeValidationResponse>()
    const [editorOpen, setEditorOpen] = useState(false)
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:recipe:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [foodRows, recipeRows] = await Promise.all([
                listNutritionFamilyStandardFoods(familySelection.currentFamilyId),
                listNutritionFamilyRecipes(familySelection.currentFamilyId),
            ])
            setFoods(foodRows)
            setRecipes(recipeRows)
            setState('ready')
            setError(undefined)
        } catch (reason) {
            const failure = nutritionLoadFailure(reason)
            setState(failure.state)
            setError(failure.error)
        }
    }, [familySelection.currentFamilyId])

    useEffect(() => {
        void loadData()
    }, [loadData])

    const ingredients = useMemo(
        () => recipes.flatMap((recipe) => recipe.ingredients.map((ingredient) => ({recipe, ingredient}))),
        [recipes],
    )
    const hasUnmapped = ingredients.some(({ingredient}) => ingredient.mappingStatus !== 'MAPPED')

    function openRecipeEditor(recipe?: NutritionRecipeResponse) {
        setEditingRecipe(recipe)
        setValidation(undefined)
        recipeForm.resetFields()
        if (recipe) {
            recipeForm.setFieldsValue({
                name: recipe.name,
                category: recipe.category ?? undefined,
                description: recipe.description ?? undefined,
                servingCount: recipe.servingCount,
                cookingMinutes: recipe.cookingMinutes ?? undefined,
                difficultyLevel: recipe.difficultyLevel ?? undefined,
                suitableTags: recipe.suitableTags,
                allergenTags: recipe.allergenTags,
                ingredients: recipe.ingredients.map((ingredient) => ({
                    standardFoodId: ingredient.standardFoodId ?? undefined,
                    foodName: ingredient.rawFoodName,
                    amount: ingredient.amount,
                    unit: ingredient.unit,
                    gramsPerUnit: ingredient.gramsPerUnit ?? undefined,
                    optional: ingredient.optional,
                })),
                steps: recipe.steps.map((step) => ({
                    stepNo: step.stepNo,
                    title: step.title ?? undefined,
                    instruction: step.instruction,
                })),
            })
        } else {
            recipeForm.setFieldsValue({
                servingCount: 1,
                ingredients: [{foodName: '', amount: '', unit: 'g', optional: false}],
                steps: [],
            })
        }
        setEditorOpen(true)
    }

    async function saveRecipe(values: NutritionCreateRecipeRequest) {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            if (editingRecipe) {
                await updateNutritionFamilyRecipe(familySelection.currentFamilyId!, editingRecipe.id, values)
            } else {
                await createNutritionFamilyRecipe(familySelection.currentFamilyId!, values)
            }
            setEditorOpen(false)
            await loadData()
        }, '菜谱已保存')
    }

    function openMapping(recipe: NutritionRecipeResponse, ingredient: NutritionRecipeIngredientResponse) {
        setMapping({recipe, ingredient})
        mappingForm.resetFields()
        if (ingredient.standardFoodId) {
            mappingForm.setFieldValue('standardFoodId', ingredient.standardFoodId)
        }
        mappingForm.setFieldValue('gramsPerUnit', ingredient.gramsPerUnit ?? undefined)
    }

    async function saveMapping(values: NutritionUpdateRecipeIngredientMappingRequest) {
        if (!familySelection.currentFamilyId || !mapping) return
        await mutate(async () => {
            await updateNutritionRecipeIngredientMapping(
                familySelection.currentFamilyId!, mapping.recipe.id, mapping.ingredient.id,
                {standardFoodId: values.standardFoodId, gramsPerUnit: values.gramsPerUnit},
            )
            setMapping(undefined)
            await loadData()
        }, '食材映射已保存')
    }

    async function runValidation(recipe: NutritionRecipeResponse) {
        if (!familySelection.currentFamilyId) return
        setValidation(await validateNutritionRecipe(familySelection.currentFamilyId, recipe.id))
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

    const recipeColumns: ColumnsType<NutritionRecipeResponse> = [
        {title: '菜谱', dataIndex: 'name'},
        {title: '来源', dataIndex: 'sourceType', width: 150, render: (value) => <Tag>{value}</Tag>},
        {title: '分类', dataIndex: 'category', width: 110},
        {title: '份数', dataIndex: 'servingCount', width: 80},
        {
            title: '操作', width: 260, render: (_, recipe) => (
                <Space wrap size="small">
                    <Button disabled={!canManage || recipe.sourceType === 'PLATFORM_PUBLIC'} onClick={() => openRecipeEditor(recipe)} size="small">编辑</Button>
                    <Button aria-label={`校验 ${recipe.name}`} onClick={() => void runValidation(recipe)} size="small">校验</Button>
                    <Button danger disabled={!canManage || recipe.sourceType === 'PLATFORM_PUBLIC'} onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await deactivateNutritionFamilyRecipe(familySelection.currentFamilyId, recipe.id)
                        await loadData()
                    }, '菜谱已停用')} size="small">停用</Button>
                </Space>
            ),
        },
    ]
    const ingredientColumns: ColumnsType<{recipe: NutritionRecipeResponse; ingredient: NutritionRecipeIngredientResponse}> = [
        {title: '菜谱', render: (_, row) => row.recipe.name},
        {title: '原始食材', render: (_, row) => row.ingredient.rawFoodName},
        {title: '数量', render: (_, row) => `${row.ingredient.amount} ${row.ingredient.unit}`},
        {title: '映射状态', render: (_, row) => <Tag color={row.ingredient.mappingStatus === 'MAPPED' ? 'success' : 'warning'}>{row.ingredient.mappingStatus}</Tag>},
        {
            title: '操作', width: 110, render: (_, row) => (
                <Button
                    aria-label={`映射 ${row.ingredient.rawFoodName}`}
                    disabled={!canManage || row.recipe.sourceType === 'PLATFORM_PUBLIC'}
                    onClick={() => openMapping(row.recipe, row.ingredient)}
                    size="small"
                >映射</Button>
            ),
        },
    ]
    const visibleState = familySelection.state === 'ready' ? state : familySelection.state

    return (
        <NutritionStack>
            <PageToolbar
                actions={(
                    <Space wrap>
                        <CurrentFamilySelect
                            families={familySelection.families}
                            loading={familySelection.state === 'loading'}
                            onChange={familySelection.setCurrentFamilyId}
                            value={familySelection.currentFamilyId}
                        />
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={() => openRecipeEditor()} type="primary">新建菜谱</Button>
                    </Space>
                )}
                description="维护平台可见食材、家庭菜谱、制作步骤、营养映射和发布校验。"
                title="家庭菜谱"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            {hasUnmapped && <Alert showIcon title="存在未映射食材" type="warning"/>}
            {validation && (
                <Alert
                    description={[...validation.errors, ...validation.warnings].join('；') || '校验通过'}
                    showIcon
                    title={validation.publishable ? '菜谱可发布' : '菜谱暂不可发布'}
                    type={validation.publishable ? 'success' : 'warning'}
                />
            )}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionPageGrid>
                    <NutritionSection title="标准食材">
                        <Table
                            columns={[
                                {title: '名称', dataIndex: 'nameCn'},
                                {title: '分类', dataIndex: 'category'},
                                {title: '热量/100g', dataIndex: 'caloriesPer100g'},
                            ]}
                            dataSource={foods}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                    <NutritionSection title="家庭与公共菜谱">
                        <Table columns={recipeColumns} dataSource={recipes} pagination={false} rowKey="id" scroll={{x: 760}} size="small"/>
                    </NutritionSection>
                    <NutritionSection title="食材映射">
                        <Table columns={ingredientColumns} dataSource={ingredients} pagination={false} rowKey={(row) => row.ingredient.id} scroll={{x: 760}} size="small"/>
                    </NutritionSection>
                </NutritionPageGrid>
            </NutritionAsyncState>
            <Drawer destroyOnHidden loading={saving} onClose={() => setEditorOpen(false)} open={editorOpen} size={760} title={editingRecipe ? '编辑菜谱' : '新建菜谱'}>
                <Form form={recipeForm} layout="vertical" onFinish={(values) => void saveRecipe(values)}>
                    <NutritionPageGrid>
                        <Form.Item label="菜谱名称" name="name" rules={[{required: true}]}><Input aria-label="菜谱名称"/></Form.Item>
                        <Form.Item label="分类" name="category"><Input/></Form.Item>
                        <Form.Item label="份数" name="servingCount"><InputNumber min={1} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="烹饪分钟" name="cookingMinutes"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="难度" name="difficultyLevel"><Input/></Form.Item>
                    </NutritionPageGrid>
                    <Form.Item label="描述" name="description"><Input.TextArea rows={3}/></Form.Item>
                    <Form.Item label="适用标签" name="suitableTags"><Select mode="tags"/></Form.Item>
                    <Form.Item label="过敏标签" name="allergenTags"><Select mode="tags"/></Form.Item>
                    <Form.List name="ingredients">
                        {(fields, {add, remove}) => (
                            <NutritionStack>
                                {fields.map((field, index) => (
                                    <Space align="start" key={field.key} wrap>
                                        <Form.Item label={`食材名称 ${index + 1}`} name={[field.name, 'foodName']} rules={[{required: true}]}>
                                            <Input aria-label={`食材名称 ${index + 1}`}/>
                                        </Form.Item>
                                        <Form.Item label={`数量 ${index + 1}`} name={[field.name, 'amount']} rules={[{required: true}]}>
                                            <InputNumber aria-label={`数量 ${index + 1}`} min={0.001}/>
                                        </Form.Item>
                                        <Form.Item label={`单位 ${index + 1}`} name={[field.name, 'unit']} rules={[{required: true}]}>
                                            <Input aria-label={`单位 ${index + 1}`}/>
                                        </Form.Item>
                                        <Button aria-label={`删除食材 ${index + 1}`} icon={<MinusCircleOutlined/>} onClick={() => remove(field.name)} type="text"/>
                                    </Space>
                                ))}
                                <Button icon={<PlusOutlined/>} onClick={() => add({unit: 'g', optional: false})}>添加食材</Button>
                            </NutritionStack>
                        )}
                    </Form.List>
                    <Form.List name="steps">
                        {(fields, {add, remove}) => (
                            <NutritionStack>
                                {fields.map((field, index) => (
                                    <Space align="start" key={field.key} wrap>
                                        <Form.Item initialValue={index + 1} label={`步骤序号 ${index + 1}`} name={[field.name, 'stepNo']}><InputNumber min={1}/></Form.Item>
                                        <Form.Item label={`步骤标题 ${index + 1}`} name={[field.name, 'title']}><Input/></Form.Item>
                                        <Form.Item label={`步骤说明 ${index + 1}`} name={[field.name, 'instruction']} rules={[{required: true}]}><Input.TextArea rows={2}/></Form.Item>
                                        <Button icon={<DeleteOutlined/>} onClick={() => remove(field.name)} type="text"/>
                                    </Space>
                                ))}
                                <Button onClick={() => add({stepNo: fields.length + 1})}>添加步骤</Button>
                            </NutritionStack>
                        )}
                    </Form.List>
                    <Button htmlType="submit" loading={saving} style={{marginTop: 16}} type="primary">保存菜谱</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden loading={saving} onClose={() => setMapping(undefined)} open={Boolean(mapping)} size={420} title="食材映射">
                <Form form={mappingForm} layout="vertical" onFinish={(values) => void saveMapping(values)}>
                    <Form.Item label="标准食材" name="standardFoodId" rules={[{required: true}]}>
                        <Select
                            aria-label="标准食材"
                            options={foods.map((food) => ({label: food.nameCn, value: food.id}))}
                        />
                    </Form.Item>
                    <Form.Item label="每单位克数" name="gramsPerUnit"><InputNumber min={0.001} style={{width: '100%'}}/></Form.Item>
                    <Button htmlType="submit" loading={saving} type="primary">保存映射</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

export const Component = RecipeLibraryPage
