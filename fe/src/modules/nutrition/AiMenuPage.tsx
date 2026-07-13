import {CloudUploadOutlined, ReloadOutlined, RobotOutlined, SaveOutlined} from '@ant-design/icons'
import {Alert, App, Button, Checkbox, Descriptions, Input, Space, Table, Tag} from 'antd'
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
    generateNutritionAiRecommendation,
    getNutritionAiRecommendationJob,
    listNutritionAiRecommendations,
    listNutritionFamilyRecipes,
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
    NutritionMealPlanResponse,
    NutritionRecipeResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

const AI_POLL_INTERVAL_MS = 1000
const AI_POLL_LIMIT = 30

function AiMenuPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [recommendation, setRecommendation] = useState<NutritionAiRecommendationResponse>()
    const [recommendations, setRecommendations] = useState<NutritionAiRecommendationResponse[]>([])
    const [recipes, setRecipes] = useState<NutritionRecipeResponse[]>([])
    const [job, setJob] = useState<NutritionAiRecommendationJobResponse>()
    const [draftItems, setDraftItems] = useState<NutritionMealPlanResponse['items']>([])
    const [riskConfirmed, setRiskConfirmed] = useState(false)
    const [riskNote, setRiskNote] = useState('')
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [versionConflict, setVersionConflict] = useState(false)
    const [saving, setSaving] = useState(false)
    const pollCount = useRef(0)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:meal-plan:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [plans, recommendations, recipeRows] = await Promise.all([
                listNutritionMealPlans(familySelection.currentFamilyId),
                listNutritionAiRecommendations(familySelection.currentFamilyId),
                listNutritionFamilyRecipes(familySelection.currentFamilyId),
            ])
            const currentPlan = plans[0]
            setMealPlan(currentPlan)
            setDraftItems(currentPlan?.items ?? [])
            setRecommendations(recommendations)
            setRecommendation(recommendations.find((row) => row.mealPlanId === currentPlan?.id) ?? recommendations[0])
            setRecipes(recipeRows)
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
                    if (nextJob.status === 'SUCCEEDED') await loadData()
                    if (nextJob.status === 'FAILED') setMutationError(nextJob.errorMessage || 'AI 菜单生成失败')
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
        }, 'AI 菜单生成任务已提交')
    }

    function replaceDish(itemId: number) {
        const replacement = recipes[0]
        if (!replacement) return
        setDraftItems((items) => items.map((item) => item.id === itemId
            ? {...item, recipeId: replacement.id, dishName: replacement.name}
            : item))
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
                    id: item.id,
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
            setJob(await regenerateNutritionMealPlan(familySelection.currentFamilyId!, mealPlan.id))
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
                        <Button disabled={!canManage} icon={<RobotOutlined/>} loading={saving} onClick={() => void generateRecommendation()} type="primary">生成 AI 建议</Button>
                    </Space>
                )}
                description="审核 AI 建议、风险检查和厨师调整后的可发布菜单。"
                title="AI 菜单"
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
            {job && (
                <Table<NutritionAiRecommendationJobResponse>
                    columns={jobColumns}
                    dataSource={[job]}
                    pagination={false}
                    rowKey="id"
                    size="small"
                />
            )}
            <NutritionAsyncState
                emptyDescription="暂无 AI 菜单"
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
                                    <Button aria-label="保存调整" disabled={!canManage || hasBlockingRisk} icon={<SaveOutlined/>} loading={saving} onClick={() => void saveAdjustment()}>保存调整</Button>
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
                                    {title: '操作', width: 180, render: (_, item) => (
                                        <Button aria-label={`替换菜品 ${item.dishName}`} disabled={!canManage || hasBlockingRisk || recipes.length === 0} onClick={() => replaceDish(item.id)} size="small">替换菜品</Button>
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
        </NutritionStack>
    )
}

const jobColumns: ColumnsType<NutritionAiRecommendationJobResponse> = [
    {title: '任务 ID', dataIndex: 'id'},
    {title: '计划日期', dataIndex: 'plannedDate'},
    {title: '触发方式', dataIndex: 'triggerType'},
    {title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'FAILED' ? 'error' : 'processing'}>{value}</Tag>},
]

function nextLocalDate() {
    const date = new Date()
    date.setDate(date.getDate() + 1)
    return date.toLocaleDateString('en-CA')
}

function isVersionConflict(reason: unknown) {
    return typeof reason === 'object' && reason !== null && 'code' in reason
        && reason.code === 'NUTRITION_MEAL_VERSION_CONFLICT'
}

export const Component = AiMenuPage
