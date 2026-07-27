import {ApartmentOutlined, DeleteOutlined, LinkOutlined, PlusOutlined, SafetyCertificateOutlined} from '@ant-design/icons'
import {Alert, App, Button, Form, Input, Popconfirm, Select, Space, Switch, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {FormDrawer} from '../../components/FormDrawer'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    associateNutritionClanFamily,
    createNutritionClan,
    createNutritionDataGrant,
    createNutritionFamily,
    createNutritionRoleBinding,
    deleteNutritionFamily,
    listNutritionClanFamilyRelations,
    listNutritionClans,
    listNutritionDataGrants,
    listNutritionRoleBindings,
    removeNutritionClanFamilyRelation,
    revokeNutritionDataGrant,
    revokeNutritionRoleBinding,
    updateNutritionFamilySettings,
} from './nutritionService'
import type {
    NutritionClanFamilyRelationResponse,
    NutritionClanResponse,
    NutritionCreateDataGrantRequest,
    NutritionCreateScopedRoleBindingRequest,
    NutritionDataGrantResponse,
    NutritionFamilyResponse,
    NutritionLoadState,
    NutritionScopedRoleBindingResponse,
    NutritionUpdateFamilySettingsRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

/** Each drawer footer submits its form by id, so the buttons live outside the `<Form>`. */
const settingsFormId = 'nutrition-family-settings-form'
const actionFormId = 'nutrition-family-action-form'

const clanColumns: ColumnsType<NutritionClanResponse> = [
    {
        title: 'Clan',
        dataIndex: 'name',
        render: (value: string, record) => <StackedCell primary={value} secondary={`Owner #${record.ownerUserId}`}/>,
    },
    {title: '状态', dataIndex: 'status', width: 100, render: (value) => <Tag>{value}</Tag>},
]

const familyColumns: ColumnsType<NutritionFamilyResponse> = [
    {
        title: '家庭',
        dataIndex: 'name',
        render: (value: string, record) => <StackedCell primary={value} secondary={record.region || '未设置地区'}/>,
    },
    {
        title: 'AI',
        dataIndex: 'aiEnabled',
        width: 90,
        render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '开启' : '关闭'}</Tag>,
    },
]

type AdministrationAction = 'clan' | 'family' | 'association' | 'role' | 'grant'
type AdministrationActionFormValues = {
    name?: string
    region?: string
    currency?: string
    clanId?: number
    familyId?: number
    subjectId?: number
    roleCode?: string
    granteeType?: string
    granteeId?: number
    dataScope?: string
    permissionLevel?: string
}

function ClanFamilyPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [settingsForm] = Form.useForm<NutritionUpdateFamilySettingsRequest>()
    const [actionForm] = Form.useForm<AdministrationActionFormValues>()
    const [clans, setClans] = useState<NutritionClanResponse[]>([])
    const [relations, setRelations] = useState<NutritionClanFamilyRelationResponse[]>([])
    const [roles, setRoles] = useState<NutritionScopedRoleBindingResponse[]>([])
    const [grants, setGrants] = useState<NutritionDataGrantResponse[]>([])
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [settingsOpen, setSettingsOpen] = useState(false)
    const [action, setAction] = useState<AdministrationAction>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:family:manage')
        || auth.hasPermission(nutritionApiCodes.family)
    const canDeleteCurrentFamily = Boolean(
        familySelection.currentFamily && auth.user?.id === familySelection.currentFamily.ownerUserId,
    )

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [clanRows, relationRows, roleRows, grantRows] = await Promise.all([
                listNutritionClans(),
                listNutritionClanFamilyRelations(familySelection.currentFamilyId),
                listNutritionRoleBindings(familySelection.currentFamilyId),
                listNutritionDataGrants(familySelection.currentFamilyId),
            ])
            setClans(clanRows)
            setRelations(relationRows)
            setRoles(roleRows)
            setGrants(grantRows)
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

    function openSettings() {
        if (!familySelection.currentFamily) return
        settingsForm.setFieldsValue({
            region: familySelection.currentFamily.region ?? undefined,
            currency: familySelection.currentFamily.currency ?? 'CNY',
            defaultMealTypes: familySelection.currentFamily.defaultMealTypes,
            aiEnabled: familySelection.currentFamily.aiEnabled,
            aiGenerateTime: familySelection.currentFamily.aiGenerateTime,
            healthAlertEnabled: familySelection.currentFamily.healthAlertEnabled,
            budgetEnabled: familySelection.currentFamily.budgetEnabled,
        })
        setSettingsOpen(true)
    }

    async function saveSettings(values: NutritionUpdateFamilySettingsRequest) {
        if (!familySelection.currentFamilyId) return
        const familyId = familySelection.currentFamilyId
        await mutate(async () => {
            await updateNutritionFamilySettings(familyId, values)
            setSettingsOpen(false)
            await familySelection.reload()
        }, '家庭设置已保存')
    }

    async function deleteCurrentFamily() {
        if (!familySelection.currentFamilyId) return
        const familyId = familySelection.currentFamilyId
        await mutate(async () => {
            await deleteNutritionFamily(familyId)
            setSettingsOpen(false)
            await familySelection.reload()
        }, '家庭及其关联数据已删除')
    }

    async function submitAction(values: AdministrationActionFormValues) {
        if (!action) return
        await mutate(async () => {
            if (action === 'clan') {
                await createNutritionClan({name: String(values.name)})
            } else if (action === 'family') {
                await createNutritionFamily({
                    name: String(values.name),
                    region: stringValue(values.region),
                    currency: stringValue(values.currency),
                })
                await familySelection.reload()
            } else if (action === 'association') {
                await associateNutritionClanFamily(Number(values.clanId), Number(values.familyId))
            } else if (action === 'role' && familySelection.currentFamilyId) {
                await createNutritionRoleBinding(familySelection.currentFamilyId, {
                    subjectType: 'USER',
                    subjectId: Number(values.subjectId),
                    roleCode: String(values.roleCode) as NutritionCreateScopedRoleBindingRequest['roleCode'],
                    scopeType: 'FAMILY',
                    scopeId: familySelection.currentFamilyId,
                })
            } else if (action === 'grant' && familySelection.currentFamilyId) {
                await createNutritionDataGrant(familySelection.currentFamilyId, {
                    granteeType: String(values.granteeType) as NutritionCreateDataGrantRequest['granteeType'],
                    granteeId: Number(values.granteeId),
                    dataScope: String(values.dataScope) as NutritionCreateDataGrantRequest['dataScope'],
                    permissionLevel: String(values.permissionLevel) as NutritionCreateDataGrantRequest['permissionLevel'],
                })
            }
            setAction(undefined)
            actionForm.resetFields()
            await loadData()
        }, '管理数据已保存')
    }

    async function mutate(operation: () => Promise<void>, success: string) {
        setSaving(true)
        setMutationError(undefined)
        try {
            await operation()
            void message.success(success)
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    const roleColumns: ColumnsType<NutritionScopedRoleBindingResponse> = [
        {
            title: '角色',
            dataIndex: 'roleCode',
            render: (value: string, record) => (
                <StackedCell primary={value} secondary={`用户 #${record.subjectId} · ${record.scopeType}`}/>
            ),
        },
        {
            title: '操作', width: 110, render: (_, record) => (
                <Button
                    aria-label={`撤销角色 ${record.id}`}
                    disabled={!canManage}
                    onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await revokeNutritionRoleBinding(familySelection.currentFamilyId, record.id)
                        await loadData()
                    }, '角色已撤销')}
                    size="small"
                >撤销</Button>
            ),
        },
    ]
    const grantColumns: ColumnsType<NutritionDataGrantResponse> = [
        {
            title: '授权对象',
            render: (_, record) => (
                <StackedCell primary={`${record.granteeType} #${record.granteeId}`} secondary={record.dataScope}/>
            ),
        },
        {title: '权限', dataIndex: 'permissionLevel', width: 100},
        {
            title: '操作', width: 110, render: (_, record) => (
                <Button
                    aria-label={`撤销授权 ${record.id}`}
                    disabled={!canManage}
                    onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await revokeNutritionDataGrant(familySelection.currentFamilyId, record.id)
                        await loadData()
                    }, '授权已撤销')}
                    size="small"
                >撤销</Button>
            ),
        },
    ]
    const relationColumns: ColumnsType<NutritionClanFamilyRelationResponse> = [
        {
            title: '关联 Clan',
            dataIndex: 'clanId',
            render: (value: number, record) => (
                <StackedCell primary={`Clan #${value}`} secondary={record.joinedAt ? `加入于 ${record.joinedAt}` : '未记录加入时间'}/>
            ),
        },
        {title: '状态', dataIndex: 'relationStatus', width: 110, render: (value) => <Tag color="success">{value}</Tag>},
        {
            title: '操作', width: 110, render: (_, record) => (
                <Button
                    disabled={!canManage}
                    onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await removeNutritionClanFamilyRelation(familySelection.currentFamilyId, record.id)
                        await loadData()
                    }, '关联已移除')}
                    size="small"
                >移除</Button>
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
                        <Button disabled={!canManage} onClick={openSettings}>编辑设置</Button>
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={() => setAction('family')}>新建家庭</Button>
                        {canDeleteCurrentFamily && <Popconfirm
                            description="该操作会清理成员、健康、菜谱、餐单、购物、预算和营养记录，且无法恢复。"
                            okButtonProps={{danger: true}}
                            okText="确认删除"
                            onConfirm={() => void deleteCurrentFamily()}
                            title={`删除家庭“${familySelection.currentFamily?.name ?? ''}”？`}
                        >
                            <Button danger icon={<DeleteOutlined/>}>删除家庭</Button>
                        </Popconfirm>}
                    </Space>
                )}
                description="管理 Clan、家庭设置、关联关系、家庭角色和显式数据授权。"
                icon={<ApartmentOutlined/>}
                title="家庭营养"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionStack>
                    <NutritionPageGrid>
                        <DataTable<NutritionClanResponse>
                            columns={clanColumns}
                            count={clans.length}
                            dataSource={clans}
                            emptyDescription="Clan 用来把多个家庭编成一族；先新建 Clan，再在下方关联家庭。"
                            emptyTitle="还没有 Clan"
                            pagination={false}
                            rowKey="id"
                            size="small"
                            title="Clan 列表"
                            toolbar={<Button disabled={!canManage} onClick={() => setAction('clan')} size="small">新建 Clan</Button>}
                        />
                        <DataTable<NutritionFamilyResponse>
                            columns={familyColumns}
                            count={familySelection.families.length}
                            dataSource={familySelection.families}
                            emptyDescription="点击右上角「新建家庭」创建，创建后即可维护成员、菜单与预算。"
                            emptyTitle="还没有可管理的家庭"
                            pagination={false}
                            rowKey="id"
                            size="small"
                            title="家庭列表"
                        />
                    </NutritionPageGrid>
                    <DataTable<NutritionClanFamilyRelationResponse>
                        columns={relationColumns}
                        count={relations.length}
                        dataSource={relations}
                        emptyDescription="点击「关联家庭」把当前家庭挂到某个 Clan 下，之后可共享 Clan 级授权。"
                        emptyTitle="当前家庭尚未关联 Clan"
                        pagination={false}
                        rowKey="id"
                        size="small"
                        title="关联关系"
                        toolbar={<Button disabled={!canManage} icon={<LinkOutlined/>} onClick={() => setAction('association')} size="small">关联家庭</Button>}
                    />
                    <NutritionPageGrid>
                        <DataTable<NutritionScopedRoleBindingResponse>
                            columns={roleColumns}
                            count={roles.length}
                            dataSource={roles}
                            emptyDescription="点击「新增角色」把成员设为家庭管理员、厨师或监护人。"
                            emptyTitle="还没有家庭角色"
                            pagination={false}
                            rowKey="id"
                            size="small"
                            title="家庭角色"
                            toolbar={<Button disabled={!canManage} icon={<SafetyCertificateOutlined/>} onClick={() => setAction('role')} size="small">新增角色</Button>}
                        />
                        <DataTable<NutritionDataGrantResponse>
                            columns={grantColumns}
                            count={grants.length}
                            dataSource={grants}
                            emptyDescription="点击「新增授权」把健康档案、菜单或预算按范围显式授权给某位用户或 Clan。"
                            emptyTitle="还没有数据授权"
                            pagination={false}
                            rowKey="id"
                            size="small"
                            title="数据授权"
                            toolbar={<Button disabled={!canManage} onClick={() => setAction('grant')} size="small">新增授权</Button>}
                        />
                    </NutritionPageGrid>
                </NutritionStack>
            </NutritionAsyncState>
            <FormDrawer
                footerHint="设置只影响当前家庭；AI 生成时间使用 24 小时制。"
                formId={settingsFormId}
                loading={saving}
                onClose={() => setSettingsOpen(false)}
                open={settingsOpen}
                size="md"
                title="家庭设置"
            >
                <Form form={settingsForm} id={settingsFormId} layout="vertical" onFinish={(values) => void saveSettings(values)}>
                    <Form.Item label="地区" name="region"><Input/></Form.Item>
                    <Form.Item label="币种" name="currency"><Input maxLength={3}/></Form.Item>
                    <Form.Item label="默认餐次" name="defaultMealTypes">
                        <Select mode="multiple" options={mealTypeOptions}/>
                    </Form.Item>
                    <Form.Item label="启用 AI" name="aiEnabled" valuePropName="checked">
                        <Switch aria-label="启用 AI"/>
                    </Form.Item>
                    <Form.Item label="AI 生成时间" name="aiGenerateTime"><Input placeholder="07:30:00"/></Form.Item>
                    <Form.Item label="健康提醒" name="healthAlertEnabled" valuePropName="checked"><Switch/></Form.Item>
                    <Form.Item label="预算管理" name="budgetEnabled" valuePropName="checked"><Switch/></Form.Item>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={actionFormId}
                loading={saving}
                onClose={() => setAction(undefined)}
                open={Boolean(action)}
                size="sm"
                submitText="保存"
                title={actionTitle(action)}
            >
                <Form form={actionForm} id={actionFormId} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    {action === 'clan' && <Form.Item label="Clan 名称" name="name" rules={[{required: true}]}><Input/></Form.Item>}
                    {action === 'family' && <>
                        <Form.Item label="家庭名称" name="name" rules={[{required: true}]}><Input/></Form.Item>
                        <Form.Item label="地区" name="region"><Input/></Form.Item>
                        <Form.Item label="币种" name="currency" initialValue="CNY"><Input/></Form.Item>
                    </>}
                    {action === 'association' && <>
                        <Form.Item label="Clan" name="clanId" rules={[{required: true}]}>
                            <Select options={clans.map((clan) => ({label: clan.name, value: clan.id}))}/>
                        </Form.Item>
                        <Form.Item label="家庭" name="familyId" initialValue={familySelection.currentFamilyId} rules={[{required: true}]}>
                            <Select options={familySelection.families.map((family) => ({label: family.name, value: family.id}))}/>
                        </Form.Item>
                    </>}
                    {action === 'role' && <>
                        <Form.Item label="用户 ID" name="subjectId" rules={[{required: true}]}><Input type="number"/></Form.Item>
                        <Form.Item label="角色" name="roleCode" rules={[{required: true}]}>
                            <Select options={roleOptions}/>
                        </Form.Item>
                    </>}
                    {action === 'grant' && <>
                        <Form.Item label="授权对象类型" name="granteeType" initialValue="USER" rules={[{required: true}]}>
                            <Select options={[{label: '用户', value: 'USER'}, {label: 'Clan', value: 'CLAN'}]}/>
                        </Form.Item>
                        <Form.Item label="授权对象 ID" name="granteeId" rules={[{required: true}]}><Input type="number"/></Form.Item>
                        <Form.Item label="数据范围" name="dataScope" rules={[{required: true}]}><Select options={grantScopeOptions}/></Form.Item>
                        <Form.Item label="权限级别" name="permissionLevel" rules={[{required: true}]}><Select options={permissionOptions}/></Form.Item>
                    </>}
                </Form>
            </FormDrawer>
        </NutritionStack>
    )
}

const mealTypeOptions = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'].map((value) => ({label: value, value}))
const roleOptions = ['FAMILY_ADMIN', 'COOK', 'MEMBER', 'GUARDIAN'].map((value) => ({label: value, value}))
const grantScopeOptions = ['FAMILY', 'MEMBER_PROFILE', 'HEALTH_PROFILE', 'MEAL_PLAN', 'SHOPPING_LIST', 'BUDGET', 'NUTRITION_RECORD']
    .map((value) => ({label: value, value}))
const permissionOptions = ['READ', 'WRITE', 'MANAGE'].map((value) => ({label: value, value}))

function actionTitle(action?: AdministrationAction) {
    return {
        clan: '新建 Clan',
        family: '新建家庭',
        association: '关联家庭',
        role: '新增角色',
        grant: '新增授权',
    }[action ?? 'clan']
}

function stringValue(value: unknown) {
    return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

export const Component = ClanFamilyPage
