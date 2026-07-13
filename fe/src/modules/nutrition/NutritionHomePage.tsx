import {CheckCircleOutlined, DollarOutlined, RobotOutlined} from '@ant-design/icons'
import {Card, Statistic, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {getNutritionHomeOverview} from './nutritionService'
import type {NutritionAmount, NutritionHomeOverviewResponse, NutritionLoadState, NutritionMealPlanResponse} from './nutritionTypes'
import {NutritionPageGrid, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function NutritionHomePage() {
    const familySelection = useNutritionFamilySelection()
    const [overview, setOverview] = useState<NutritionHomeOverviewResponse>()
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()

    const loadOverview = useCallback(async () => {
        if (!familySelection.currentFamilyId) {
            setOverview(undefined)
            return
        }
        setState('loading')
        try {
            const response = await getNutritionHomeOverview(familySelection.currentFamilyId, {date: localDate()})
            setOverview(response)
            setState('ready')
            setError(undefined)
        } catch (reason) {
            const failure = nutritionLoadFailure(reason)
            setState(failure.state)
            setError(failure.error)
        }
    }, [familySelection.currentFamilyId])

    useEffect(() => {
        void loadOverview()
    }, [loadOverview])

    const columns: ColumnsType<NutritionMealPlanResponse> = [
        {title: '菜单', dataIndex: 'title', width: 180},
        {title: '日期', dataIndex: 'planDate', width: 120},
        {title: '状态', dataIndex: 'status', width: 140, render: (value) => <Tag color="processing">{value}</Tag>},
        {title: '确认人数', dataIndex: 'confirmedMemberCount', width: 110},
        {title: '预估成本', dataIndex: 'estimatedCost', width: 130, render: (value: NutritionAmount | null | undefined) => <MoneyText value={value}/>},
    ]
    const visibleState = familySelection.state === 'ready' ? state : familySelection.state
    const visibleError = familySelection.state === 'ready' ? error : familySelection.error

    return (
        <NutritionStack>
            <PageToolbar
                actions={(
                    <CurrentFamilySelect
                        families={familySelection.families}
                        loading={familySelection.state === 'loading'}
                        onChange={familySelection.setCurrentFamilyId}
                        value={familySelection.currentFamilyId}
                    />
                )}
                description="跟踪家庭菜单、确认进度、风险、采购和预算使用。"
                title="营养首页"
            />
            <NutritionAsyncState
                error={visibleError}
                onRetry={() => void (familySelection.state === 'ready' ? loadOverview() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionPageGrid>
                        <Card>
                            <Statistic
                                prefix={<RobotOutlined/>}
                                title="今日菜单"
                                value={overview?.mealPlans.length ?? 0}
                            />
                        </Card>
                        <Card>
                            <Statistic
                                suffix="%"
                                prefix={<DollarOutlined/>}
                                title="预算使用率"
                                value={Number(overview?.budgetUsageRate ?? 0)}
                            />
                        </Card>
                        <Card>
                            <Statistic
                                prefix={<CheckCircleOutlined/>}
                                title="待确认成员"
                                value={overview?.unconfirmedMemberCount ?? 0}
                            />
                        </Card>
                        <Card title="今日实际成本">
                            <MoneyText value={overview?.actualCost ?? 0}/>
                        </Card>
                    </NutritionPageGrid>
                    <Table<NutritionMealPlanResponse>
                        columns={columns}
                        dataSource={overview?.mealPlans ?? []}
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 760}}
                        size="small"
                    />
                </NutritionStack>
            </NutritionAsyncState>
        </NutritionStack>
    )
}

function localDate() {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

export const Component = NutritionHomePage
