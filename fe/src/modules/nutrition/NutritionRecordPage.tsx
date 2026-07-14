import {FileTextOutlined, PlusOutlined, SaveOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {RiskTag} from './components/RiskTag'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    adjustNutritionRecord,
    createNutritionExtraFoodRecord,
    generateNutritionFamilyMonthlyReport,
    generateNutritionFamilyWeeklyReport,
    getNutritionDailyOverview,
    getNutritionFamilyMonthlyReport,
    getNutritionFamilyWeeklyReport,
    listNutritionMembers,
} from './nutritionService'
import type {
    NutritionCreateExtraFoodRecordRequest,
    NutritionDailyOverviewResponse,
    NutritionLoadState,
    NutritionMemberProfileResponse,
    NutritionNutrients,
    NutritionRecordAdjustmentRequest,
    NutritionRecordResponse,
    NutritionReportResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

type AdjustmentFormValues = NutritionRecordAdjustmentRequest & {nutrients: NutritionNutrients}

function NutritionRecordPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [adjustmentForm] = Form.useForm<AdjustmentFormValues>()
    const [extraForm] = Form.useForm<NutritionCreateExtraFoodRecordRequest>()
    const [members, setMembers] = useState<NutritionMemberProfileResponse[]>([])
    const [daily, setDaily] = useState<NutritionDailyOverviewResponse>()
    const [report, setReport] = useState<NutritionReportResponse>()
    const [monthlyReport, setMonthlyReport] = useState<NutritionReportResponse>()
    const [editingRecord, setEditingRecord] = useState<NutritionRecordResponse>()
    const [extraOpen, setExtraOpen] = useState(false)
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:record:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const weekStart = mondayOfCurrentWeek()
            const [memberRows, dailyOverview, weeklyReport, monthReport] = await Promise.all([
                listNutritionMembers(familySelection.currentFamilyId),
                getNutritionDailyOverview(familySelection.currentFamilyId, {date: localDate()}),
                getNutritionFamilyWeeklyReport(familySelection.currentFamilyId, {weekStart}),
                getNutritionFamilyMonthlyReport(familySelection.currentFamilyId, {month: firstDayOfCurrentMonth()}),
            ])
            setMembers(memberRows)
            setDaily(dailyOverview)
            setReport(weeklyReport)
            setMonthlyReport(monthReport)
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

    const records = useMemo(
        () => daily?.memberSummaries.flatMap((summary) => summary.records) ?? [],
        [daily],
    )

    function openAdjustment(record: NutritionRecordResponse) {
        setEditingRecord(record)
        adjustmentForm.resetFields()
        adjustmentForm.setFieldsValue({nutrients: record.nutrients, reason: ''})
    }

    async function saveAdjustment(values: AdjustmentFormValues) {
        if (!familySelection.currentFamilyId || !editingRecord) return
        await mutate(async () => {
            const updated = await adjustNutritionRecord(familySelection.currentFamilyId!, editingRecord.id, values)
            replaceRecord(updated)
            setEditingRecord(undefined)
            await loadData()
        }, '营养记录已调整')
    }

    function openExtraFood() {
        extraForm.resetFields()
        extraForm.setFieldsValue({
            memberProfileId: members[0]?.id,
            recordDate: localDate(),
            mealType: 'SNACK',
            amount: undefined,
            unit: '',
            nutrients: zeroNutrients(),
        })
        setExtraOpen(true)
    }

    async function saveExtraFood(values: NutritionCreateExtraFoodRecordRequest) {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            const created = await createNutritionExtraFoodRecord(familySelection.currentFamilyId!, {
                ...values,
                amount: Number(values.amount),
                nutrients: values.nutrients ?? zeroNutrients(),
            })
            appendRecord(created)
            setExtraOpen(false)
            await loadData()
        }, '加餐已登记')
    }

    async function generateWeeklyReport() {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            setReport(await generateNutritionFamilyWeeklyReport(
                familySelection.currentFamilyId!, {weekStart: mondayOfCurrentWeek()},
            ))
            await loadData()
        }, '周报告快照已生成')
    }

    async function generateMonthlyReport() {
        if (!familySelection.currentFamilyId) return
        await mutate(async () => {
            setMonthlyReport(await generateNutritionFamilyMonthlyReport(
                familySelection.currentFamilyId!, {month: firstDayOfCurrentMonth()},
            ))
            await loadData()
        }, '月报告快照已生成')
    }

    function replaceRecord(updated: NutritionRecordResponse) {
        setDaily((current) => current ? {
            ...current,
            memberSummaries: current.memberSummaries.map((summary) => ({
                ...summary,
                records: summary.records.map((record) => record.id === updated.id ? updated : record),
            })),
        } : current)
    }

    function appendRecord(created: NutritionRecordResponse) {
        setDaily((current) => current ? {
            ...current,
            memberSummaries: current.memberSummaries.map((summary) => summary.memberProfileId === created.memberProfileId
                ? {...summary, records: [created, ...summary.records]}
                : summary),
        } : current)
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
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={openExtraFood} type="primary">加餐登记</Button>
                    </Space>
                )}
                description="查看每日摄入、修正实际摄入、登记加餐，并生成可追溯家庭报告快照。"
                title="营养记录"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionSection title="每日目标对比">
                        <Descriptions bordered column={3} size="small">
                            <Descriptions.Item label="已摄入热量">{daily?.totalNutrients.calories} kcal</Descriptions.Item>
                            <Descriptions.Item label="目标热量">{daily?.targetNutrients.calories} kcal</Descriptions.Item>
                            <Descriptions.Item label="剩余热量">{daily?.remainingNutrients.calories} kcal</Descriptions.Item>
                        </Descriptions>
                    </NutritionSection>
                    <NutritionSection title="每日摄入记录">
                        <Table<NutritionRecordResponse>
                            columns={[
                                {title: '成员', dataIndex: 'memberProfileId'},
                                {title: '餐次', dataIndex: 'mealType'},
                                {title: '热量', render: (_, record) => record.nutrients.calories},
                                {title: '蛋白', render: (_, record) => record.nutrients.protein},
                                {title: '钠', render: (_, record) => record.nutrients.sodium},
                                {title: '风险', render: (_, record) => record.riskTags ? <RiskTag value="MEDIUM"/> : '-'},
                                {title: '操作', render: (_, record) => (
                                    <Button aria-label={`调整记录 ${record.id}`} disabled={!canManage} onClick={() => openAdjustment(record)} size="small">调整记录</Button>
                                )},
                            ]}
                            dataSource={records}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                    <NutritionPageGrid>
                        <NutritionSection title="周报告">
                            <Descriptions column={1} bordered size="small">
                                <Descriptions.Item label="报告周期">{report?.periodStart} ~ {report?.periodEnd}</Descriptions.Item>
                                <Descriptions.Item label="快照">{report?.snapshotId ? `快照 #${report.snapshotId}` : '实时预览'}</Descriptions.Item>
                                <Descriptions.Item label="实际成本"><MoneyText value={report?.actualCost}/></Descriptions.Item>
                                <Descriptions.Item label="预估成本"><MoneyText value={report?.estimatedCost}/></Descriptions.Item>
                            </Descriptions>
                            <Button disabled={!canManage} icon={<FileTextOutlined/>} loading={saving} onClick={() => void generateWeeklyReport()} style={{marginTop: 12}} type="primary">生成周报告</Button>
                        </NutritionSection>
                        <NutritionSection title="月报告">
                            <Descriptions column={1} bordered size="small">
                                <Descriptions.Item label="报告周期">{monthlyReport?.periodStart} ~ {monthlyReport?.periodEnd}</Descriptions.Item>
                                <Descriptions.Item label="快照">{monthlyReport?.snapshotId ? `月度快照 #${monthlyReport.snapshotId}` : '实时预览'}</Descriptions.Item>
                                <Descriptions.Item label="实际成本"><MoneyText value={monthlyReport?.actualCost}/></Descriptions.Item>
                                <Descriptions.Item label="预估成本"><MoneyText value={monthlyReport?.estimatedCost}/></Descriptions.Item>
                            </Descriptions>
                            <Button disabled={!canManage} icon={<FileTextOutlined/>} loading={saving} onClick={() => void generateMonthlyReport()} style={{marginTop: 12}} type="primary">生成月报告</Button>
                        </NutritionSection>
                        <NutritionSection title="营养提醒">
                            <Space wrap>{(report?.nutrientReminders ?? []).map((reminder) => <Tag color="warning" key={reminder}>{reminder}</Tag>)}</Space>
                        </NutritionSection>
                        <NutritionSection title="摄入趋势">
                            <Table
                                columns={[
                                    {title: '日期', dataIndex: 'date'},
                                    {title: '热量', render: (_, point) => `热量：${point.nutrients.calories}`},
                                    {title: '蛋白', render: (_, point) => `蛋白：${point.nutrients.protein}`},
                                ]}
                                dataSource={report?.trends ?? []}
                                pagination={false}
                                rowKey="date"
                                size="small"
                            />
                        </NutritionSection>
                    </NutritionPageGrid>
                </NutritionStack>
            </NutritionAsyncState>
            <Drawer destroyOnHidden loading={saving} onClose={() => setEditingRecord(undefined)} open={Boolean(editingRecord)} size={520} title="调整营养记录">
                <Form form={adjustmentForm} layout="vertical" onFinish={(values) => void saveAdjustment(values)}>
                    <NutrientFields prefix="调整"/>
                    <Form.Item label="调整原因" name="reason" rules={[{required: true}]}><Input aria-label="调整原因"/></Form.Item>
                    <Button htmlType="submit" icon={<SaveOutlined/>} loading={saving} type="primary">保存调整</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden loading={saving} onClose={() => setExtraOpen(false)} open={extraOpen} size={520} title="加餐登记">
                <Form form={extraForm} layout="vertical" onFinish={(values) => void saveExtraFood(values)}>
                    <Form.Item label="成员档案" name="memberProfileId" rules={[{required: true}]}>
                        <Select options={members.map((member) => ({label: member.nickname, value: member.id}))}/>
                    </Form.Item>
                    <Form.Item hidden name="recordDate"><Input/></Form.Item>
                    <Form.Item hidden name="mealType"><Input/></Form.Item>
                    <Form.Item label="食物名称" name="foodName" rules={[{required: true}]}><Input aria-label="食物名称"/></Form.Item>
                    <Form.Item label="数量" name="amount" rules={[{required: true}]}><InputNumber aria-label="数量" min={0.001} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="单位" name="unit" rules={[{required: true}]}><Input aria-label="单位"/></Form.Item>
                    <NutrientFields prefix="加餐"/>
                    <Button htmlType="submit" icon={<SaveOutlined/>} loading={saving} type="primary">保存加餐</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

