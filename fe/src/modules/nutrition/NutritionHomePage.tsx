import {CheckCircleOutlined, DashboardOutlined, DollarOutlined, RobotOutlined} from '@ant-design/icons'
import {Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
import {StatCard, StatGrid} from '../../components/StatCard'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {getNutritionHomeOverview} from './nutritionService'
import type {NutritionAmount, NutritionHomeOverviewResponse, NutritionLoadState, NutritionMealPlanResponse} from './nutritionTypes'
import {NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

const mealPlanColumns: ColumnsType<NutritionMealPlanResponse> = [
    {
        title: '菜单',
        dataIndex: 'title',
        render: (value: string, row) => <StackedCell primary={value} secondary={row.planDate}/>,
    },
    {title: '状态', dataIndex: 'status', width: 140, render: (value) => <Tag color="processing">{value}</Tag>},
    {title: '确认人数', dataIndex: 'confirmedMemberCount', width: 110},
    {title: '预估成本', dataIndex: 'estimatedCost', width: 130, render: (value: NutritionAmount | null | undefined) => <MoneyText value={value}/>},
]

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
                icon={<DashboardOutlined/>}
                title="营养首页"
            />
            <NutritionAsyncState
                error={visibleError}
                onRetry={() => void (familySelection.state === 'ready' ? loadOverview() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <StatGrid columns={4}>
                        <StatCard
                            icon={<RobotOutlined/>}
                            label="今日菜单"
                            value={overview?.mealPlans.length ?? 0}
                        />
                        <StatCard
                            icon={<DollarOutlined/>}
                            label="预算使用率"
                            suffix="%"
                            tone="sky"
                            value={Number(overview?.budgetUsageRate ?? 0)}
                        />
                        <StatCard
                            icon={<CheckCircleOutlined/>}
                            label="待确认成员"
                            tone="amber"
                            value={overview?.unconfirmedMemberCount ?? 0}
                        />
                        <StatCard
                            hint="按已记录的采购价格累计"
                            label="今日实际成本"
                            tone="violet"
                            value={<MoneyText value={overview?.actualCost ?? 0}/>}
                        />
                    </StatGrid>
                    <DataTable<NutritionMealPlanResponse>
                        columns={mealPlanColumns}
                        count={overview?.mealPlans.length ?? 0}
                        dataSource={overview?.mealPlans ?? []}
                        emptyDescription="生成或人工创建今日菜单后，确认进度和成本会显示在这里。"
                        emptyTitle="今天还没有菜单"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 560}}
                        size="small"
                        title="今日菜单"
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
