import {EditOutlined, KeyOutlined, LinkOutlined, PlusOutlined, SafetyOutlined, TeamOutlined} from '@ant-design/icons'
import {App, Button, Input, Modal, Popconfirm, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
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
    const loadUsersPage = useCallback(
        (request: { page: number; size: number }) => getUsers(request),
        [],
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
        {title: '账号', dataIndex: 'accountNo', fixed: 'left', width: 150},
        {title: '用户名', dataIndex: 'username', width: 150},
        {title: '昵称', dataIndex: 'nickname', width: 140, render: (_, record) => record.nickname || '-'},
        {title: '邮箱', dataIndex: 'email', width: 190, render: (_, record) => record.email || '-'},
        {title: '手机', dataIndex: 'mobile', width: 140, render: (_, record) => record.mobile || '-'},
        {title: '状态', dataIndex: 'status', width: 100, render: (_, record) => <StatusTag value={record.status}/>},
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
        {title: '锁定', dataIndex: 'locked', width: 80, render: (_, record) => record.locked ? '是' : '否'},
        {
            title: '密码过期',
            dataIndex: 'passwordExpired',
            width: 100,
            render: (_, record) => record.passwordExpired ? '是' : '否'
        },
        {
            title: '操作',
            fixed: 'right',
            width: 360,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: UserResponse) {
        const actions: ReactNode[] = []
        const activationActions = activationActionsFor(record, {
            resetPassword: canResetPassword,
            resendActivation: canResendActivation,
        })
        if (canEdit) {
            actions.push(<Button icon={<EditOutlined/>} key="edit" size="small"
                                 onClick={() => openEditor(record)}>编辑</Button>)
        }
        if (canAssignRoles) {
            actions.push(<Button icon={<TeamOutlined/>} key="roles" size="small"
                                 onClick={() => void openRoles(record)}>角色</Button>)
        }
        if (canViewPermissions) {
            actions.push(<Button icon={<SafetyOutlined/>} key="permissions" size="small"
                                 onClick={() => void openPermissions(record)}>权限</Button>)
        }
        if (activationActions.resetPassword) {
            actions.push(
                <Button icon={<KeyOutlined/>} key="password" size="small" onClick={() => openPasswordModal(record)}>
                    重置密码
                </Button>,
            )
        }
        if (activationActions.resendActivation) {
            actions.push(
                <Button icon={<LinkOutlined/>} key="resend-activation" size="small"
                        onClick={() => void resendActivation(record)}>
                    重新生成激活链接
                </Button>,
            )
        }
        if (canChangeStatus) {
            actions.push(
                <Popconfirm
                    key="status"
                    title={`确认${isEnabled(record) ? '禁用' : '启用'}该用户？`}
                    onConfirm={() => void toggleStatus(record)}
                >
                    <Button size="small">{isEnabled(record) ? '禁用' : '启用'}</Button>
                </Popconfirm>,
            )
        }
        if (canDelete) {
            actions.push(
                <Popconfirm key="delete" title="确认删除该用户？" onConfirm={() => void removeUser(record.id)}>
                    <Button danger size="small">删除</Button>
                </Popconfirm>,
            )
        }
        return actions.length ? <Space>{actions}</Space> : '-'
    }

    function openEditor(user?: UserResponse) {
        setEditingUser(user ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: CreateUserRequest | UpdateUserRequest) {
        setSaving(true)
        try {
            if (editingUser) {
                await updateUser(editingUser.id, request)
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

    function openPasswordModal(user: UserResponse) {
        let password = ''
        Modal.confirm({
            title: `重置密码：${user.accountNo}`,
            content: (
                <Input.Password
                    autoFocus
                    placeholder="输入新密码"
                    onChange={(event) => {
                        password = event.target.value
                    }}
                />
            ),
            onOk: async () => {
                if (!password) {
                    message.error('请输入新密码')
                    return Promise.reject(new Error('请输入新密码'))
                }
                await resetUserPassword(user.id, password)
                message.success('密码已重置')
            },
        })
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
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建用户</Button>}
                description="维护用户资料、状态、角色授权和有效权限。"
                title="用户管理"
            />
            <Table<UserResponse>
                columns={columns}
                dataSource={users}
                loading={loading}
                pagination={{
                    current: page,
                    pageSize: size,
                    total,
                    showSizeChanger: true,
                    onChange: voidify(loadUsers),
                }}
                rowKey="id"
                scroll={{x: 1280}}
            />
            <UserEditorDrawer
                loading={saving}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                value={editingUser}
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

export const Component = UserListPage
