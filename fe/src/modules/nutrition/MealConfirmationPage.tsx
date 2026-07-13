import {CheckOutlined} from '@ant-design/icons'
import {Alert, App, Button, Checkbox, Input, Select, Space, Table, Tag} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {RiskTag} from './components/RiskTag'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    createNutritionMealConfirmation,
    listNutritionMealConfirmations,
    listNutritionMembers,
    listTodayNutritionMealPlans,
} from './nutritionService'
import type {
    NutritionLoadState,
    NutritionMealConfirmationItemRequest,
    NutritionMealConfirmationResponse,
    NutritionMealPlanResponse,
    NutritionMemberProfileResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function MealConfirmationPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [members, setMembers] = useState<NutritionMemberProfileResponse[]>([])
    const [confirmations, setConfirmations] = useState<NutritionMealConfirmationResponse[]>([])
    const [memberProfileId, setMemberProfileId] = useState<number>()
    const [items, setItems] = useState<NutritionMealConfirmationItemRequest[]>([])
    const [remark, setRemark] = useState('')
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canConfirm = canUseRbacButton(auth, 'btn:nutrition:meal-confirmation:submit')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [plans, memberRows] = await Promise.all([
                listTodayNutritionMealPlans(familySelection.currentFamilyId),
                listNutritionMembers(familySelection.currentFamilyId),
            ])
            const currentPlan = plans[0]
            const confirmationRows = currentPlan
                ? await listNutritionMealConfirmations(familySelection.currentFamilyId, currentPlan.id)
                : []
            setMealPlan(currentPlan)
            setMembers(memberRows)
            setConfirmations(confirmationRows)
            setMemberProfileId((current) => current ?? memberRows[0]?.id)
            setItems(currentPlan?.items.map((item) => ({
                mealPlanItemId: item.id,
                selected: true,
                servingCount: item.servingCount,
                riskAcknowledged: false,
            })) ?? [])
            setState(currentPlan ? 'ready' : 'empty')
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

    function updateItem(itemId: number, patch: Partial<NutritionMealConfirmationItemRequest>) {
        setItems((current) => current.map((item) => item.mealPlanItemId === itemId ? {...item, ...patch} : item))
    }

    async function submitConfirmation() {
        if (!familySelection.currentFamilyId || !mealPlan || !memberProfileId) return
        setSaving(true)
        setMutationError(undefined)
        try {
            const created = await createNutritionMealConfirmation(familySelection.currentFamilyId, mealPlan.id, {
                memberProfileId,
                eatAtHome: true,
                items: items.map((item) => ({...item, servingCount: String(item.servingCount)})),
                remark: remark || undefined,
            })
            setConfirmations((current) => [created, ...current.filter((row) => row.memberProfileId !== created.memberProfileId)])
            await loadData()
            void message.success('用餐确认已提交')
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
            void message.error(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    const hasBlockingRisk = mealPlan?.risks.some((risk) => risk.blocking) ?? false
    const hasMediumRisk = mealPlan?.risks.some((risk) => risk.riskLevel === 'MEDIUM' && risk.requiresConfirmation) ?? false
    const mediumRiskPending = hasMediumRisk && items.some((item) => item.selected && !item.riskAcknowledged)
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
                        <Button
                            disabled={!canConfirm || hasBlockingRisk || mediumRiskPending || !memberProfileId}
                            icon={<CheckOutlined/>}
                            loading={saving}
                            onClick={() => void submitConfirmation()}
                            type="primary"
                        >提交确认</Button>
                    </Space>
                )}
                description="确认成员是否在家用餐、逐个菜品和份量；中风险需确认，高风险阻断提交。"
                title="用餐确认"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                emptyDescription="今天暂无可确认菜单"
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    {(mealPlan?.risks ?? []).map((risk) => (
                        <Alert
                            key={risk.id}
                            showIcon
                            title={<Space><RiskTag value={risk.riskLevel}/><span>{risk.riskMessage}</span></Space>}
                            type={risk.blocking ? 'error' : 'warning'}
                        />
                    ))}
                    <NutritionPageGrid>
                        <NutritionSection title="确认成员">
                            <NutritionStack>
                                <label htmlFor="nutrition-confirm-member">成员档案</label>
                                <Select
                                    id="nutrition-confirm-member"
                                    onChange={setMemberProfileId}
                                    options={members.map((member) => ({label: member.nickname, value: member.id}))}
                                    value={memberProfileId}
                                />
                                <label htmlFor="nutrition-confirm-remark">份量备注</label>
                                <Input id="nutrition-confirm-remark" onChange={(event) => setRemark(event.target.value)} placeholder="少盐、半份或不吃主食" value={remark}/>
                            </NutritionStack>
                        </NutritionSection>
                        <NutritionSection title="逐菜确认">
                            <NutritionStack>
                                {(mealPlan?.items ?? []).map((planItem) => {
                                    const item = items.find((row) => row.mealPlanItemId === planItem.id)
                                    return (
                                        <NutritionStack key={planItem.id}>
                                            <Checkbox
                                                aria-label={`选择${planItem.dishName}`}
                                                checked={item?.selected ?? false}
                                                disabled={!canConfirm || hasBlockingRisk}
                                                onChange={(event) => updateItem(planItem.id, {selected: event.target.checked})}
                                            ><span>{planItem.mealType} · </span><span>{planItem.dishName}</span></Checkbox>
                                            <Input
                                                aria-label={`${planItem.dishName}份数`}
                                                disabled={!canConfirm || hasBlockingRisk || !item?.selected}
                                                min={0.1}
                                                onChange={(event) => updateItem(planItem.id, {servingCount: event.target.value})}
                                                step={0.1}
                                                type="number"
                                                value={item?.servingCount}
                                            />
                                            {hasMediumRisk && (
                                                <Checkbox
                                                    aria-label={`确认${planItem.dishName}中风险`}
                                                    checked={item?.riskAcknowledged ?? false}
                                                    disabled={!canConfirm || hasBlockingRisk || !item?.selected}
                                                    onChange={(event) => updateItem(planItem.id, {riskAcknowledged: event.target.checked})}
                                                >确认中风险</Checkbox>
                                            )}
                                        </NutritionStack>
                                    )
                                })}
                            </NutritionStack>
                        </NutritionSection>
                    </NutritionPageGrid>
                    <NutritionSection title="确认记录">
                        <Table<NutritionMealConfirmationResponse>
                            columns={[
                                {title: '成员档案', dataIndex: 'memberProfileId'},
                                {title: '状态', dataIndex: 'confirmationStatus', render: (value) => <Tag color="success">{value}</Tag>},
                                {title: '在家用餐', dataIndex: 'eatAtHome', render: (value) => value ? '是' : '否'},
                                {title: '已选菜品', render: (_, row) => row.items.filter((item) => item.selected).length},
                                {title: '备注', dataIndex: 'remark', render: (value: string | null | undefined) => value || '-'},
                            ]}
                            dataSource={confirmations}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                </NutritionStack>
            </NutritionAsyncState>
        </NutritionStack>
    )
}

export const Component = MealConfirmationPage
