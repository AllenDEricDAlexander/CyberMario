import {StopOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Space, Table, Tag} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    closeNutritionMealPlanConfirmation,
    getNutritionMealPlanSummary,
    listTodayNutritionMealPlans,
} from './nutritionService'
import type {NutritionLoadState, NutritionMealPlanResponse, NutritionMealPlanSummaryResponse} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function MealSummaryPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [summary, setSummary] = useState<NutritionMealPlanSummaryResponse>()
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canClose = canUseRbacButton(auth, 'btn:nutrition:meal-confirmation:close')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const plans = await listTodayNutritionMealPlans(familySelection.currentFamilyId)
            const currentPlan = plans[0]
            const currentSummary = currentPlan
                ? await getNutritionMealPlanSummary(familySelection.currentFamilyId, currentPlan.id)
                : undefined
            setMealPlan(currentPlan)
            setSummary(currentSummary)
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
                emptyDescription="今天暂无餐食汇总"
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
                    <NutritionSection title="菜品份数汇总">
                        <Table
                            columns={[
                                {title: '菜品', dataIndex: 'dishName'},
                                {title: '餐次', dataIndex: 'mealType'},
                                {title: '原菜单份数', dataIndex: 'servingCount'},
                                {title: '选择人数', dataIndex: 'selectedMemberCount'},
                                {title: '确认份数', dataIndex: 'confirmedServingTotal'},
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
