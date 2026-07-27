import {PlusOutlined, TeamOutlined} from '@ant-design/icons'
import {Alert, App, Button, Form, Input, InputNumber, Select, Space, Switch, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {FormDrawer} from '../../components/FormDrawer'
import {PageGrid} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions, type RowAction} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {CurrentFamilySelect} from './components/CurrentFamilySelect'
import {NutritionAsyncState, nutritionLoadFailure} from './components/NutritionAsyncState'
import {nutritionApiCodes} from './nutritionPermissionCodes'
import {
    assignNutritionProfileGuardian,
    bindNutritionMemberUser,
    createNutritionMemberProfile,
    deactivateNutritionMemberProfile,
    listNutritionFamilyHealthTags,
    listNutritionHealthProfiles,
    listNutritionMembers,
    unbindNutritionMemberUser,
    updateNutritionHealthProfile,
    updateNutritionMemberProfile,
} from './nutritionService'
import type {
    NutritionHealthProfileResponse,
    NutritionHealthTagResponse,
    NutritionLoadState,
    NutritionMemberProfileResponse,
    NutritionUpdateHealthProfileRequest,
    NutritionUpdateMemberProfileRequest,
} from './nutritionTypes'
import {NutritionPageGrid, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

type DrawerMode = 'health' | 'bind' | 'guardian' | 'member'

/** Each drawer footer submits its form by id, so the buttons live outside the `<Form>`. */
const healthFormId = 'nutrition-health-profile-form'
const memberFormId = 'nutrition-member-profile-form'
const bindFormId = 'nutrition-member-bind-form'
const guardianFormId = 'nutrition-member-guardian-form'

function MemberHealthPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const familySelection = useNutritionFamilySelection()
    const [healthForm] = Form.useForm<NutritionUpdateHealthProfileRequest>()
    const [actionForm] = Form.useForm<{userId: number}>()
    const [memberForm] = Form.useForm<NutritionUpdateMemberProfileRequest>()
    const [members, setMembers] = useState<NutritionMemberProfileResponse[]>([])
    const [profiles, setProfiles] = useState<NutritionHealthProfileResponse[]>([])
    const [tags, setTags] = useState<NutritionHealthTagResponse[]>([])
    const [selectedMember, setSelectedMember] = useState<NutritionMemberProfileResponse>()
    const [drawerMode, setDrawerMode] = useState<DrawerMode>()
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()
    const [mutationError, setMutationError] = useState<string>()
    const [saving, setSaving] = useState(false)
    const canManage = canUseRbacButton(auth, 'btn:nutrition:member:manage')
        || auth.hasPermission(nutritionApiCodes.family)

    const loadData = useCallback(async () => {
        if (!familySelection.currentFamilyId) return
        setState('loading')
        try {
            const [memberRows, profileRows, tagRows] = await Promise.all([
                listNutritionMembers(familySelection.currentFamilyId),
                listNutritionHealthProfiles(familySelection.currentFamilyId),
                listNutritionFamilyHealthTags(familySelection.currentFamilyId),
            ])
            setMembers(memberRows)
            setProfiles(profileRows)
            setTags(tagRows)
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

    const profilesByMember = useMemo(
        () => new Map(profiles.map((profile) => [profile.memberProfileId, profile])),
        [profiles],
    )

    function openHealth(member: NutritionMemberProfileResponse) {
        setSelectedMember(member)
        healthForm.setFieldsValue(profilesByMember.get(member.id) ?? {})
        setDrawerMode('health')
    }

    function openAction(mode: DrawerMode, member?: NutritionMemberProfileResponse) {
        setSelectedMember(member)
        actionForm.resetFields()
        memberForm.resetFields()
        if (mode === 'member' && member) {
            memberForm.setFieldsValue({
                nickname: member.nickname,
                gender: member.gender ?? undefined,
                birthDate: member.birthDate ?? undefined,
                heightCm: member.heightCm ?? undefined,
                weightKg: member.weightKg ?? undefined,
                memberType: member.memberType,
                loginEnabled: member.loginEnabled,
                guardianMemberId: member.guardianMemberId ?? undefined,
            })
        }
        setDrawerMode(mode)
    }

    async function mutate(operation: () => Promise<void>, success: string) {
        setSaving(true)
        setMutationError(undefined)
        try {
            await operation()
            setDrawerMode(undefined)
            await loadData()
            void message.success(success)
        } catch (reason) {
            setMutationError(nutritionLoadFailure(reason).error)
        } finally {
            setSaving(false)
        }
    }

    async function saveHealth(values: NutritionUpdateHealthProfileRequest) {
        if (!familySelection.currentFamilyId || !selectedMember) return
        const familyId = familySelection.currentFamilyId
        const memberId = selectedMember.id
        await mutate(async () => {
            await updateNutritionHealthProfile(familyId, memberId, values)
        }, '健康档案已保存')
    }

    async function saveMember(values: NutritionUpdateMemberProfileRequest) {
        if (!familySelection.currentFamilyId) return
        const familyId = familySelection.currentFamilyId
        const memberId = selectedMember?.id
        await mutate(async () => {
            if (memberId) {
                await updateNutritionMemberProfile(familyId, memberId, values)
            } else {
                await createNutritionMemberProfile(familyId, values)
            }
        }, '成员档案已保存')
    }

    async function submitAction(values: {userId: number}) {
        if (!familySelection.currentFamilyId || !selectedMember) return
        if (drawerMode === 'bind') {
            await mutate(async () => {
                await bindNutritionMemberUser(familySelection.currentFamilyId!, selectedMember.id, values)
            }, '登录用户已绑定')
        } else if (drawerMode === 'guardian') {
            await mutate(async () => {
                await assignNutritionProfileGuardian(familySelection.currentFamilyId!, selectedMember.id, values)
            }, '监护人已添加')
        }
    }

    function memberActions(record: NutritionMemberProfileResponse): RowAction[] {
        return [
            {
                key: 'health',
                label: '健康档案',
                onClick: () => openHealth(record),
            },
            {
                key: 'member',
                label: '编辑',
                disabled: !canManage,
                onClick: () => openAction('member', record),
            },
            {
                key: 'bind',
                label: '绑定用户',
                disabled: !canManage,
                hidden: record.ownerProfile,
                onClick: () => openAction('bind', record),
            },
            {
                key: 'guardian',
                label: '添加监护人',
                disabled: !canManage,
                onClick: () => openAction('guardian', record),
            },
            {
                key: 'unbind',
                label: '解绑',
                disabled: !canManage,
                hidden: record.ownerProfile || !record.boundUserId,
                confirm: `确认解绑「${record.nickname}」的登录用户？该成员将无法再自行登录。`,
                onClick: () => void mutate(async () => {
                    if (!familySelection.currentFamilyId) return
                    await unbindNutritionMemberUser(familySelection.currentFamilyId, record.id)
                }, '登录用户已解绑'),
            },
            {
                key: 'deactivate',
                label: '停用',
                danger: true,
                disabled: !canManage,
                hidden: record.ownerProfile,
                confirm: `确认停用成员「${record.nickname}」？停用后不再参与配餐与统计。`,
                onClick: () => void mutate(async () => {
                    if (!familySelection.currentFamilyId) return
                    await deactivateNutritionMemberProfile(familySelection.currentFamilyId, record.id)
                }, '成员已停用'),
            },
        ]
    }

    const memberColumns: ColumnsType<NutritionMemberProfileResponse> = [
        {
            title: '成员',
            dataIndex: 'nickname',
            render: (value: string, record) => (
                <StackedCell
                    primary={(
                        <Space size="small">
                            <span>{value}</span>
                            {record.ownerProfile && <Tag color="blue">家庭所有者</Tag>}
                        </Space>
                    )}
                    secondary={record.memberType}
                />
            ),
        },
        {
            title: '账号关联',
            dataIndex: 'boundUsername',
            width: 180,
            render: (value: string | null | undefined, record) => (
                <StackedCell
                    plain
                    primary={value ?? (record.boundUserId ? `#${record.boundUserId}` : '未绑定登录用户')}
                    secondary={record.guardianMemberId ? `监护成员 #${record.guardianMemberId}` : undefined}
                />
            ),
        },
        {title: '操作', width: 200, render: (_, record) => <RowActions actions={memberActions(record)} maxInline={2}/>},
    ]
    const profileColumns: ColumnsType<NutritionHealthProfileResponse> = [
        {title: '成员', dataIndex: 'memberProfileId', render: (id: number) => members.find((member) => member.id === id)?.nickname ?? id},
        {title: '活动水平', dataIndex: 'activityLevel'},
        {title: '目标热量', dataIndex: 'targetCalories'},
        {title: '目标蛋白', dataIndex: 'targetProtein'},
        {title: '过敏标签', dataIndex: 'allergyTags', render: (values: string[] = []) => values.map((value) => <Tag color="error" key={value}>{value}</Tag>)},
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
                        <Button disabled={!canManage} icon={<PlusOutlined/>} onClick={() => openAction('member')} type="primary">新建成员</Button>
                    </Space>
                )}
                description="维护家庭成员、账号绑定、监护关系、健康目标和健康标签。"
                icon={<TeamOutlined/>}
                title="成员健康"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionPageGrid>
                    <DataTable<NutritionMemberProfileResponse>
                        columns={memberColumns}
                        count={members.length}
                        dataSource={members}
                        emptyDescription="先新建一个成员，再为其绑定登录用户或指定监护人。"
                        emptyTitle="该家庭还没有成员档案"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 640}}
                        size="small"
                        title="成员档案"
                    />
                    <DataTable<NutritionHealthProfileResponse>
                        columns={profileColumns}
                        count={profiles.length}
                        dataSource={profiles}
                        emptyDescription="在成员档案里点击「健康档案」，填写活动水平与营养目标。"
                        emptyTitle="还没有成员填写健康档案"
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 760}}
                        size="small"
                        title="健康档案"
                    />
                </NutritionPageGrid>
            </NutritionAsyncState>
            <FormDrawer
                formId={healthFormId}
                loading={saving}
                onClose={() => setDrawerMode(undefined)}
                open={drawerMode === 'health'}
                size="md"
                submitText="保存健康档案"
                title={`健康档案 · ${selectedMember?.nickname ?? ''}`}
            >
                <Form form={healthForm} id={healthFormId} layout="vertical" onFinish={(values) => void saveHealth(values)}>
                    <Form.Item label="活动水平" name="activityLevel"><Select options={activityOptions}/></Form.Item>
                    <Form.Item label="饮食目标" name="dietGoals"><Select mode="multiple" options={tagOptions(tags, 'DIET_GOAL')}/></Form.Item>
                    <Form.Item label="过敏标签" name="allergyTags"><Select mode="multiple" options={tagOptions(tags, 'ALLERGY_TAG')}/></Form.Item>
                    <Form.Item label="不喜标签" name="dislikeTags"><Select mode="multiple" options={tagOptions(tags, 'DISLIKE_TAG')}/></Form.Item>
                    <Form.Item label="限制标签" name="restrictionTags"><Select mode="multiple" options={tagOptions(tags, 'HEALTH_TAG')}/></Form.Item>
                    {/* Six short number fields — a tighter minimum than the page grid keeps them side by side. */}
                    <PageGrid minWidth={160}>
                        <Form.Item label="目标热量" name="targetCalories"><InputNumber aria-label="目标热量" className="u-full-width" min={0}/></Form.Item>
                        <Form.Item label="目标蛋白" name="targetProtein"><InputNumber className="u-full-width" min={0}/></Form.Item>
                        <Form.Item label="目标脂肪" name="targetFat"><InputNumber className="u-full-width" min={0}/></Form.Item>
                        <Form.Item label="目标碳水" name="targetCarbs"><InputNumber className="u-full-width" min={0}/></Form.Item>
                        <Form.Item label="目标钠" name="targetSodium"><InputNumber className="u-full-width" min={0}/></Form.Item>
                        <Form.Item label="目标糖" name="targetSugar"><InputNumber className="u-full-width" min={0}/></Form.Item>
                    </PageGrid>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={memberFormId}
                loading={saving}
                onClose={() => setDrawerMode(undefined)}
                open={drawerMode === 'member'}
                size="md"
                submitText="保存成员"
                title={selectedMember ? '编辑成员' : '新建成员'}
            >
                <Form form={memberForm} id={memberFormId} initialValues={{memberType: 'ADULT', loginEnabled: false}} layout="vertical" onFinish={(values) => void saveMember(values)}>
                    <Form.Item label="成员昵称" name="nickname" rules={[{required: true}]}>
                        <Input disabled={selectedMember?.ownerProfile}/>
                    </Form.Item>
                    <Form.Item label="性别" name="gender"><Input/></Form.Item>
                    <Form.Item label="出生日期" name="birthDate"><Input placeholder="YYYY-MM-DD"/></Form.Item>
                    <Form.Item label="身高 cm" name="heightCm"><InputNumber className="u-full-width" min={0}/></Form.Item>
                    <Form.Item label="体重 kg" name="weightKg"><InputNumber className="u-full-width" min={0}/></Form.Item>
                    <Form.Item label="成员类型" name="memberType" rules={[{required: true}]}><Select options={memberTypeOptions}/></Form.Item>
                    <Form.Item label="允许登录" name="loginEnabled" valuePropName="checked"><Switch/></Form.Item>
                    <Form.Item label="监护成员 ID" name="guardianMemberId"><InputNumber className="u-full-width" min={1}/></Form.Item>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={bindFormId}
                loading={saving}
                onClose={() => setDrawerMode(undefined)}
                open={drawerMode === 'bind'}
                size="sm"
                submitText="确认绑定"
                title={`绑定用户 · ${selectedMember?.nickname ?? ''}`}
            >
                <Form form={actionForm} id={bindFormId} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    <Form.Item label="用户 ID" name="userId" rules={[{required: true}]}><InputNumber aria-label="用户 ID" className="u-full-width" min={1}/></Form.Item>
                </Form>
            </FormDrawer>
            <FormDrawer
                formId={guardianFormId}
                loading={saving}
                onClose={() => setDrawerMode(undefined)}
                open={drawerMode === 'guardian'}
                size="sm"
                submitText="确认添加"
                title={`添加监护人 · ${selectedMember?.nickname ?? ''}`}
            >
                <Form form={actionForm} id={guardianFormId} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    <Form.Item label="监护用户 ID" name="userId" rules={[{required: true}]}><InputNumber aria-label="监护用户 ID" className="u-full-width" min={1}/></Form.Item>
                </Form>
            </FormDrawer>
        </NutritionStack>
    )
}

const activityOptions = ['SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE', 'VERY_ACTIVE'].map((value) => ({label: value, value}))
const memberTypeOptions = ['ADULT', 'CHILD', 'ELDER', 'GUEST'].map((value) => ({label: value, value}))

function tagOptions(tags: NutritionHealthTagResponse[], tagType: string) {
    return tags.filter((tag) => tag.tagType === tagType).map((tag) => ({label: tag.name, value: tag.tagCode}))
}

export const Component = MemberHealthPage
