import {SaveOutlined, StopOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Input, InputNumber, Select, Space, Table, Tag} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {selectNearestNutritionMealPlan} from './mealPlanSelection'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    adjustNutritionConfirmedMenu,
    closeNutritionMealPlanConfirmation,
    getNutritionMealPlanSummary,
    listNutritionMealPlans,
} from './nutritionService'
import type {
    NutritionAmount,
    NutritionLoadState,
    NutritionMealPlanResponse,
    NutritionMealPlanSummaryResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function MealSummaryPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [mealPlans, setMealPlans] = useState<NutritionMealPlanResponse[]>([])
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [summary, setSummary] = useState<NutritionMealPlanSummaryResponse>()
    const [finalServings, setFinalServings] = useState<Record<number, NutritionAmount>>({})
    const [adjustmentNote, setAdjustmentNote] = useState('')
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const selectedMealPlanId = useRef<number | undefined>(undefined)
    const canClose = canUseRbacButton(auth, 'btn:nutrition:meal-confirmation:close')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const plans = await listNutritionMealPlans(familySelection.currentFamilyId)
            const currentPlan = plans.find((plan) => plan.id === selectedMealPlanId.current)
                ?? selectNearestNutritionMealPlan(plans, [
                'PUBLISHED',
                'CONFIRMING',
                'CONFIRM_CLOSED',
                'PREPARING',
                'COMPLETED',
            ])
            const currentSummary = currentPlan
                ? await getNutritionMealPlanSummary(familySelection.currentFamilyId, currentPlan.id)
                : undefined
            selectedMealPlanId.current = currentPlan?.id
            setMealPlans(plans)
            setMealPlan(currentPlan)
            setSummary(currentSummary)
            setFinalServings(Object.fromEntries(
                (currentSummary?.dishes ?? []).map((dish) => [dish.itemId, dish.finalServingCount]),
            ))
            setState(currentPlan && currentSummary ? 'ready' : 'empty')
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

    async function closeEarly() {
        if (!familySelection.currentFamilyId || !mealPlan) return
        setSaving(true)
        setMutationError(undefined)
        try {
            setMealPlan(await closeNutritionMealPlanConfirmation(familySelection.currentFamilyId, mealPlan.id, true))
            await loadData()
            void message.success('用餐确认已提前关闭')
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
            void message.error(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    async function selectMealPlan(mealPlanId: number) {
        selectedMealPlanId.current = mealPlanId
        await loadData()
    }

    async function saveConfirmedMenu() {
        if (!familySelection.currentFamilyId || !mealPlan || !summary) return
        setSaving(true)
        setMutationError(undefined)
        try {
            await adjustNutritionConfirmedMenu(familySelection.currentFamilyId, mealPlan.id, {
                expectedVersion: mealPlan.version,
                note: adjustmentNote.trim() || undefined,
                items: summary.dishes.map((dish) => ({
                    mealPlanItemId: dish.itemId,
                    finalServingCount: finalServings[dish.itemId] ?? dish.finalServingCount,
                })),
            })
            await loadData()
            void message.success('确认后菜单已调整，采购清单已同步更新')
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
            void message.error(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    const visibleState = familySelection.state === 'ready' ? state : familySelection.state
    const confirmedMenuEditable = canClose && mealPlan?.status === 'CONFIRM_CLOSED'

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
                                aria-label="选择餐食汇总菜单"
                                onChange={(value) => void selectMealPlan(value)}
                                options={mealPlans.map((plan) => ({
                                    label: `${plan.planDate} · ${plan.title} · ${plan.status}`,
                                    value: plan.id,
                                }))}
                                style={{minWidth: 280}}
                                value={mealPlan?.id}
                            />
                        )}
                        <Button
                            disabled={!canClose || mealPlan?.status !== 'CONFIRMING'}
                            icon={<StopOutlined/>}
                            loading={saving}
                            onClick={() => void closeEarly()}
                            type="primary"
                        >提前关闭确认</Button>
                    </Space>
                )}
                description="汇总确认、离家和未确认人数，并按菜品统计最终份数。"
                title="餐食汇总"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                emptyDescription="暂无餐食汇总"
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionPageGrid>
                        <NutritionSection title="参与情况">
                            <Space wrap>
                                <Tag color="success">已确认 {summary?.confirmedMemberCount ?? 0}</Tag>
                                <Tag>不在家 {summary?.awayMemberCount ?? 0}</Tag>
                                <Tag color="warning">未确认 {summary?.unconfirmedMemberCount ?? 0}</Tag>
                                <Tag>有效成员 {summary?.activeMemberCount ?? 0}</Tag>
                            </Space>
                        </NutritionSection>
                        <NutritionSection title="菜单信息">
                            <Descriptions column={1} bordered size="small">
                                <Descriptions.Item label="菜单">{mealPlan?.title}</Descriptions.Item>
                                <Descriptions.Item label="状态">{mealPlan?.status}</Descriptions.Item>
                                <Descriptions.Item label="预估成本"><MoneyText value={mealPlan?.estimatedCost}/></Descriptions.Item>
                                <Descriptions.Item label="可生成采购">{summary?.readyForShopping ? '是' : '否'}</Descriptions.Item>
                            </Descriptions>
                        </NutritionSection>
                        <NutritionSection title="确认备注">
                            {(summary?.remarks.length ?? 0) > 0
                                ? summary?.remarks.map((remark) => <div key={remark}>{remark}</div>)
                                : '-'}
                        </NutritionSection>
                    </NutritionPageGrid>
                    <NutritionSection
                        extra={confirmedMenuEditable && (
                            <Space wrap>
                                <Input
                                    aria-label="确认后菜单调整说明"
                                    onChange={(event) => setAdjustmentNote(event.target.value)}
                                    placeholder="调整说明（可选）"
                                    value={adjustmentNote}
                                />
                                <Button
                                    icon={<SaveOutlined/>}
                                    loading={saving}
                                    onClick={() => void saveConfirmedMenu()}
                                    type="primary"
                                >保存确认后菜单调整</Button>
                            </Space>
                        )}
                        title={mealPlan?.status === 'CONFIRM_CLOSED' ? '确认后菜单' : '菜品份数汇总'}
                    >
                        <Table
                            columns={[
                                {title: '菜品', dataIndex: 'dishName'},
                                {title: '餐次', dataIndex: 'mealType'},
                                {title: '原菜单份数', dataIndex: 'servingCount'},
                                {title: '选择人数', dataIndex: 'selectedMemberCount'},
                                {title: '确认份数', dataIndex: 'confirmedServingTotal'},
                                {title: '最终采购份数', render: (_, dish) => confirmedMenuEditable ? (
                                    <InputNumber
                                        aria-label={`最终采购份数 ${dish.dishName}`}
                                        min={0}
                                        onChange={(value) => setFinalServings((current) => ({
                                            ...current,
                                            [dish.itemId]: value ?? 0,
                                        }))}
                                        step={0.5}
                                        value={finalServings[dish.itemId]}
                                    />
                                ) : dish.finalServingCount},
                            ]}
                            dataSource={summary?.dishes ?? []}
                            pagination={false}
                            rowKey="itemId"
                            size="small"
                        />
                    </NutritionSection>
                </NutritionStack>
            </NutritionAsyncState>
        </NutritionStack>
    )
}

export const Component = MealSummaryPage
