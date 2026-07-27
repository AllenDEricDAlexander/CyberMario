import {DollarOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Form, Input, InputNumber, Select, Space, Switch, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {FormDrawer} from '../../components/FormDrawer'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions, type RowAction} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    createNutritionBudgetRule,
    deactivateNutritionBudgetRule,
    getNutritionMonthlyBudget,
    getNutritionWeeklyBudget,
    listNutritionBudgetRules,
    updateNutritionBudgetRule,
} from './nutritionService'
import type {
    NutritionBudgetChannelSummaryResponse,
    NutritionBudgetDailySummaryResponse,
    NutritionBudgetDishSummaryResponse,
    NutritionBudgetIngredientSummaryResponse,
    NutritionBudgetRuleResponse,
    NutritionBudgetSummaryResponse,
    NutritionAmount,
    NutritionLoadState,
    NutritionUpsertBudgetRuleRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

/** The drawer footer submits the form by id, so the button lives outside the `<Form>`. */
const ruleFormId = 'nutrition-budget-rule-form'

const dailyColumns: ColumnsType<NutritionBudgetDailySummaryResponse> = [
    {
        title: '日期',
        dataIndex: 'date',
        render: (value: string, row) => <StackedCell primary={value} secondary={`${row.confirmedMemberCount} 人确认`}/>,
    },
    {title: '总成本', dataIndex: 'totalAmount', render: (value: NutritionAmount) => <MoneyText value={value}/>},
    {title: '实际支出', dataIndex: 'actualAmount', render: (value: NutritionAmount) => <MoneyText value={value}/>},
    {title: '预估成本', dataIndex: 'estimatedAmount', render: (value: NutritionAmount) => <MoneyText value={value}/>},
    {title: '人均成本', dataIndex: 'perPersonCost', render: (value: NutritionAmount) => <MoneyText value={value}/>},
]

const dishColumns: ColumnsType<NutritionBudgetDishSummaryResponse> = [
    {
        title: '菜品',
        dataIndex: 'dishName',
        render: (value: string, row) => <StackedCell primary={value} secondary={`${row.planDate} · ${row.mealType}`}/>,
    },
    {
        title: '份数',
        dataIndex: 'finalServingCount',
        width: 140,
        render: (value: NutritionAmount, row) => (
            <StackedCell plain primary={`最终 ${value}`} secondary={`确认 ${row.confirmedServingCount}`}/>
        ),
    },
    {title: '成本', dataIndex: 'amount', width: 110, render: (value: NutritionAmount) => <MoneyText value={value}/>},
]

const ingredientColumns: ColumnsType<NutritionBudgetIngredientSummaryResponse> = [
    {
        title: '食材',
        dataIndex: 'rawFoodName',
        render: (value: string, row) => (
            <StackedCell primary={value} secondary={`计划 ${row.plannedAmount}${row.unit ?? ''} · 已购 ${row.purchasedAmount}${row.unit ?? ''}`}/>
        ),
    },
    {title: '成本', dataIndex: 'totalAmount', width: 110, render: (value: NutritionAmount) => <MoneyText value={value}/>},
]

const channelColumns: ColumnsType<NutritionBudgetChannelSummaryResponse> = [
    {
        title: '渠道',
        dataIndex: 'channel',
        render: (value: string | null | undefined, row) => (
            <StackedCell primary={value || '未记录渠道'} secondary={`${row.itemCount} 个采购项`}/>
        ),
    },
    {title: '成本', dataIndex: 'totalAmount', width: 110, render: (value: NutritionAmount) => <MoneyText value={value}/>},
]

function BudgetPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [ruleForm] = Form.useForm<NutritionUpsertBudgetRuleRequest>()
    const [weekly, setWeekly] = useState<NutritionBudgetSummaryResponse>()
    const [monthly, setMonthly] = useState<NutritionBudgetSummaryResponse>()
    const [rules, setRules] = useState<NutritionBudgetRuleResponse[]>([])
    const [editingRule, setEditingRule] = useState<NutritionBudgetRuleResponse>()
    const [editorOpen, setEditorOpen] = useState(false)
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:budget:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [weekSummary, monthSummary, ruleRows] = await Promise.all([
                getNutritionWeeklyBudget(familySelection.currentFamilyId),
                getNutritionMonthlyBudget(familySelection.currentFamilyId),
                listNutritionBudgetRules(familySelection.currentFamilyId),
            ])
            setWeekly(weekSummary)
            setMonthly(monthSummary)
            setRules(ruleRows)
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

    function openEditor(rule?: NutritionBudgetRuleResponse) {
        setEditingRule(rule)
        ruleForm.resetFields()
        ruleForm.setFieldsValue(rule ? {
            ruleName: rule.ruleName,
            periodType: rule.periodType,
            amountLimit: rule.amountLimit,
            currency: rule.currency,
            warningThreshold: rule.warningThreshold,
            enabled: rule.enabled,
        } : {
            periodType: 'WEEKLY',
            currency: 'CNY',
            warningThreshold: 0.8,
            enabled: true,
        })
        setEditorOpen(true)
    }

    async function saveRule(values: NutritionUpsertBudgetRuleRequest) {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            const request = {...values, amountLimit: Number(values.amountLimit)}
            if (editingRule) {
                await updateNutritionBudgetRule(familySelection.currentFamilyId!, editingRule.id, request)
            } else {
                await createNutritionBudgetRule(familySelection.currentFamilyId!, request)
            }
            setEditorOpen(false)
            await loadData()
        }, '预算规则已保存')
    }

    async function deactivateRule(ruleId: number) {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            await deactivateNutritionBudgetRule(familySelection.currentFamilyId!, ruleId)
            setRules((current) => current.filter((rule) => rule.id !== ruleId))
            await loadData()
        }, '预算规则已停用')
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

    function ruleActions(rule: NutritionBudgetRuleResponse): RowAction[] {
        return [
            {key: 'edit', label: '编辑', disabled: !canManage, onClick: () => openEditor(rule)},
            {
                key: 'deactivate',
                label: '停用',
                danger: true,
                disabled: !canManage,
                confirm: `确认停用预算规则「${rule.ruleName}」？停用后不再收到超支提醒。`,
                onClick: () => void deactivateRule(rule.id),
            },
        ]
    }

    const ruleColumns: ColumnsType<NutritionBudgetRuleResponse> = [
        {
            title: '规则',
            dataIndex: 'ruleName',
            render: (value: string, rule) => <StackedCell primary={value} secondary={rule.periodType}/>,
        },
        {title: '预算上限', width: 120, render: (_, rule) => <MoneyText value={rule.amountLimit}/>},
        {title: '提醒阈值', width: 100, render: (_, rule) => `${Number(rule.warningThreshold ?? 0) * 100}%`},
        {title: '状态', width: 90, render: (_, rule) => <Tag color={rule.enabled ? 'success' : 'default'}>{rule.enabled ? '启用' : '停用'}</Tag>},
        {title: '操作', width: 150, render: (_, rule) => <RowActions actions={ruleActions(rule)}/>},
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
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新增预算规则</Button>
                    </Space>
                )}
                description="分别查看预算使用率与采购完成率，并维护家庭预算规则。"
                icon={<DollarOutlined/>}
                title="预算分析"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    {rules.length === 0 && (
                        <Alert
                            showIcon
                            title="尚未设置预算规则；当前仍会展示菜单、采购清单和价格形成的成本分析"
                            type="info"
                        />
                    )}
                    <NutritionPageGrid>
                        {weekly && <BudgetSummary title="周预算概览" value={weekly}/>}
                        {monthly && <BudgetSummary title="月预算概览" value={monthly}/>}
                    </NutritionPageGrid>
                    {weekly && (
                        <>
                            <DataTable<NutritionBudgetDailySummaryResponse>
                                columns={dailyColumns}
                                count={weekly.dailySummaries.length}
                                dataSource={weekly.dailySummaries}
                                emptyDescription="本周还没有产生成本，发布菜单并记录采购后即可看到。"
                                emptyTitle="本周暂无每日成本"
                                pagination={false}
                                rowKey="date"
                                size="small"
                                title="本周每日成本"
                            />
                            <DataTable<NutritionBudgetDishSummaryResponse>
                                columns={dishColumns}
                                count={weekly.dishSummaries.length}
                                dataSource={weekly.dishSummaries}
                                emptyDescription="菜单确认关闭后，按菜品分摊的成本会列在这里。"
                                emptyTitle="本周暂无确认后菜单成本"
                                pagination={false}
                                rowKey="itemId"
                                size="small"
                                title="本周确认后菜单成本"
                            />
                            <NutritionPageGrid>
                                <DataTable<NutritionBudgetIngredientSummaryResponse>
                                    columns={ingredientColumns}
                                    count={weekly.ingredientSummaries.length}
                                    dataSource={weekly.ingredientSummaries}
                                    emptyDescription="记录采购价格后即可按食材归集成本。"
                                    emptyTitle="本周暂无食材成本"
                                    pagination={false}
                                    rowKey={(row) => `${row.standardFoodId ?? 'raw'}-${row.rawFoodName}`}
                                    size="small"
                                    title="本周食材成本"
                                />
                                <DataTable<NutritionBudgetChannelSummaryResponse>
                                    columns={channelColumns}
                                    count={weekly.channelSummaries.length}
                                    dataSource={weekly.channelSummaries}
                                    emptyDescription="在采购项上填写渠道后即可比较各渠道支出。"
                                    emptyTitle="本周暂无渠道成本"
                                    pagination={false}
                                    rowKey={(row) => row.channel ?? 'unassigned'}
                                    size="small"
                                    title="本周渠道成本"
                                />
                            </NutritionPageGrid>
                        </>
                    )}
                    <DataTable<NutritionBudgetRuleResponse>
                        columns={ruleColumns}
                        count={rules.length}
                        dataSource={rules}
                        emptyAction={(
                            <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新增预算规则</Button>
                        )}
                        emptyDescription="设置周或月预算上限后，超支会在这里给出提醒。"
                        emptyTitle="还没有预算规则"
                        pagination={false}
                        rowKey="id"
                        size="small"
                        title="预算规则"
                    />
                </NutritionStack>
            </NutritionAsyncState>
            <FormDrawer
                footerHint="提醒阈值是预算上限的比例，例如 0.8 表示用掉 80% 时提醒。"
                formId={ruleFormId}
                loading={saving}
                onClose={() => setEditorOpen(false)}
                open={editorOpen}
                size="sm"
                submitText="保存规则"
                title={editingRule ? '编辑预算规则' : '新增预算规则'}
            >
                <Form form={ruleForm} id={ruleFormId} layout="vertical" onFinish={(values) => void saveRule(values)}>
                    <Form.Item label="规则名称" name="ruleName" rules={[{required: true}]}><Input aria-label="规则名称"/></Form.Item>
                    <Form.Item label="周期类型" name="periodType" rules={[{required: true}]}>
                        <Select aria-label="周期类型" options={[{label: 'WEEKLY', value: 'WEEKLY'}, {label: 'MONTHLY', value: 'MONTHLY'}]}/>
                    </Form.Item>
                    <Form.Item label="预算上限" name="amountLimit" rules={[{required: true}]}><InputNumber aria-label="预算上限" className="u-full-width" min={0.01} precision={2}/></Form.Item>
                    <Form.Item label="币种" name="currency"><Input/></Form.Item>
                    <Form.Item label="提醒阈值" name="warningThreshold"><InputNumber className="u-full-width" max={1} min={0.01} step={0.05}/></Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked"><Switch/></Form.Item>
                </Form>
            </FormDrawer>
        </NutritionStack>
    )
}

function BudgetSummary({title, value}: {title: string; value: NutritionBudgetSummaryResponse}) {
    return (
        <NutritionSection title={title}>
            <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="预算上限"><MoneyText value={value.budgetLimit}/></Descriptions.Item>
                <Descriptions.Item label="菜单数量">{value.mealPlanCount}</Descriptions.Item>
                <Descriptions.Item label="菜品数量">{value.mealCount}</Descriptions.Item>
                <Descriptions.Item label="确认人数">{value.confirmedMemberCount}</Descriptions.Item>
                <Descriptions.Item label="预估成本"><MoneyText value={value.totalEstimatedAmount}/></Descriptions.Item>
                <Descriptions.Item label="实际支出"><MoneyText value={value.totalActualAmount}/></Descriptions.Item>
                <Descriptions.Item label="预算使用率">{value.usageRate}%</Descriptions.Item>
                <Descriptions.Item label="采购完成率">{value.shoppingCompletionRate}%</Descriptions.Item>
                <Descriptions.Item label="人均成本"><MoneyText value={value.perPersonCost}/></Descriptions.Item>
            </Descriptions>
        </NutritionSection>
    )
}

export const Component = BudgetPage
