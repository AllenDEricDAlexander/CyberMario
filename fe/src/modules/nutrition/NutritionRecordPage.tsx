import {FileTextOutlined, LineChartOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Descriptions, Form, Input, InputNumber, Select, Space, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {EmptyState} from '../../components/EmptyState'
import {FormDrawer} from '../../components/FormDrawer'
import {PageGrid, PageSection} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
import {StatCard, StatGrid} from '../../components/StatCard'
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
    NutritionTrendPointResponse,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

type AdjustmentFormValues = NutritionRecordAdjustmentRequest & {nutrients: NutritionNutrients}

/** Each drawer footer submits its form by id, so the buttons live outside the `<Form>`. */
const adjustmentFormId = 'nutrition-record-adjustment-form'
const extraFoodFormId = 'nutrition-record-extra-food-form'

const trendColumns: ColumnsType<NutritionTrendPointResponse> = [
    {title: '日期', dataIndex: 'date'},
    {title: '热量', render: (_, point) => `热量：${point.nutrients.calories}`},
    {title: '蛋白', render: (_, point) => `蛋白：${point.nutrients.protein}`},
]

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

    const recordColumns: ColumnsType<NutritionRecordResponse> = [
        {
            title: '成员',
            dataIndex: 'memberProfileId',
            render: (id: number, record) => (
                <StackedCell primary={members.find((entry) => entry.id === id)?.nickname ?? `#${id}`} secondary={record.mealType}/>
            ),
        },
        {title: '热量', render: (_, record) => record.nutrients.calories},
        {title: '蛋白', render: (_, record) => record.nutrients.protein},
        {title: '钠', render: (_, record) => record.nutrients.sodium},
        {title: '风险', width: 90, render: (_, record) => record.riskTags ? <RiskTag value="MEDIUM"/> : '-'},
        {
            title: '操作',
            width: 120,
            render: (_, record) => (
                <Button aria-label={`调整记录 ${record.id}`} disabled={!canManage} onClick={() => openAdjustment(record)} size="small">调整记录</Button>
            ),
        },
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
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={openExtraFood} type="primary">加餐登记</Button>
                    </Space>
                )}
                description="查看每日摄入、修正实际摄入、登记加餐，并生成可追溯家庭报告快照。"
                icon={<LineChartOutlined/>}
                title="营养记录"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <PageSection title="每日目标对比">
                        <StatGrid columns={3}>
                            <StatCard label="已摄入热量" suffix="kcal" value={daily?.totalNutrients.calories ?? '-'}/>
                            <StatCard label="目标热量" suffix="kcal" tone="sky" value={daily?.targetNutrients.calories ?? '-'}/>
                            <StatCard
                                hint="按当日目标扣减后的余量"
                                label="剩余热量"
                                suffix="kcal"
                                tone="amber"
                                value={daily?.remainingNutrients.calories ?? '-'}
                            />
                        </StatGrid>
                    </PageSection>
                    <DataTable<NutritionRecordResponse>
                        columns={recordColumns}
                        count={records.length}
                        dataSource={records}
                        emptyDescription="今天还没有摄入记录，确认菜单或点击「加餐登记」补录。"
                        emptyTitle="今日暂无摄入记录"
                        pagination={false}
                        rowKey="id"
                        size="small"
                        title="每日摄入记录"
                    />
                    <NutritionPageGrid>
                        <NutritionSection
                            extra={<Button disabled={!canManage} icon={<FileTextOutlined/>} loading={saving} onClick={() => void generateWeeklyReport()} type="primary">生成周报告</Button>}
                            title="周报告"
                        >
                            <Descriptions column={1} bordered size="small">
                                <Descriptions.Item label="报告周期">{report?.periodStart} ~ {report?.periodEnd}</Descriptions.Item>
                                <Descriptions.Item label="快照">{report?.snapshotId ? `快照 #${report.snapshotId}` : '实时预览'}</Descriptions.Item>
                                <Descriptions.Item label="实际成本"><MoneyText value={report?.actualCost}/></Descriptions.Item>
                                <Descriptions.Item label="预估成本"><MoneyText value={report?.estimatedCost}/></Descriptions.Item>
                            </Descriptions>
                        </NutritionSection>
                        <NutritionSection
                            extra={<Button disabled={!canManage} icon={<FileTextOutlined/>} loading={saving} onClick={() => void generateMonthlyReport()} type="primary">生成月报告</Button>}
                            title="月报告"
                        >
                            <Descriptions column={1} bordered size="small">
                                <Descriptions.Item label="报告周期">{monthlyReport?.periodStart} ~ {monthlyReport?.periodEnd}</Descriptions.Item>
                                <Descriptions.Item label="快照">{monthlyReport?.snapshotId ? `月度快照 #${monthlyReport.snapshotId}` : '实时预览'}</Descriptions.Item>
                                <Descriptions.Item label="实际成本"><MoneyText value={monthlyReport?.actualCost}/></Descriptions.Item>
                                <Descriptions.Item label="预估成本"><MoneyText value={monthlyReport?.estimatedCost}/></Descriptions.Item>
                            </Descriptions>
                        </NutritionSection>
                        <NutritionSection title="营养提醒">
                            {report?.nutrientReminders?.length
                                ? <Space wrap>{report.nutrientReminders.map((reminder) => <Tag color="warning" key={reminder}>{reminder}</Tag>)}</Space>
                                : <EmptyState description="本周摄入没有触发钠、糖等超标提醒。" inline title="暂无营养提醒"/>}
                        </NutritionSection>
                        <DataTable<NutritionTrendPointResponse>
                            columns={trendColumns}
                            dataSource={report?.trends ?? []}
                            emptyDescription="生成周报告后，这里会按天列出热量与蛋白摄入。"
                            emptyTitle="暂无摄入趋势"
                            pagination={false}
                            rowKey="date"
                            size="small"
                            title="摄入趋势"
                        />
                    </NutritionPageGrid>
                </NutritionStack>
            </NutritionAsyncState>
            <FormDrawer
                footerHint={editingRecord ? `记录 #${editingRecord.id} · ${editingRecord.mealType}` : undefined}
                formId={adjustmentFormId}
                loading={saving}
                onClose={() => setEditingRecord(undefined)}
                open={Boolean(editingRecord)}
                size="md"
                submitText="保存调整"
                title="调整营养记录"
            >
                <Form form={adjustmentForm} id={adjustmentFormId} layout="vertical" onFinish={(values) => void saveAdjustment(values)}>
                    <NutrientFields prefix="调整"/>
                    <Form.Item label="调整原因" name="reason" rules={[{required: true}]}><Input aria-label="调整原因"/></Form.Item>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={extraFoodFormId}
                loading={saving}
                onClose={() => setExtraOpen(false)}
                open={extraOpen}
                size="md"
                submitText="保存加餐"
                title="加餐登记"
            >
                <Form form={extraForm} id={extraFoodFormId} layout="vertical" onFinish={(values) => void saveExtraFood(values)}>
                    <Form.Item label="成员档案" name="memberProfileId" rules={[{required: true}]}>
                        <Select options={members.map((member) => ({label: member.nickname, value: member.id}))}/>
                    </Form.Item>
                    <Form.Item hidden name="recordDate"><Input/></Form.Item>
                    <Form.Item hidden name="mealType"><Input/></Form.Item>
                    <Form.Item label="食物名称" name="foodName" rules={[{required: true}]}><Input aria-label="食物名称"/></Form.Item>
                    <Form.Item label="数量" name="amount" rules={[{required: true}]}><InputNumber aria-label="数量" className="u-full-width" min={0.001}/></Form.Item>
                    <Form.Item label="单位" name="unit" rules={[{required: true}]}><Input aria-label="单位"/></Form.Item>
                    <NutrientFields prefix="加餐"/>
                </Form>
            </FormDrawer>
        </NutritionStack>
    )
}

function NutrientFields({prefix}: {prefix: string}) {
    return (
        // Eight short number fields — a tighter minimum than the page grid keeps them side by side.
        <PageGrid minWidth={160}>
            {nutrientFields.map(({name, label}) => (
                <Form.Item key={name} label={`${prefix}${label}`} name={['nutrients', name]}>
                    <InputNumber aria-label={`${prefix}${label}`} className="u-full-width" min={0}/>
                </Form.Item>
            ))}
        </PageGrid>
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
