import {CloudUploadOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, RobotOutlined, SaveOutlined} from '@ant-design/icons'
import {Alert, App, Button, Checkbox, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useRef, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {RiskTag} from './components/RiskTag'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    acknowledgeNutritionMealRisks,
    createNutritionTodayMealPlan,
    generateNutritionAiRecommendation,
    getNutritionAiRecommendationJob,
    listNutritionAiRecommendationJobs,
    listNutritionAiRecommendations,
    listNutritionMealPlanRecipeCandidates,
    listNutritionMealPlans,
    publishNutritionMealPlan,
    regenerateNutritionMealPlan,
    updateNutritionMealPlan,
} from './nutritionService'
import type {
    NutritionAiRecommendationJobResponse,
    NutritionAiRecommendationResponse,
    NutritionAmount,
    NutritionLoadState,
    NutritionMealType,
    NutritionMealPlanResponse,
    NutritionRecipeResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

const AI_POLL_INTERVAL_MS = 1000
const AI_POLL_LIMIT = 120

type ManualMealPlanForm = {
    title: string
    confirmationCutoffAt: string
    items: Array<{
        mealType: NutritionMealType
        recipeId: number
        servingCount: NutritionAmount
    }>
}

function AiMenuPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [manualForm] = Form.useForm<ManualMealPlanForm>()
    const [mealPlans, setMealPlans] = useState<NutritionMealPlanResponse[]>([])
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [recommendation, setRecommendation] = useState<NutritionAiRecommendationResponse>()
    const [recommendations, setRecommendations] = useState<NutritionAiRecommendationResponse[]>([])
    const [recipes, setRecipes] = useState<NutritionRecipeResponse[]>([])
    const [job, setJob] = useState<NutritionAiRecommendationJobResponse>()
    const [jobs, setJobs] = useState<NutritionAiRecommendationJobResponse[]>([])
    const [draftItems, setDraftItems] = useState<NutritionMealPlanResponse['items']>([])
    const [riskConfirmed, setRiskConfirmed] = useState(false)
    const [riskNote, setRiskNote] = useState('')
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [versionConflict, setVersionConflict] = useState(false)
    const [saving, setSaving] = useState(false)
    const [manualOpen, setManualOpen] = useState(false)
    const pollCount = useRef(0)
    const selectedMealPlanId = useRef<number | undefined>(undefined)
    const localDraftId = useRef(0)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:meal-plan:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [plans, recommendations, recipeRows, jobRows] = await Promise.all([
                listNutritionMealPlans(familySelection.currentFamilyId),
                listNutritionAiRecommendations(familySelection.currentFamilyId),
                listNutritionMealPlanRecipeCandidates(familySelection.currentFamilyId),
                listNutritionAiRecommendationJobs(familySelection.currentFamilyId),
            ])
            const latestGeneratedPlanId = jobRows.find((row) => row.mealPlanId)?.mealPlanId
            const currentPlan = plans.find((row) => row.id === selectedMealPlanId.current)
                ?? plans.find((row) => row.id === latestGeneratedPlanId)
                ?? plans[0]
            selectedMealPlanId.current = currentPlan?.id
            setMealPlans(plans)
            setMealPlan(currentPlan)
            setDraftItems(currentPlan?.items ?? [])
            setRecommendations(recommendations)
            setRecommendation(recommendations.find((row) => row.mealPlanId === currentPlan?.id))
            setRecipes(recipeRows)
            setJobs(jobRows)
            const currentJob = jobRows.find((row) => ['PENDING', 'RUNNING'].includes(row.status)) ?? jobRows[0]
            setJob(currentJob)
            if (currentJob?.status === 'FAILED') {
                setMutationError(nutritionAiJobError(currentJob.errorMessage))
            }
            setState(plans.length === 0 && recommendations.length === 0 ? 'empty' : 'ready')
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

    useEffect(() => {
        if (!job || !familySelection.currentFamilyId || !['PENDING', 'RUNNING'].includes(job.status)) return
        if (pollCount.current >= AI_POLL_LIMIT) {
            setMutationError('AI 生成仍在处理中，请稍后重新加载')
            return
        }
        const timer = window.setTimeout(() => {
            pollCount.current += 1
            void getNutritionAiRecommendationJob(familySelection.currentFamilyId!, job.id)
                .then(async (nextJob) => {
                    setJob(nextJob)
                    setJobs((current) => [nextJob, ...current.filter((row) => row.id !== nextJob.id)])
                    if (nextJob.status === 'SUCCEEDED') {
                        selectedMealPlanId.current = nextJob.mealPlanId ?? selectedMealPlanId.current
                        await loadData()
                    }
                    if (nextJob.status === 'FAILED') setMutationError(nutritionAiJobError(nextJob.errorMessage))
                })
                .catch((reason: unknown) => setMutationError(nutritionLoadFailure(reason).error))
        }, AI_POLL_INTERVAL_MS)
        return () => window.clearTimeout(timer)
    }, [familySelection.currentFamilyId, job, loadData])

    async function generateRecommendation() {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            pollCount.current = 0
            const nextJob = await generateNutritionAiRecommendation(familySelection.currentFamilyId!, {
                plannedDate: nextLocalDate(),
                mealTypes: mealPlan?.items.map((item) => item.mealType) ?? familySelection.currentFamily?.defaultMealTypes,
            })
            setJob(nextJob)
            setJobs((current) => [nextJob, ...current.filter((row) => row.id !== nextJob.id)])
        }, 'AI 菜单生成任务已提交')
    }

    function selectMealPlan(mealPlanId: number) {
        const selected = mealPlans.find((row) => row.id === mealPlanId)
        if (!selected) return
        selectedMealPlanId.current = selected.id
        setMealPlan(selected)
        setDraftItems(selected.items)
        setRecommendation(recommendations.find((row) => row.mealPlanId === selected.id))
    }

    function openManualMenu() {
        if (recipes.length === 0) {
            void message.warning('请先维护并校验至少一份可发布菜谱')
            return
        }
        manualForm.resetFields()
        manualForm.setFieldsValue({
            title: `今日菜单 ${currentLocalDate()}`,
            confirmationCutoffAt: defaultConfirmationCutoff(),
            items: [{
                mealType: familySelection.currentFamily?.defaultMealTypes?.[0] ?? 'DINNER',
                recipeId: recipes[0].id,
                servingCount: recipes[0].servingCount,
            }],
        })
        setManualOpen(true)
    }

    async function createManualMenu(values: ManualMealPlanForm) {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            const created = await createNutritionTodayMealPlan(familySelection.currentFamilyId!, {
                title: values.title.trim(),
                confirmationCutoffAt: new Date(values.confirmationCutoffAt).toISOString(),
                items: values.items.map((item, index) => ({...item, sortOrder: index})),
            })
            selectedMealPlanId.current = created.id
            setManualOpen(false)
            await loadData()
        }, '今日菜单已创建')
    }

    function replaceDish(itemId: number, recipeId: number) {
        const replacement = recipes.find((recipe) => recipe.id === recipeId)
        if (!replacement) return
        setDraftItems((items) => items.map((item) => item.id === itemId
            ? {...item, recipeId: replacement.id, dishName: replacement.name}
            : item))
    }

    function addDish() {
        if (!mealPlan || recipes.length === 0) return
        const recipe = recipes[0]
        localDraftId.current -= 1
        setDraftItems((items) => [...items, {
            id: localDraftId.current,
            mealPlanId: mealPlan.id,
            mealType: items[0]?.mealType ?? familySelection.currentFamily?.defaultMealTypes?.[0] ?? 'DINNER',
            recipeId: recipe.id,
            dishName: recipe.name,
            servingCount: recipe.servingCount,
            sortOrder: items.length,
            version: 0,
        }])
    }

    function removeDish(itemId: number) {
        setDraftItems((items) => items.filter((item) => item.id !== itemId)
            .map((item, index) => ({...item, sortOrder: index})))
    }

    async function saveAdjustment() {
        if (!familySelection.currentFamilyId || !mealPlan) return
        setSaving(true)
        setMutationError(undefined)
        setVersionConflict(false)
        try {
            const updated = await updateNutritionMealPlan(familySelection.currentFamilyId, mealPlan.id, {
                expectedVersion: mealPlan.version,
                confirmationCutoffAt: mealPlan.confirmationCutoffAt,
                items: draftItems.map((item) => ({
                    id: item.id > 0 ? item.id : undefined,
                    mealType: item.mealType,
                    recipeId: item.recipeId!,
                    servingCount: item.servingCount,
                    sortOrder: item.sortOrder,
                })),
            })
            setMealPlan(updated)
            setDraftItems(updated.items)
            await loadData()
            void message.success('菜单调整已保存')
        } catch (reason) {
            if (isVersionConflict(reason)) {
                setVersionConflict(true)
                return
            }
            setMutationError(nutritionLoadFailure(reason).error)
            void message.error(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    async function publishPlan() {
        if (!familySelection.currentFamilyId || !mealPlan) return
        const pendingRisks = mealPlan.risks.filter((risk) => risk.requiresConfirmation && !risk.acknowledged)
        await mutate(async () => {
            if (pendingRisks.length > 0) {
                const acknowledged = await acknowledgeNutritionMealRisks(
                    familySelection.currentFamilyId!,
                    mealPlan.id,
                    {riskIds: pendingRisks.map((risk) => risk.id), note: riskNote},
                )
                setMealPlan(acknowledged)
            }
            const published = await publishNutritionMealPlan(familySelection.currentFamilyId!, mealPlan.id)
            setMealPlan(published)
            setDraftItems(published.items)
            await loadData()
        }, '菜单已发布')
    }

    async function regenerate() {
        if (!familySelection.currentFamilyId || !mealPlan) return
        await mutate(async () => {
            pollCount.current = 0
            const nextJob = await regenerateNutritionMealPlan(familySelection.currentFamilyId!, mealPlan.id)
            setJob(nextJob)
            setJobs((current) => [nextJob, ...current.filter((row) => row.id !== nextJob.id)])
        }, '重新生成任务已提交')
    }

    async function mutate(operation: () => Promise<void>, success: string) {
        setSaving(true)
        setMutationError(undefined)
        try {
            await operation()
            void message.success(success)
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
            void message.error(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    const hasBlockingRisk = mealPlan?.risks.some((risk) => risk.blocking) ?? false
    const pendingConfirmRisk = mealPlan?.risks.some((risk) => risk.requiresConfirmation && !risk.acknowledged) ?? false
    const isEditable = mealPlan?.status === 'PENDING_REVIEW' || mealPlan?.status === 'ADJUSTED'
    const publishDisabled = !canManage || !mealPlan?.publishable || hasBlockingRisk
        || (pendingConfirmRisk && (!riskConfirmed || !riskNote.trim()))
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
                        {mealPlans.length > 0 && (
                            <Select
                                aria-label="选择菜单"
                                onChange={selectMealPlan}
                                options={mealPlans.map((plan) => ({
                                    label: `${plan.planDate} · ${plan.title} · ${plan.status}`,
                                    value: plan.id,
                                }))}
                                style={{minWidth: 260}}
                                value={mealPlan?.id}
                            />
                        )}
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={openManualMenu}>人工创建今日菜单</Button>
                        <Button disabled={!canManage} icon={<RobotOutlined/>} loading={saving} onClick={() => void generateRecommendation()} type="primary">生成 AI 建议</Button>
                    </Space>
                )}
                description="人工选择菜谱创建今日菜单，或审核 AI 建议、风险检查和厨师调整后的菜单。"
                title="菜单管理"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            {versionConflict && (
                <Alert
                    action={<Button aria-label="重新加载" icon={<ReloadOutlined/>} onClick={() => void loadData()} size="small">重新加载</Button>}
                    showIcon
                    title="菜单版本已变化，请重新加载后再编辑"
                    type="warning"
                />
            )}
            {jobs.length > 0 && (
                <Table<NutritionAiRecommendationJobResponse>
                    columns={jobColumns}
                    dataSource={jobs}
                    pagination={false}
                    rowKey="id"
                    size="small"
                />
            )}
            <NutritionAsyncState
                emptyDescription="暂无菜单，可人工创建今日菜单或生成 AI 建议"
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    {mealPlan && <Descriptions bordered column={2} size="small">
                        <Descriptions.Item label="菜单">{mealPlan.title}</Descriptions.Item>
                        <Descriptions.Item label="状态"><Tag>{mealPlan.status}</Tag></Descriptions.Item>
                        <Descriptions.Item label="预估成本"><MoneyText value={mealPlan.estimatedCost}/></Descriptions.Item>
                        <Descriptions.Item label="版本">{mealPlan.version}</Descriptions.Item>
                        {recommendation && <Descriptions.Item label="推荐理由" span={2}>{recommendation.reason || '-'}</Descriptions.Item>}
                    </Descriptions>}
                    <NutritionSection title="AI 建议对比">
                        <Table<NutritionAiRecommendationResponse>
                            columns={[
                                {title: '建议', dataIndex: 'title'},
                                {title: '日期', dataIndex: 'recommendationDate'},
                                {title: '推荐理由', dataIndex: 'reason', render: (value: string | null | undefined) => value || '-'},
                                {title: '成本预估', dataIndex: 'costEstimate', render: (value: NutritionAmount | null | undefined) => <MoneyText value={value}/>},
                                {title: '状态', dataIndex: 'status'},
                            ]}
                            dataSource={recommendations}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                    <NutritionPageGrid>
                        <NutritionSection title="风险检查">
                            <NutritionStack>
                                {(mealPlan?.risks ?? []).map((risk) => (
                                    <Alert
                                        key={risk.id}
                                        showIcon
                                        title={<Space><RiskTag value={risk.riskLevel}/><span>{risk.riskMessage}</span></Space>}
                                        type={risk.blocking ? 'error' : 'warning'}
                                    />
                                ))}
                                {pendingConfirmRisk && (
                                    <>
                                        <Checkbox checked={riskConfirmed} onChange={(event) => setRiskConfirmed(event.target.checked)}>确认中风险</Checkbox>
                                        <Input aria-label="风险确认说明" onChange={(event) => setRiskNote(event.target.value)} placeholder="填写核对说明" value={riskNote}/>
                                    </>
                                )}
                                {hasBlockingRisk && <Button disabled={!canManage} onClick={() => void regenerate()}>重新生成</Button>}
                            </NutritionStack>
                        </NutritionSection>
                        <NutritionSection
                            extra={(
                                <Space>
                                    <Button disabled={!canManage || !isEditable || hasBlockingRisk || recipes.length === 0} icon={<PlusOutlined/>} onClick={addDish}>添加菜品</Button>
                                    <Button aria-label="保存调整" disabled={!canManage || !isEditable || hasBlockingRisk || draftItems.length === 0} icon={<SaveOutlined/>} loading={saving} onClick={() => void saveAdjustment()}>保存调整</Button>
                                    <Button disabled={publishDisabled} icon={<CloudUploadOutlined/>} loading={saving} onClick={() => void publishPlan()} type="primary">发布菜单</Button>
                                </Space>
                            )}
                            title="厨师调整菜单"
                        >
                            <Table
                                columns={[
                                    {title: '餐次', dataIndex: 'mealType', width: 110},
                                    {title: '菜品', dataIndex: 'dishName'},
                                    {title: '份数', dataIndex: 'servingCount', width: 90},
                                    {title: '选择菜谱', width: 220, render: (_, item) => (
                                        <Select
                                            aria-label={`选择菜谱 ${item.dishName}`}
                                            disabled={!canManage || !isEditable || hasBlockingRisk || recipes.length === 0}
                                            onChange={(recipeId) => replaceDish(item.id, recipeId)}
                                            options={recipes.map((recipe) => ({label: recipe.name, value: recipe.id}))}
                                            style={{width: '100%'}}
                                            value={item.recipeId ?? undefined}
                                        />
                                    )},
                                    {title: '操作', width: 80, render: (_, item) => (
                                        <Button
                                            aria-label={`删除菜品 ${item.dishName}`}
                                            danger
                                            disabled={!canManage || !isEditable || hasBlockingRisk || draftItems.length <= 1}
                                            icon={<DeleteOutlined/>}
                                            onClick={() => removeDish(item.id)}
                                            size="small"
                                            type="text"
                                        />
                                    )},
                                ]}
                                dataSource={draftItems}
                                pagination={false}
                                rowKey="id"
                                size="small"
                            />
                        </NutritionSection>
                    </NutritionPageGrid>
                </NutritionStack>
            </NutritionAsyncState>
            <Drawer
                destroyOnHidden
                loading={saving}
                onClose={() => setManualOpen(false)}
                open={manualOpen}
                size={720}
                title="人工创建今日菜单"
            >
                <Form form={manualForm} layout="vertical" onFinish={(values) => void createManualMenu(values)}>
                    <Form.Item label="菜单标题" name="title" rules={[{required: true, whitespace: true}]}>
                        <Input aria-label="菜单标题" maxLength={128}/>
                    </Form.Item>
                    <Form.Item label="确认截止时间" name="confirmationCutoffAt" rules={[{required: true}]}>
                        <Input aria-label="确认截止时间" type="datetime-local"/>
                    </Form.Item>
                    <Form.List name="items">
                        {(fields, {add, remove}) => (
                            <NutritionStack>
                                {fields.map((field, index) => (
                                    <Space align="start" key={field.key} wrap>
                                        <Form.Item label={`餐次 ${index + 1}`} name={[field.name, 'mealType']} rules={[{required: true}]}>
                                            <Select aria-label={`餐次 ${index + 1}`} options={mealTypeOptions}/>
                                        </Form.Item>
                                        <Form.Item label={`菜谱 ${index + 1}`} name={[field.name, 'recipeId']} rules={[{required: true}]}>
                                            <Select
                                                aria-label={`菜谱 ${index + 1}`}
                                                options={recipes.map((recipe) => ({label: recipe.name, value: recipe.id}))}
                                                style={{minWidth: 220}}
                                            />
                                        </Form.Item>
                                        <Form.Item label={`份数 ${index + 1}`} name={[field.name, 'servingCount']} rules={[{required: true}]}>
                                            <InputNumber aria-label={`份数 ${index + 1}`} min={0.001} step={0.5}/>
                                        </Form.Item>
                                        <Button
                                            aria-label={`删除菜单项 ${index + 1}`}
                                            danger
                                            disabled={fields.length <= 1}
                                            icon={<DeleteOutlined/>}
                                            onClick={() => remove(field.name)}
                                            type="text"
                                        />
                                    </Space>
                                ))}
                                <Button icon={<PlusOutlined/>} onClick={() => add({
                                    mealType: familySelection.currentFamily?.defaultMealTypes?.[0] ?? 'DINNER',
                                    recipeId: recipes[0]?.id,
                                    servingCount: recipes[0]?.servingCount ?? 1,
                                })}>添加菜品</Button>
                            </NutritionStack>
                        )}
                    </Form.List>
                    <Button htmlType="submit" loading={saving} style={{marginTop: 16}} type="primary">创建今日菜单</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

const jobColumns: ColumnsType<NutritionAiRecommendationJobResponse> = [
    {title: '任务 ID', dataIndex: 'id'},
    {title: '计划日期', dataIndex: 'plannedDate'},
    {title: '触发方式', dataIndex: 'triggerType'},
    {title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'FAILED' ? 'error' : 'processing'}>{value}</Tag>},
    {title: '失败原因', dataIndex: 'errorMessage', render: (value: string | null | undefined) => value ? nutritionAiJobError(value) : '-'},
]

const mealTypeOptions = [
    {label: '早餐', value: 'BREAKFAST'},
    {label: '午餐', value: 'LUNCH'},
    {label: '晚餐', value: 'DINNER'},
    {label: '加餐', value: 'SNACK'},
]

function nextLocalDate() {
    const date = new Date()
    date.setDate(date.getDate() + 1)
    return date.toLocaleDateString('en-CA')
}

function currentLocalDate() {
    return new Date().toLocaleDateString('en-CA')
}

function defaultConfirmationCutoff() {
    const date = new Date(Date.now() + 2 * 60 * 60 * 1000)
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return `${date.toLocaleDateString('en-CA')}T${hours}:${minutes}`
}

function nutritionAiJobError(error?: string | null) {
    if (!error) return 'AI 菜单生成失败'
    if (error.includes('NUTRITION_UNIT_CONVERSION_MISSING')) {
        return '菜谱食材单位无法换算，请到家庭菜谱中修正单位或填写每单位克数'
    }
    if (error.includes('NUTRITION_AI_RECIPE_INVALID')) {
        return 'AI 选择的菜谱未通过发布校验，请检查家庭菜谱数据'
    }
    if (error.includes('NUTRITION_AI_OUTPUT_INVALID')) {
        return 'AI 返回的菜单格式无效，请重新生成'
    }
    return error
}

function isVersionConflict(reason: unknown) {
    return typeof reason === 'object' && reason !== null && 'code' in reason
        && reason.code === 'NUTRITION_MEAL_VERSION_CONFLICT'
}

export const Component = AiMenuPage
