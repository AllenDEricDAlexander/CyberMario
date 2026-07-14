import {LinkOutlined, PlusOutlined, SafetyCertificateOutlined} from '@ant-design/icons'
import {Alert, App, Button, Drawer, Form, Input, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
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
    NutritionLoadState,
    NutritionScopedRoleBindingResponse,
    NutritionUpdateFamilySettingsRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

type AdministrationAction = 'clan' | 'family' | 'association' | 'role' | 'grant'
type AdministrationActionFormValues = {
    name?: string
    region?: string
    currency?: string
    ownerNickname?: string
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
                    ownerNickname: stringValue(values.ownerNickname),
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
        {title: '用户', dataIndex: 'subjectId', width: 100},
        {title: '角色', dataIndex: 'roleCode'},
        {title: '范围', dataIndex: 'scopeType', width: 120},
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
        {title: '授权对象', render: (_, record) => `${record.granteeType} #${record.granteeId}`},
        {title: '数据范围', dataIndex: 'dataScope'},
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
        {title: 'Clan ID', dataIndex: 'clanId'},
        {title: '状态', dataIndex: 'relationStatus', render: (value) => <Tag color="success">{value}</Tag>},
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
                    </Space>
                )}
                description="管理 Clan、家庭设置、关联关系、家庭角色和显式数据授权。"
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
                        <NutritionSection
                            extra={<Button disabled={!canManage} onClick={() => setAction('clan')} size="small">新建 Clan</Button>}
                            title="Clan 列表"
                        >
                            <Table<NutritionClanResponse>
                                columns={[
                                    {title: 'Clan 名称', dataIndex: 'name'},
                                    {title: 'Owner', dataIndex: 'ownerUserId', width: 100},
                                ]}
                                dataSource={clans}
                                pagination={false}
                                rowKey="id"
                                size="small"
                            />
                        </NutritionSection>
                        <NutritionSection title="家庭列表">
                            <Table
                                columns={[
                                    {title: '家庭名称', dataIndex: 'name'},
                                    {title: '地区', dataIndex: 'region'},
                                    {title: 'AI', dataIndex: 'aiEnabled', render: (value) => value ? '开启' : '关闭'},
                                ]}
                                dataSource={familySelection.families}
                                pagination={false}
                                rowKey="id"
                                size="small"
                            />
                        </NutritionSection>
                    </NutritionPageGrid>
                    <NutritionSection
                        extra={<Button disabled={!canManage} icon={<LinkOutlined/>} onClick={() => setAction('association')} size="small">关联家庭</Button>}
                        title="关联关系"
                    >
                        <Table columns={relationColumns} dataSource={relations} pagination={false} rowKey="id" size="small"/>
                    </NutritionSection>
                    <NutritionPageGrid>
                        <NutritionSection
                            extra={<Button disabled={!canManage} icon={<SafetyCertificateOutlined/>} onClick={() => setAction('role')} size="small">新增角色</Button>}
                            title="家庭角色"
                        >
                            <Table columns={roleColumns} dataSource={roles} pagination={false} rowKey="id" size="small"/>
                        </NutritionSection>
                        <NutritionSection
                            extra={<Button disabled={!canManage} onClick={() => setAction('grant')} size="small">新增授权</Button>}
                            title="数据授权"
                        >
                            <Table columns={grantColumns} dataSource={grants} pagination={false} rowKey="id" size="small"/>
                        </NutritionSection>
                    </NutritionPageGrid>
                </NutritionStack>
            </NutritionAsyncState>
            <Drawer
                destroyOnHidden
                loading={saving}
                onClose={() => setSettingsOpen(false)}
                open={settingsOpen}
                title="家庭设置"
                size={480}
            >
                <Form form={settingsForm} layout="vertical" onFinish={(values) => void saveSettings(values)}>
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
                    <Button htmlType="submit" loading={saving} type="primary">保存</Button>
                </Form>
            </Drawer>
            <Drawer
                destroyOnHidden
                onClose={() => setAction(undefined)}
                open={Boolean(action)}
                title={actionTitle(action)}
                size={480}
            >
                <Form form={actionForm} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    {action === 'clan' && <Form.Item label="Clan 名称" name="name" rules={[{required: true}]}><Input/></Form.Item>}
                    {action === 'family' && <>
                        <Form.Item label="家庭名称" name="name" rules={[{required: true}]}><Input/></Form.Item>
                        <Form.Item label="地区" name="region"><Input/></Form.Item>
                        <Form.Item label="币种" name="currency" initialValue="CNY"><Input/></Form.Item>
                        <Form.Item label="家庭所有者昵称" name="ownerNickname"><Input/></Form.Item>
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
                    <Button htmlType="submit" loading={saving} type="primary">保存</Button>
                </Form>
            </Drawer>
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
