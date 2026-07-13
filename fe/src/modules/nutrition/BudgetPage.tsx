import {DollarOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Switch, Table, Tag} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
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
    NutritionBudgetRuleResponse,
    NutritionBudgetSummaryResponse,
    NutritionLoadState,
    NutritionUpsertBudgetRuleRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

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
                title="预算分析"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionPageGrid>
                        {weekly && <BudgetSummary title="周预算概览" value={weekly}/>}
                        {monthly && <BudgetSummary title="月预算概览" value={monthly}/>}
                    </NutritionPageGrid>
                    <NutritionSection title="预算规则">
                        <Table<NutritionBudgetRuleResponse>
                            columns={[
                                {title: '规则', dataIndex: 'ruleName'},
                                {title: '周期', dataIndex: 'periodType'},
                                {title: '预算上限', render: (_, rule) => <MoneyText value={rule.amountLimit}/>},
                                {title: '提醒阈值', render: (_, rule) => `${Number(rule.warningThreshold ?? 0) * 100}%`},
                                {title: '状态', render: (_, rule) => <Tag color={rule.enabled ? 'success' : 'default'}>{rule.enabled ? '启用' : '停用'}</Tag>},
                                {title: '操作', render: (_, rule) => (
                                    <Space>
                                        <Button aria-label={`编辑预算规则 ${rule.id}`} disabled={!canManage} onClick={() => openEditor(rule)} size="small">编辑</Button>
                                        <Button aria-label={`停用预算规则 ${rule.id}`} danger disabled={!canManage} onClick={() => void deactivateRule(rule.id)} size="small">停用</Button>
                                    </Space>
                                )},
                            ]}
                            dataSource={rules}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                </NutritionStack>
            </NutritionAsyncState>
            <Drawer destroyOnHidden loading={saving} onClose={() => setEditorOpen(false)} open={editorOpen} size={480} title={editingRule ? '编辑预算规则' : '新增预算规则'}>
                <Form form={ruleForm} layout="vertical" onFinish={(values) => void saveRule(values)}>
                    <Form.Item label="规则名称" name="ruleName" rules={[{required: true}]}><Input aria-label="规则名称"/></Form.Item>
                    <Form.Item label="周期类型" name="periodType" rules={[{required: true}]}>
                        <Select aria-label="周期类型" options={[{label: 'WEEKLY', value: 'WEEKLY'}, {label: 'MONTHLY', value: 'MONTHLY'}]}/>
                    </Form.Item>
                    <Form.Item label="预算上限" name="amountLimit" rules={[{required: true}]}><InputNumber aria-label="预算上限" min={0.01} precision={2} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="币种" name="currency"><Input/></Form.Item>
                    <Form.Item label="提醒阈值" name="warningThreshold"><InputNumber max={1} min={0.01} step={0.05} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked"><Switch/></Form.Item>
                    <Button htmlType="submit" icon={<DollarOutlined/>} loading={saving} type="primary">保存规则</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

function BudgetSummary({title, value}: {title: string; value: NutritionBudgetSummaryResponse}) {
    return (
        <NutritionSection title={title}>
            <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="预算上限"><MoneyText value={value.budgetLimit}/></Descriptions.Item>
                <Descriptions.Item label="实际支出"><MoneyText value={value.totalActualAmount}/></Descriptions.Item>
                <Descriptions.Item label="预算使用率">{value.usageRate}%</Descriptions.Item>
                <Descriptions.Item label="采购完成率">{value.shoppingCompletionRate}%</Descriptions.Item>
                <Descriptions.Item label="人均成本"><MoneyText value={value.perPersonCost}/></Descriptions.Item>
            </Descriptions>
        </NutritionSection>
    )
}

export const Component = BudgetPage
