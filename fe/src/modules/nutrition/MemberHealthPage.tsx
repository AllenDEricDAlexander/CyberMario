import {EditOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Drawer, Form, Input, InputNumber, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
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
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import {useNutritionFamilySelection} from './useNutritionFamilySelection'

type DrawerMode = 'health' | 'bind' | 'guardian' | 'member'

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

    const memberColumns: ColumnsType<NutritionMemberProfileResponse> = [
        {
            title: '成员',
            dataIndex: 'nickname',
            render: (value: string, record) => (
                <Space size="small">
                    <span>{value}</span>
                    {record.ownerProfile && <Tag color="blue">家庭所有者</Tag>}
                </Space>
            ),
        },
        {title: '类型', dataIndex: 'memberType', width: 110, render: (value) => <Tag>{value}</Tag>},
        {
            title: '绑定用户',
            dataIndex: 'boundUsername',
            width: 150,
            render: (value: string | null | undefined, record) => value ?? (record.boundUserId ? `#${record.boundUserId}` : '-'),
        },
        {title: '监护成员', dataIndex: 'guardianMemberId', width: 110, render: (value: number | null | undefined) => value ?? '-'},
        {
            title: '操作', width: 430, render: (_, record) => (
                <Space wrap size="small">
                    <Button aria-label={`健康档案 ${record.nickname}`} icon={<EditOutlined/>} onClick={() => openHealth(record)} size="small">健康档案</Button>
                    <Button aria-label={`编辑成员 ${record.nickname}`} disabled={!canManage} onClick={() => openAction('member', record)} size="small">编辑</Button>
                    {!record.ownerProfile && <Button aria-label={`绑定用户 ${record.nickname}`} disabled={!canManage} onClick={() => openAction('bind', record)} size="small">绑定用户</Button>}
                    <Button aria-label={`添加监护人 ${record.nickname}`} disabled={!canManage} onClick={() => openAction('guardian', record)} size="small">添加监护人</Button>
                    {!record.ownerProfile && record.boundUserId && <Button disabled={!canManage} onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await unbindNutritionMemberUser(familySelection.currentFamilyId, record.id)
                    }, '登录用户已解绑')} size="small">解绑</Button>}
                    {!record.ownerProfile && <Button danger disabled={!canManage} onClick={() => void mutate(async () => {
                        if (!familySelection.currentFamilyId) return
                        await deactivateNutritionMemberProfile(familySelection.currentFamilyId, record.id)
                    }, '成员已停用')} size="small">停用</Button>}
                </Space>
            ),
        },
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
                title="成员健康"
            />
            {mutationError && <Alert closable={{onClose: () => setMutationError(undefined)}} showIcon title={mutationError} type="error"/>}
            <NutritionAsyncState
                error={familySelection.state === 'ready' ? error : familySelection.error}
                onRetry={() => void (familySelection.state === 'ready' ? loadData() : familySelection.reload())}
                state={visibleState}
            >
                <NutritionPageGrid>
                    <NutritionSection title="成员档案">
                        <Table columns={memberColumns} dataSource={members} pagination={false} rowKey="id" scroll={{x: 1000}} size="small"/>
                    </NutritionSection>
                    <NutritionSection title="健康档案">
                        <Table columns={profileColumns} dataSource={profiles} pagination={false} rowKey="id" scroll={{x: 760}} size="small"/>
                    </NutritionSection>
                </NutritionPageGrid>
            </NutritionAsyncState>
            <Drawer destroyOnHidden loading={saving} onClose={() => setDrawerMode(undefined)} open={drawerMode === 'health'} size={520} title={`健康档案 · ${selectedMember?.nickname ?? ''}`}>
                <Form form={healthForm} layout="vertical" onFinish={(values) => void saveHealth(values)}>
                    <Form.Item label="活动水平" name="activityLevel"><Select options={activityOptions}/></Form.Item>
                    <Form.Item label="饮食目标" name="dietGoals"><Select mode="multiple" options={tagOptions(tags, 'DIET_GOAL')}/></Form.Item>
                    <Form.Item label="过敏标签" name="allergyTags"><Select mode="multiple" options={tagOptions(tags, 'ALLERGY_TAG')}/></Form.Item>
                    <Form.Item label="不喜标签" name="dislikeTags"><Select mode="multiple" options={tagOptions(tags, 'DISLIKE_TAG')}/></Form.Item>
                    <Form.Item label="限制标签" name="restrictionTags"><Select mode="multiple" options={tagOptions(tags, 'HEALTH_TAG')}/></Form.Item>
                    <NutritionPageGrid>
                        <Form.Item label="目标热量" name="targetCalories"><InputNumber aria-label="目标热量" min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="目标蛋白" name="targetProtein"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="目标脂肪" name="targetFat"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="目标碳水" name="targetCarbs"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="目标钠" name="targetSodium"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                        <Form.Item label="目标糖" name="targetSugar"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                    </NutritionPageGrid>
                    <Button htmlType="submit" loading={saving} type="primary">保存健康档案</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden onClose={() => setDrawerMode(undefined)} open={drawerMode === 'member'} size={500} title={selectedMember ? '编辑成员' : '新建成员'}>
                <Form form={memberForm} initialValues={{memberType: 'ADULT', loginEnabled: false}} layout="vertical" onFinish={(values) => void saveMember(values)}>
                    <Form.Item label="成员昵称" name="nickname" rules={[{required: true}]}>
                        <Input disabled={selectedMember?.ownerProfile}/>
                    </Form.Item>
                    <Form.Item label="性别" name="gender"><Input/></Form.Item>
                    <Form.Item label="出生日期" name="birthDate"><Input placeholder="YYYY-MM-DD"/></Form.Item>
                    <Form.Item label="身高 cm" name="heightCm"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="体重 kg" name="weightKg"><InputNumber min={0} style={{width: '100%'}}/></Form.Item>
                    <Form.Item label="成员类型" name="memberType" rules={[{required: true}]}><Select options={memberTypeOptions}/></Form.Item>
                    <Form.Item label="允许登录" name="loginEnabled" valuePropName="checked"><Switch/></Form.Item>
                    <Form.Item label="监护成员 ID" name="guardianMemberId"><InputNumber min={1} style={{width: '100%'}}/></Form.Item>
                    <Button htmlType="submit" loading={saving} type="primary">保存成员</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden onClose={() => setDrawerMode(undefined)} open={drawerMode === 'bind'} title={`绑定用户 · ${selectedMember?.nickname ?? ''}`}>
                <Form form={actionForm} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    <Form.Item label="用户 ID" name="userId" rules={[{required: true}]}><InputNumber aria-label="用户 ID" min={1} style={{width: '100%'}}/></Form.Item>
                    <Button htmlType="submit" loading={saving} type="primary">确认绑定</Button>
                </Form>
            </Drawer>
            <Drawer destroyOnHidden onClose={() => setDrawerMode(undefined)} open={drawerMode === 'guardian'} title={`添加监护人 · ${selectedMember?.nickname ?? ''}`}>
                <Form form={actionForm} layout="vertical" onFinish={(values) => void submitAction(values)}>
                    <Form.Item label="监护用户 ID" name="userId" rules={[{required: true}]}><InputNumber aria-label="监护用户 ID" min={1} style={{width: '100%'}}/></Form.Item>
                    <Button htmlType="submit" loading={saving} type="primary">确认添加</Button>
                </Form>
            </Drawer>
        </NutritionStack>
    )
}

const activityOptions = ['SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE', 'VERY_ACTIVE'].map((value) => ({label: value, value}))
const memberTypeOptions = ['ADULT', 'CHILD', 'ELDER', 'GUEST'].map((value) => ({label: value, value}))

function tagOptions(tags: NutritionHealthTagResponse[], tagType: string) {
    return tags.filter((tag) => tag.tagType === tagType).map((tag) => ({label: tag.name, value: tag.tagCode}))
}

export const Component = MemberHealthPage
