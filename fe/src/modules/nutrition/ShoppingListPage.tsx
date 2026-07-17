import {PlayCircleOutlined, SaveOutlined, ShoppingCartOutlined} from '@ant-design/icons'
import {Alert, App, Button, Checkbox, Descriptions, Drawer, Form, Input, InputNumber, Space, Table, Tag} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {MoneyText} from './components/MoneyText'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {selectNearestNutritionMealPlan} from './mealPlanSelection'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    closeNutritionMealPlanConfirmation,
    createNutritionPriceRecord,
    generateNutritionShoppingList,
    listNutritionMealPlans,
    listNutritionPriceRecords,
    listNutritionShoppingLists,
    previewNutritionShoppingList,
    transitionNutritionShoppingList,
    updateNutritionShoppingListItem,
} from './nutritionService'
import type {
    NutritionCreateFoodPriceRecordRequest,
    NutritionAmount,
    NutritionFoodPriceRecordResponse,
    NutritionLoadState,
    NutritionMealPlanResponse,
    NutritionShoppingListItemResponse,
    NutritionShoppingListResponse,
} from './nutritionTypes'
import {NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

function ShoppingListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [priceForm] = Form.useForm<NutritionCreateFoodPriceRecordRequest>()
    const [mealPlan, setMealPlan] = useState<NutritionMealPlanResponse>()
    const [shoppingList, setShoppingList] = useState<NutritionShoppingListResponse>()
    const [priceRecords, setPriceRecords] = useState<NutritionFoodPriceRecordResponse[]>([])
    const [preview, setPreview] = useState(false)
    const [priceItem, setPriceItem] = useState<NutritionShoppingListItemResponse>()
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:shopping-list:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const plans = await listNutritionMealPlans(familySelection.currentFamilyId)
            const currentPlan = selectNearestNutritionMealPlan(plans, [
                'PUBLISHED',
                'CONFIRMING',
                'CONFIRM_CLOSED',
                'PREPARING',
                'COMPLETED',
            ])
            if (!currentPlan) {
                setMealPlan(undefined)
                setShoppingList(undefined)
                setState('empty')
                return
            }
            const isPreview = ['PUBLISHED', 'CONFIRMING'].includes(currentPlan.status)
            const [lists, prices] = await Promise.all([
                isPreview
                    ? previewNutritionShoppingList(familySelection.currentFamilyId, currentPlan.id).then((value) => [value])
                    : listNutritionShoppingLists(familySelection.currentFamilyId, currentPlan.id),
                listNutritionPriceRecords(familySelection.currentFamilyId),
            ])
            setMealPlan(currentPlan)
            setShoppingList(lists[0])
            setPriceRecords(prices)
            setPreview(isPreview)
            setState(lists[0] ? 'ready' : 'empty')
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

    async function closeAndGenerate() {
        if (!familySelection.currentFamilyId || !mealPlan) return
        await mutate(async () => {
            const closedPlan = await closeNutritionMealPlanConfirmation(familySelection.currentFamilyId!, mealPlan.id, true)
            const generated = await generateNutritionShoppingList(familySelection.currentFamilyId!, mealPlan.id)
            setMealPlan(closedPlan)
            setShoppingList(generated)
            setPreview(false)
            await loadData()
        }, '正式采购清单已生成')
    }

    async function toggleItem(item: NutritionShoppingListItemResponse, checked: boolean) {
        if (!familySelection.currentFamilyId || !shoppingList) return
        await mutate(async () => {
            const updated = await updateNutritionShoppingListItem(
                familySelection.currentFamilyId!, shoppingList.id, item.id,
                {checked, itemStatus: checked ? 'CHECKED' : 'PENDING'},
            )
            setShoppingList((current) => current ? {
                ...current,
                items: current.items.map((row) => row.id === item.id ? updated : row),
            } : current)
            await loadData()
        }, checked ? '采购项已勾选' : '采购项已取消')
    }

    function openPrice(item: NutritionShoppingListItemResponse) {
        setPriceItem(item)
        priceForm.resetFields()
        priceForm.setFieldsValue({
            channel: item.channel ?? undefined,
            brand: item.brand ?? undefined,
            specAmount: item.specAmount ?? undefined,
            specUnit: item.specUnit ?? undefined,
            totalPrice: item.totalPrice ?? undefined,
        })
    }

    async function savePrice(values: NutritionCreateFoodPriceRecordRequest) {
        if (!familySelection.currentFamilyId || !priceItem) return
        await mutate(async () => {
            const price = await createNutritionPriceRecord(familySelection.currentFamilyId!, {
                ...values,
                shoppingListItemId: priceItem.id,
                standardFoodId: priceItem.standardFoodId ?? undefined,
                rawFoodName: priceItem.rawFoodName,
                totalPrice: Number(values.totalPrice),
            })
            setShoppingList((current) => current ? {
                ...current,
                items: current.items.map((item) => item.id === priceItem.id ? {
                    ...item,
                    channel: price.channel ?? item.channel,
                    brand: price.brand ?? item.brand,
                    normalizedUnitPrice: price.normalizedUnitPrice,
                    totalPrice: price.totalPrice,
                } : item),
            } : current)
            setPriceItem(undefined)
            await loadData()
        }, '价格记录已保存')
    }

    async function startShopping() {
        if (!familySelection.currentFamilyId || !shoppingList) return
        await mutate(async () => {
            setShoppingList(await transitionNutritionShoppingList(
                familySelection.currentFamilyId!, shoppingList.id, {targetStatus: 'PURCHASING'},
            ))
            await loadData()
        }, '采购已开始')
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
                        {preview ? (
                            <Button disabled={!canManage} icon={<ShoppingCartOutlined/>} loading={saving} onClick={() => void closeAndGenerate()} type="primary">关闭确认并生成正式清单</Button>
                        ) : (
                            <Button disabled={!canManage || shoppingList?.status !== 'ACTIVE'} icon={<PlayCircleOutlined/>} loading={saving} onClick={() => void startShopping()} type="primary">开始采购</Button>
                        )}
                    </Space>
                )}
                description="确认关闭前查看预览，关闭后跟踪正式采购项、渠道和价格。"
                title="采购清单"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                emptyDescription="暂无采购清单"
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionSection title={preview ? '预览清单' : '正式清单'}>
                        <Descriptions bordered column={3} size="small">
                            <Descriptions.Item label="清单状态"><Tag>{shoppingList?.status}</Tag></Descriptions.Item>
                            <Descriptions.Item label="预估成本"><MoneyText value={shoppingList?.estimatedTotalPrice}/></Descriptions.Item>
                            <Descriptions.Item label="实际成本"><MoneyText value={shoppingList?.actualTotalPrice}/></Descriptions.Item>
                        </Descriptions>
                    </NutritionSection>
                    <NutritionSection title="采购项">
                        <Table<NutritionShoppingListItemResponse>
                            columns={[
                                {title: '采购项', dataIndex: 'rawFoodName'},
                                {title: '分类', dataIndex: 'category'},
                                {title: '计划数量', render: (_, row) => `${row.plannedAmount ?? '-'}${row.plannedUnit ?? ''}`},
                                {title: '状态', dataIndex: 'itemStatus'},
                                {title: '价格', render: (_, row) => <MoneyText value={row.totalPrice}/>},
                                {title: '操作', render: (_, row) => (
                                    <Space>
                                        <Checkbox
                                            aria-label={`勾选${row.rawFoodName}`}
                                            checked={row.itemStatus === 'CHECKED'}
                                            disabled={preview || !canManage}
                                            onChange={(event) => void toggleItem(row, event.target.checked)}
                                        />
                                        <Button aria-label={`记录${row.rawFoodName}价格`} disabled={preview || !canManage} onClick={() => openPrice(row)} size="small">记录价格</Button>
                                    </Space>
                                )},
                            ]}
                            dataSource={shoppingList?.items ?? []}
                            pagination={false}
                            rowKey="id"
                            scroll={{x: 900}}
                            size="small"
                        />
                    </NutritionSection>
                    <NutritionSection title="历史价格记录">
                        <Table<NutritionFoodPriceRecordResponse>
                            columns={[
                                {title: '食材', dataIndex: 'rawFoodName'},
                                {title: '渠道', dataIndex: 'channel'},
                                {title: '品牌', dataIndex: 'brand'},
                                {title: '总价', dataIndex: 'totalPrice', render: (value: NutritionAmount | null | undefined) => <MoneyText value={value}/>},
                                {title: '归一单价', dataIndex: 'normalizedUnitPrice', render: (value: NutritionAmount | null | undefined) => <MoneyText value={value}/>},
                                {title: '价格日期', dataIndex: 'priceDate'},
                            ]}
                            dataSource={priceRecords}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </NutritionSection>
                </NutritionStack>
            </NutritionAsyncState>
            <Drawer destroyOnHidden loading={saving} onClose={() => setPriceItem(undefined)} open={Boolean(priceItem)} size={480} title={`记录${priceItem?.rawFoodName ?? ''}价格`}>
                <Form form={priceForm} layout="vertical" onFinish={(values) => void savePrice(values)}>
                    <Form.Item label="采购渠道" name="channel"><Input aria-label="采购渠道"/></Form.Item>
                    <Form.Item label="品牌" name="brand"><Input/></Form.Item>
                    <Form.Item label="规格数量" name="specAmount"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="规格单位" name="specUnit"><Input/></Form.Item>
                    <Form.Item label="总价" name="totalPrice" rules={[{required: true}]}><InputNumber aria-label="总价" min={0} precision={2} style={{width: '100%'}}/></Form.Item>
                    <Button htmlType="submit" icon={<SaveOutlined/>} loading={saving} type="primary">保存价格</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

export const Component = ShoppingListPage
