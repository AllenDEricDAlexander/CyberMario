import {
    DeleteOutlined,
    EditOutlined,
    KeyOutlined,
    LinkOutlined,
    LockOutlined,
    PlusOutlined,
    SafetyOutlined,
    StopOutlined,
    TeamOutlined,
    UserOutlined,
} from '@ant-design/icons'
import {App, Button, Form, Input, Modal, Select, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {PromptModal} from '../../../components/PromptModal'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {StackedCell} from '../../../components/StackedCell'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import {enumEquals} from '../../../utils/enum'
import {
    createUser,
    deleteUser,
    getRoles,
    getUserEffectivePermissions,
    getUserRoles,
    getUsers,
    replaceUserRoles,
    reissueUserActivation,
    resetUserPassword,
    updateUser,
    updateUserStatus,
} from '../rbacService'
import type {
    ActivationDeliveryResponse,
    CreateUserRequest,
    EffectivePermissionResponse,
    RoleResponse,
    UpdateUserRequest,
    UserResponse,
} from '../rbacTypes'
import {ActivationLinkModal} from './ActivationLinkModal'
import {UserEditorDrawer} from './UserEditorDrawer'
import {UserPermissionDrawer} from './UserPermissionDrawer'
import {UserRoleDrawer} from './UserRoleDrawer'

type ActivationModalState = {
    title: string
    delivery: ActivationDeliveryResponse
}

type UserFilters = {
    keyword?: string
    status?: string
    activationStatus?: string
}

function UserListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [saving, setSaving] = useState(false)
    const [roles, setRoles] = useState<RoleResponse[]>([])
    const [editingUser, setEditingUser] = useState<UserResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [roleUser, setRoleUser] = useState<UserResponse | null>(null)
    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([])
    const [permissionUser, setPermissionUser] = useState<UserResponse | null>(null)
    const [effectivePermissions, setEffectivePermissions] = useState<EffectivePermissionResponse | null>(null)
    const [activationModal, setActivationModal] = useState<ActivationModalState | null>(null)
    const [passwordUser, setPasswordUser] = useState<UserResponse | null>(null)
    const [filters, setFilters] = useState<UserFilters>({})
    // Filters go to the server so a search covers every user, not just the
    // rows already loaded into the current page.
    const loadUsersPage = useCallback(
        (request: { page: number; size: number }) => getUsers({...request, ...filters}),
        [filters],
    )
    const {loading, records: users, page, size, total, load: loadUsers} = usePageData<UserResponse>(loadUsersPage)

    async function loadRoles() {
        const result = await getRoles({page: 1, size: 200})
        setRoles(result.records)
    }

    useEffect(() => {
        void loadRoles()
    }, [])

    const canCreate = canUseRbacButton(auth, rbacButtonCodes.user.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.user.edit)
    const canAssignRoles = canUseRbacButton(auth, rbacButtonCodes.user.roles)
    const canViewPermissions = canUseRbacButton(auth, rbacButtonCodes.user.permissions)
    const canResetPassword = canUseRbacButton(auth, rbacButtonCodes.user.resetPassword)
    const canResendActivation = canUseRbacButton(auth, rbacButtonCodes.user.resendActivation)
    const canChangeStatus = canUseRbacButton(auth, rbacButtonCodes.user.status)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.user.delete)

    const columns: ColumnsType<UserResponse> = [
        {
            title: '用户',
            dataIndex: 'accountNo',
            fixed: 'left',
            width: 220,
            render: (_, record) => (
                <StackedCell primary={record.nickname || record.username} secondary={record.accountNo}/>
            ),
        },
        {
            title: '联系方式',
            dataIndex: 'email',
            width: 220,
            render: (_, record) => (
                <StackedCell plain primary={record.email || '-'} secondary={record.mobile || '未绑定手机'}/>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (_, record) => <StatusTag value={record.status}/>,
        },
        {
            title: '激活状态',
            dataIndex: 'activationStatus',
            width: 110,
            render: (_, record) => (
                <Tag color={record.activationStatus === 'PENDING_ACTIVATION' ? 'gold' : 'green'}>
                    {activationStatusLabel(record)}
                </Tag>
            ),
        },
        {
            title: '安全',
            dataIndex: 'locked',
            width: 150,
            render: (_, record) => {
                const flags = [
                    record.locked && <Tag color="error" icon={<LockOutlined/>} key="locked">已锁定</Tag>,
                    record.passwordExpired && <Tag color="warning" key="expired">密码过期</Tag>,
                ].filter(Boolean)
                return flags.length ? <>{flags}</> : <Typography.Text type="secondary">正常</Typography.Text>
            },
        },
        {
            title: '操作',
            fixed: 'right',
            width: 190,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: UserResponse) {
        const activationActions = activationActionsFor(record, {
            resetPassword: canResetPassword,
            resendActivation: canResendActivation,
        })
        const actions: RowAction[] = [
            {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined/>,
                hidden: !canEdit,
                onClick: () => openEditor(record),
            },
            {
                key: 'roles',
                label: '角色',
                icon: <TeamOutlined/>,
                hidden: !canAssignRoles,
                onClick: () => void openRoles(record),
            },
            {
                key: 'permissions',
                label: '权限',
                icon: <SafetyOutlined/>,
                hidden: !canViewPermissions,
                onClick: () => void openPermissions(record),
            },
            {
                key: 'password',
                label: '重置密码',
                icon: <KeyOutlined/>,
                hidden: !activationActions.resetPassword,
                onClick: () => setPasswordUser(record),
            },
            {
                key: 'resend-activation',
                label: '重新生成激活链接',
                icon: <LinkOutlined/>,
                hidden: !activationActions.resendActivation,
                onClick: () => void resendActivation(record),
            },
            {
                key: 'status',
                label: isEnabled(record) ? '禁用' : '启用',
                icon: <StopOutlined/>,
                hidden: !canChangeStatus,
                confirm: `确认${isEnabled(record) ? '禁用' : '启用'}该用户？`,
                onClick: () => void toggleStatus(record),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该用户？删除后无法恢复。',
                onClick: () => void removeUser(record.id),
            },
        ]
        return <RowActions actions={actions} maxInline={2}/>
    }

    function openEditor(user?: UserResponse) {
        setEditingUser(user ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: CreateUserRequest | UpdateUserRequest) {
        setSaving(true)
        try {
            if (editingUser) {
                const updatedUser = await updateUser(editingUser.id, request)
                if (shouldPromptActivationReissue(editingUser, request, updatedUser)) {
                    promptForActivationReissue(updatedUser)
                }
            } else {
                const created = await createUser(request as CreateUserRequest)
                setActivationModal({title: '账号已创建', delivery: created.activationDelivery})
            }
            message.success('保存成功')
            setEditorOpen(false)
            await loadUsers()
        } finally {
            setSaving(false)
        }
    }

    function promptForActivationReissue(user: UserResponse) {
        if (!canResendActivation) {
            Modal.warning({
                title: '原激活链接已失效',
                content: '待激活用户的邮箱已变更，请联系有“重新生成激活链接”权限的管理员处理。',
            })
            return
        }
        Modal.confirm({
            title: '原激活链接已失效',
            content: '待激活用户的邮箱已变更，是否立即生成新的激活链接？',
            okText: '生成新链接',
            cancelText: '稍后处理',
            onOk: () => resendActivation(user),
        })
    }

    async function resendActivation(user: UserResponse) {
        setSaving(true)
        try {
            const delivery = await reissueUserActivation(user.id)
            setActivationModal({title: '新激活链接已生成', delivery})
            message.success('已生成新的激活链接')
        } finally {
            setSaving(false)
        }
    }

    async function openRoles(user: UserResponse) {
        setRoleUser(user)
        const ids = await getUserRoles(user.id)
        setSelectedRoleIds(ids)
    }

    async function saveRoles(ids: number[]) {
        if (!roleUser) return
        setSaving(true)
        try {
            await replaceUserRoles(roleUser.id, ids)
            message.success('角色已更新')
            setRoleUser(null)
        } finally {
            setSaving(false)
        }
    }

    async function openPermissions(user: UserResponse) {
        setPermissionUser(user)
        setEffectivePermissions(await getUserEffectivePermissions(user.id))
    }

    async function submitPasswordReset(values: { password: string }) {
        if (!passwordUser) return
        await resetUserPassword(passwordUser.id, values.password)
        message.success('密码已重置')
        setPasswordUser(null)
    }

    async function toggleStatus(user: UserResponse) {
        await updateUserStatus(user.id, isEnabled(user) ? 'DISABLED' : 'ENABLED')
        message.success('状态已更新')
        await loadUsers()
    }

    async function removeUser(id: number) {
        await deleteUser(id)
        message.success('用户已删除')
        await loadUsers()
    }

    return (
        <>
            <PageToolbar
                actions={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建用户</Button>
                )}
                description="维护用户资料、状态、角色授权和有效权限。"
                icon={<UserOutlined/>}
                title="用户管理"
            />
            <FilterBar<UserFilters>
                onSearch={setFilters}
                onReset={() => setFilters({})}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="账号、用户名、昵称、邮箱"/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select
                        allowClear
                        options={[
                            {label: '启用', value: 'ENABLED'},
                            {label: '禁用', value: 'DISABLED'},
                        ]}
                        placeholder="全部"
                    />
                </Form.Item>
                <Form.Item label="激活状态" name="activationStatus">
                    <Select
                        allowClear
                        options={[
                            {label: '待激活', value: 'PENDING_ACTIVATION'},
                            {label: '已激活', value: 'ACTIVATED'},
                        ]}
                        placeholder="全部"
                    />
                </Form.Item>
            </FilterBar>
            <DataTable<UserResponse>
                columns={columns}
                count={total}
                dataSource={users}
                emptyDescription="调整筛选条件，或创建第一个用户账号。"
                emptyTitle="没有匹配的用户"
                loading={loading}
                pagination={{
                    current: page,
                    pageSize: size,
                    total,
                    onChange: voidify(loadUsers),
                }}
                rowKey="id"
                scroll={{x: 1000}}
                title="用户列表"
            />
            <UserEditorDrawer
                loading={saving}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                value={editingUser}
            />
            <PromptModal<{ password: string }>
                danger
                description={passwordUser
                    ? `将为「${passwordUser.nickname || passwordUser.username}」（${passwordUser.accountNo}）设置新密码，用户下次登录时生效。`
                    : undefined}
                fields={[
                    {
                        name: 'password',
                        label: '新密码',
                        type: 'password',
                        autoFocus: true,
                        placeholder: '至少 8 位，包含字母与数字',
                        rules: [
                            {required: true, message: '请输入新密码'},
                            {min: 8, message: '密码至少 8 位'},
                        ],
                    },
                    {
                        name: 'confirmPassword',
                        label: '确认新密码',
                        type: 'password',
                        placeholder: '再次输入新密码',
                        rules: [
                            {required: true, message: '请再次输入新密码'},
                            ({getFieldValue}) => ({
                                validator: (_, value) =>
                                    !value || value === getFieldValue('password')
                                        ? Promise.resolve()
                                        : Promise.reject(new Error('两次输入的密码不一致')),
                            }),
                        ],
                    },
                ]}
                okText="重置密码"
                onCancel={() => setPasswordUser(null)}
                onSubmit={submitPasswordReset}
                open={Boolean(passwordUser)}
                title="重置密码"
            />
            <ActivationLinkModal
                onClose={() => setActivationModal(null)}
                title={activationModal?.title ?? '激活链接'}
                value={activationModal?.delivery ?? null}
            />
            <UserRoleDrawer
                onClose={() => setRoleUser(null)}
                onSubmit={saveRoles}
                open={Boolean(roleUser)}
                roles={roles}
                saving={saving}
                selectedRoleIds={selectedRoleIds}
                user={roleUser}
            />
            <UserPermissionDrawer
                onClose={() => setPermissionUser(null)}
                open={Boolean(permissionUser)}
                user={permissionUser}
                value={effectivePermissions}
            />
        </>
    )
}

function isEnabled(user: UserResponse) {
    return enumEquals(user.status, 1) || enumEquals(user.status, 'ENABLED')
}

type ActivationActionAccess = {
    resetPassword: boolean
    resendActivation: boolean
}

export function activationActionsFor(user: UserResponse, access: ActivationActionAccess) {
    const pending = user.activationStatus === 'PENDING_ACTIVATION'
    return {
        resetPassword: access.resetPassword && !pending,
        resendActivation: access.resendActivation && pending,
    }
}

export function activationStatusLabel(user: UserResponse) {
    return user.activationStatus === 'PENDING_ACTIVATION' ? '待激活' : '已激活'
}

export function shouldPromptActivationReissue(
    user: UserResponse,
    request: UpdateUserRequest,
    updatedUser: UserResponse,
) {
    const currentEmail = user.email?.trim() || undefined
    const nextEmail = request.email?.trim() || undefined
    return updatedUser.activationStatus === 'PENDING_ACTIVATION' && currentEmail !== nextEmail
}

export const Component = UserListPage
