import {EditOutlined, KeyOutlined, PlusOutlined, SafetyOutlined, TeamOutlined} from '@ant-design/icons'
import {App, Button, Input, Modal, Popconfirm, Space, Table} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {StatusTag} from '../../../components/StatusTag'
import {enumEquals} from '../../../utils/enum'
import {
    createUser,
    deleteUser,
    getRoles,
    getUserEffectivePermissions,
    getUserRoles,
    getUsers,
    replaceUserRoles,
    resetUserPassword,
    updateUser,
    updateUserStatus,
} from '../rbacService'
import type {
    CreateUserRequest,
    EffectivePermissionResponse,
    RoleResponse,
    UpdateUserRequest,
    UserResponse
} from '../rbacTypes'
import {UserEditorDrawer} from './UserEditorDrawer'
import {UserPermissionDrawer} from './UserPermissionDrawer'
import {UserRoleDrawer} from './UserRoleDrawer'

function UserListPage() {
    const {message} = App.useApp()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [users, setUsers] = useState<UserResponse[]>([])
    const [roles, setRoles] = useState<RoleResponse[]>([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const [editingUser, setEditingUser] = useState<UserResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [roleUser, setRoleUser] = useState<UserResponse | null>(null)
    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([])
    const [permissionUser, setPermissionUser] = useState<UserResponse | null>(null)
    const [effectivePermissions, setEffectivePermissions] = useState<EffectivePermissionResponse | null>(null)

    async function loadUsers(nextPage = page, nextSize = size) {
        setLoading(true)
        try {
            const result = await getUsers({page: nextPage, size: nextSize})
            setUsers(result.records)
            setPage(result.page)
            setSize(result.size)
            setTotal(result.total)
        } finally {
            setLoading(false)
        }
    }

    async function loadRoles() {
        const result = await getRoles({page: 1, size: 200})
        setRoles(result.records)
    }

    useEffect(() => {
        void loadUsers(1, size)
        void loadRoles()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const columns: ColumnsType<UserResponse> = [
        {title: '用户名', dataIndex: 'username', fixed: 'left', width: 150},
        {title: '昵称', dataIndex: 'nickname', width: 140, render: (value) => value || '-'},
        {title: '邮箱', dataIndex: 'email', width: 190, render: (value) => value || '-'},
        {title: '手机', dataIndex: 'mobile', width: 140, render: (value) => value || '-'},
        {title: '状态', dataIndex: 'status', width: 100, render: (value) => <StatusTag value={value}/>},
        {title: '锁定', dataIndex: 'locked', width: 80, render: (value) => value ? '是' : '否'},
        {title: '密码过期', dataIndex: 'passwordExpired', width: 100, render: (value) => value ? '是' : '否'},
        {
            title: '操作',
            fixed: 'right',
            width: 360,
            render: (_, record) => (
                <Space>
                    <Button icon={<EditOutlined/>} size="small" onClick={() => openEditor(record)}>编辑</Button>
                    <Button icon={<TeamOutlined/>} size="small" onClick={() => openRoles(record)}>角色</Button>
                    <Button icon={<SafetyOutlined/>} size="small" onClick={() => openPermissions(record)}>权限</Button>
                    <Button icon={<KeyOutlined/>} size="small"
                            onClick={() => openPasswordModal(record)}>重置密码</Button>
                    <Popconfirm
                        title={`确认${isEnabled(record) ? '禁用' : '启用'}该用户？`}
                        onConfirm={() => toggleStatus(record)}
                    >
                        <Button size="small">{isEnabled(record) ? '禁用' : '启用'}</Button>
                    </Popconfirm>
                    <Popconfirm title="确认删除该用户？" onConfirm={() => removeUser(record.id)}>
                        <Button danger size="small">删除</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ]

    function openEditor(user?: UserResponse) {
        setEditingUser(user ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: CreateUserRequest | UpdateUserRequest) {
        setSaving(true)
        try {
            if (editingUser) {
                await updateUser(editingUser.id, request as UpdateUserRequest)
            } else {
                await createUser(request as CreateUserRequest)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await loadUsers()
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
            title: `重置密码：${user.username}`,
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
                    return Promise.reject()
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
                actions={<Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建用户</Button>}
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
                    onChange: loadUsers,
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

export const Component = UserListPage