function NutrientFields({prefix}: {prefix: string}) {
    return (
        <NutritionPageGrid>
            {nutrientFields.map(({name, label}) => (
                <Form.Item key={name} label={`${prefix}${label}`} name={['nutrients', name]}>
                    <InputNumber aria-label={`${prefix}${label}`} min={0} style={{width: '100%'}}/>
                </Form.Item>
            ))}
        </NutritionPageGrid>
    )
}

const nutrientFields: Array<{name: keyof NutritionNutrients; label: string}> = [
    {name: 'calories', label: '热量'},
    {name: 'protein', label: '蛋白'},
    {name: 'fat', label: '脂肪'},
    {name: 'carbs', label: '碳水'},
    {name: 'sugar', label: '糖'},
    {name: 'sodium', label: '钠'},
    {name: 'fiber', label: '纤维'},
    {name: 'cholesterol', label: '胆固醇'},
]

function zeroNutrients(): NutritionNutrients {
    return {calories: 0, protein: 0, fat: 0, carbs: 0, sugar: 0, sodium: 0, fiber: 0, cholesterol: 0}
}

function localDate(date = new Date()) {
    return date.toLocaleDateString('en-CA')
}

function mondayOfCurrentWeek() {
    const date = new Date()
    const day = date.getDay() || 7
    date.setDate(date.getDate() - day + 1)
    return localDate(date)
}

function firstDayOfCurrentMonth() {
    const date = new Date()
    date.setDate(1)
    return localDate(date)
}

export const Component = NutritionRecordPage
